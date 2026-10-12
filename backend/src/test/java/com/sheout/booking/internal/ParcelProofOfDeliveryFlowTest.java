package com.sheout.booking.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.DropOffDeviationReason;
import com.sheout.booking.ParcelDetails;
import com.sheout.booking.ParcelExceptionReason;
import com.sheout.booking.ParcelPhotoKind;
import com.sheout.booking.ParcelSenderChoice;
import com.sheout.booking.internal.web.ParcelController;
import com.sheout.dispatch.internal.redis.DriverLocationStore;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proof of delivery for parcels, end to end against the local Postgres and
 * Redis: the pickup photo before the code, the receiver's code before
 * "Delivered" (wrong codes counted and locked), the drop check that asks
 * for a reason and flags rather than refusing, the receiver-absent flow
 * with each of the sender's answers, who may see the parcel, "End here"
 * refused, and the photo retention sweep.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class ParcelProofOfDeliveryFlowTest {

    private static final double PICKUP_LAT = 17.4156, PICKUP_LNG = 78.4347;
    private static final double DROP_LAT = 17.4435, DROP_LNG = 78.3772;
    private static final String PICKUP_CODE = "1234";
    private static final String DELIVERY_CODE = "4321";

    @Autowired BookingService bookings;
    @Autowired ParcelDeliveryService parcels;
    @Autowired ParcelDeliveryRepository parcelRows;
    @Autowired ParcelController controller;
    @Autowired DriverLocationStore locations;
    @Autowired JdbcTemplate jdbc;

    private final UUID sender = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();
    private final List<UUID> trips = new ArrayList<>();
    private final Random random = new Random(7);

    @BeforeEach
    void noCaller() {
        CurrentAccountContext.clear();
    }

    @AfterEach
    void cleanUp() {
        CurrentAccountContext.clear();
        for (UUID id : trips) {
            jdbc.update("delete from parcel_photos where booking_id = ?", id);
            jdbc.update("delete from parcel_deliveries where booking_id = ?", id);
            jdbc.update("delete from booking_events where booking_id = ?", id);
            jdbc.update("delete from payments where booking_id = ?", id);
            jdbc.update("delete from bookings where id = ?", id);
        }
        jdbc.update("delete from stored_documents where account_id = ?", partner);
        locations.remove(partner);
    }

    /** A parcel booked with proof of delivery, partner assigned, in the given status, its delivery code issued. */
    private UUID parcel(BookingStatus status) {
        UUID id = UUID.randomUUID();
        trips.add(id);
        jdbc.update("""
                insert into bookings (id, type, category, status, customer_id, driver_id,
                    pickup_label, pickup_lat, pickup_lng, drop_label, drop_lat, drop_lng,
                    fare_estimate, quoted_distance_km, pickup_otp, pickup_attempts, promo_discount,
                    accepted_at, started_at, created_at, updated_at)
                values (?, 'DELIVERY', 'PARCEL', ?, ?, ?, 'Banjara Hills', ?, ?, 'Hitech City', ?, ?,
                    80, 7.5, ?, 0, 0, now() at time zone 'utc',
                    case when ? then now() at time zone 'utc' end, now() at time zone 'utc', now() at time zone 'utc')""",
                id, status.name(), sender, partner, PICKUP_LAT, PICKUP_LNG, DROP_LAT, DROP_LNG, PICKUP_CODE,
                status == BookingStatus.IN_PROGRESS);
        parcels.recordDetails(id, new ParcelDetails("Priya Sharma", "98765 43210", new BigDecimal("1200"),
                parcels.rules().prohibitedItemsVersion(), true, true), Instant.now());
        ParcelDeliveryEntity row = parcelRows.findById(id).orElseThrow();
        row.issueCode(DELIVERY_CODE);
        parcelRows.save(row);
        return id;
    }

    private void partnerAt(double lat, double lng) {
        locations.recordLocation(partner, lat, lng);
    }

    private byte[] photo() {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 640; x += 8) {
            for (int y = 0; y < 480; y += 8) {
                int rgb = random.nextInt(0xFFFFFF);
                for (int dx = 0; dx < 8; dx++) for (int dy = 0; dy < 8; dy++) image.setRGB(x + dx, y + dy, rgb);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void upload(UUID trip, ParcelPhotoKind kind) {
        assertThat(parcels.uploadPhoto(trip, partner, kind, photo()).isSuccess()).as("upload " + kind).isTrue();
    }

    private static BookingError error(Result<?, BookingError> r) {
        assertThat(r.isFailure()).as("expected a refusal").isTrue();
        return r.error();
    }

    @Test
    void theParcelIsPhotographedBeforeThePickupCodeIsEvenLookedAt() {
        UUID trip = parcel(BookingStatus.ACCEPTED);
        partnerAt(PICKUP_LAT, PICKUP_LNG);

        assertThat(error(bookings.startTrip(trip, partner, PICKUP_CODE))).isEqualTo(BookingError.PARCEL_PICKUP_PHOTO_REQUIRED);
        assertThat(jdbc.queryForObject("select pickup_attempts from bookings where id = ?", Integer.class, trip))
                .as("a missing photo never costs her a code attempt").isZero();

        upload(trip, ParcelPhotoKind.PICKUP);
        Result<BookingSummary, BookingError> started = bookings.startTrip(trip, partner, PICKUP_CODE);
        assertThat(started.isSuccess()).isTrue();
        assertThat(started.value().status()).isEqualTo(BookingStatus.IN_PROGRESS);
    }

    @Test
    void deliveredNeedsTheHandoverPhotoAndTheReceiversCode() {
        UUID trip = parcel(BookingStatus.IN_PROGRESS);
        partnerAt(DROP_LAT, DROP_LNG);

        assertThat(error(bookings.completeTrip(trip, partner, null, null, DELIVERY_CODE))).isEqualTo(BookingError.PARCEL_DELIVERY_PHOTO_REQUIRED);
        upload(trip, ParcelPhotoKind.DELIVERY);
        assertThat(error(bookings.completeTrip(trip, partner, null, null, null))).isEqualTo(BookingError.PARCEL_DELIVERY_CODE_REQUIRED);

        Result<BookingSummary, BookingError> done = bookings.completeTrip(trip, partner, null, null, DELIVERY_CODE);
        assertThat(done.isSuccess()).isTrue();
        assertThat(done.value().status()).isEqualTo(BookingStatus.COMPLETED);
        ParcelDeliveryEntity row = parcelRows.findById(trip).orElseThrow();
        assertThat(row.getDeliveryVerifiedAt()).isNotNull();
        assertThat(row.getDropCheck()).isEqualTo(ParcelDeliveryEntity.DropCheck.AT_DROP);
        assertThat(row.getReviewFlaggedAt()).as("delivered at the drop on the code: nothing to review").isNull();
    }

    @Test
    void wrongCodesAreCountedAndTheFifthLocksIt() {
        UUID trip = parcel(BookingStatus.IN_PROGRESS);
        partnerAt(DROP_LAT, DROP_LNG);
        upload(trip, ParcelPhotoKind.DELIVERY);

        for (int i = 1; i <= 4; i++) {
            assertThat(error(bookings.completeTrip(trip, partner, null, null, "000" + i))).isEqualTo(BookingError.INVALID_PARCEL_DELIVERY_CODE);
        }
        assertThat(error(bookings.completeTrip(trip, partner, null, null, "0005"))).isEqualTo(BookingError.PARCEL_DELIVERY_CODE_LOCKED);
        assertThat(error(bookings.completeTrip(trip, partner, null, null, DELIVERY_CODE)))
                .as("locked means locked, even for the right code").isEqualTo(BookingError.PARCEL_DELIVERY_CODE_LOCKED);
        assertThat(parcelRows.findById(trip).orElseThrow().getDeliveryCodeAttempts()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, trip)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void awayFromTheDropItAsksWhyAndFlagsTheDeliveryNeverSilently() {
        UUID trip = parcel(BookingStatus.IN_PROGRESS);
        partnerAt(DROP_LAT + 0.01, DROP_LNG); // about 1.1 km away
        upload(trip, ParcelPhotoKind.DELIVERY);

        assertThat(error(bookings.completeTrip(trip, partner, null, null, DELIVERY_CODE))).isEqualTo(BookingError.DROP_OFF_REASON_REQUIRED);
        Result<BookingSummary, BookingError> done = bookings.completeTrip(trip, partner,
                DropOffDeviationReason.ROAD_CLOSED_OR_BLOCKED, null, DELIVERY_CODE);
        assertThat(done.isSuccess()).isTrue();
        ParcelDeliveryEntity row = parcelRows.findById(trip).orElseThrow();
        assertThat(row.getDropCheck()).isEqualTo(ParcelDeliveryEntity.DropCheck.OUTSIDE);
        assertThat(row.getDropCheckDistanceM()).isGreaterThan(1000);
        assertThat(row.getReviewFlaggedAt()).isNotNull();
        assertThat(row.getReviewFlagReason()).isEqualTo("OUTSIDE_DROP");
        assertThat(row.getOutsideReason()).isEqualTo(DropOffDeviationReason.ROAD_CLOSED_OR_BLOCKED);
    }

    @Test
    void receiverAbsentThenTheSenderSaysLeaveItWithSomeone() {
        UUID trip = parcel(BookingStatus.IN_PROGRESS);
        partnerAt(DROP_LAT, DROP_LNG);

        assertThat(error(parcels.raiseException(trip, partner, ParcelExceptionReason.RECEIVER_NOT_THERE, null)))
                .as("free text alone is never enough: a photo first").isEqualTo(BookingError.PARCEL_EXCEPTION_PHOTO_REQUIRED);
        upload(trip, ParcelPhotoKind.EXCEPTION);
        assertThat(error(parcels.raiseException(trip, partner, ParcelExceptionReason.OTHER, " "))).isEqualTo(BookingError.PARCEL_EXCEPTION_NOTE_REQUIRED);
        assertThat(parcels.raiseException(trip, partner, ParcelExceptionReason.RECEIVER_NOT_THERE, null).isSuccess()).isTrue();
        assertThat(parcels.viewFor(trip, sender).orElseThrow().awaitingSenderChoice()).isTrue();

        assertThat(error(parcels.recordSenderChoice(trip, UUID.randomUUID(), ParcelSenderChoice.WAIT, null)))
                .as("only the sender answers").isEqualTo(BookingError.BOOKING_NOT_FOUND);
        assertThat(error(parcels.recordSenderChoice(trip, sender, ParcelSenderChoice.LEAVE_WITH, " "))).isEqualTo(BookingError.PARCEL_LEAVE_WITH_NAME_REQUIRED);
        assertThat(parcels.recordSenderChoice(trip, sender, ParcelSenderChoice.LEAVE_WITH, "Lata, the neighbour").isSuccess()).isTrue();

        upload(trip, ParcelPhotoKind.DELIVERY);
        Result<BookingSummary, BookingError> done = bookings.completeTrip(trip, partner, null, null, null);
        assertThat(done.isSuccess()).as("the sender's answer stands in for the code").isTrue();
        ParcelDeliveryEntity row = parcelRows.findById(trip).orElseThrow();
        assertThat(row.getLeaveWithName()).isEqualTo("Lata, the neighbour");
        assertThat(row.getDeliveryVerifiedAt()).as("not delivered on a code").isNull();
    }

    @Test
    void receiverAbsentThenTheSenderWantsItBackAndPaysTheOneWayFareAgain() {
        UUID trip = parcel(BookingStatus.IN_PROGRESS);
        partnerAt(DROP_LAT, DROP_LNG);
        upload(trip, ParcelPhotoKind.EXCEPTION);
        assertThat(parcels.raiseException(trip, partner, ParcelExceptionReason.RECEIVER_REFUSED, null).isSuccess()).isTrue();

        assertThat(parcels.recordSenderChoice(trip, sender, ParcelSenderChoice.RETURN, null).isSuccess()).isTrue();
        BookingSummary now = bookings.findById(trip).orElseThrow();
        assertThat(now.drop().lat()).as("the drop is now the pickup").isEqualTo(PICKUP_LAT);
        assertThat(now.fareEstimate()).as("one-way fare added again").isEqualByComparingTo("160");
        assertThat(parcelRows.findById(trip).orElseThrow().getReturnFare()).isEqualByComparingTo("80");

        partnerAt(PICKUP_LAT, PICKUP_LNG);
        upload(trip, ParcelPhotoKind.DELIVERY);
        Result<BookingSummary, BookingError> done = bookings.completeTrip(trip, partner, null, null, DELIVERY_CODE);
        assertThat(done.isSuccess()).as("handed back on the same code, now in the sender's hands").isTrue();
        assertThat(done.value().finalFare()).isEqualByComparingTo("160");
    }

    @Test
    void onlyTheSenderAndHerPartnerSeeTheParcelAndThePartnerNeverSeesTheCodeOrNumber() {
        UUID trip = parcel(BookingStatus.ACCEPTED);

        assertThat(parcels.viewFor(trip, UUID.randomUUID())).isEmpty();
        CurrentAccountContext.set(new CurrentAccount(UUID.randomUUID(), AccountRole.CUSTOMER, UUID.randomUUID()));
        assertThatThrownBy(() -> controller.parcel(trip))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));

        var senderView = parcels.viewFor(trip, sender).orElseThrow();
        assertThat(senderView.deliveryCode()).isEqualTo(DELIVERY_CODE);
        assertThat(senderView.receiverPhone()).isEqualTo("+919876543210");
        assertThat(senderView.receiverName()).isEqualTo("Priya Sharma");

        var partnerView = parcels.viewFor(trip, partner).orElseThrow();
        assertThat(partnerView.deliveryCode()).isNull();
        assertThat(partnerView.receiverPhone()).isNull();
        assertThat(partnerView.declaredValue()).isNull();
        assertThat(partnerView.receiverName()).isEqualTo("Priya");
    }

    @Test
    void theSenderCannotEndADeliveryHere() {
        UUID trip = parcel(BookingStatus.IN_PROGRESS);
        assertThat(error(bookings.endTripAtRidersRequest(trip, sender))).isEqualTo(BookingError.PARCEL_END_HERE_NOT_ALLOWED);
    }

    @Test
    void photosGoAfterTheRetentionPeriodExceptWhileADisputeIsOpen() {
        // One at a time: she cannot hold two active trips (V81), so the first is finished before the second.
        UUID kept = parcel(BookingStatus.ACCEPTED);
        upload(kept, ParcelPhotoKind.PICKUP);
        jdbc.update("update bookings set status = 'COMPLETED' where id = ?", kept);
        UUID gone = parcel(BookingStatus.ACCEPTED);
        upload(gone, ParcelPhotoKind.PICKUP);
        jdbc.update("update parcel_photos set taken_at = now() - interval '100 days' where booking_id in (?, ?)", kept, gone);
        jdbc.update("update parcel_deliveries set created_at = now() - interval '100 days' where booking_id in (?, ?)", kept, gone);
        assertThat(parcels.openDispute(kept, UUID.randomUUID(), "Sender says the box was crushed").isSuccess()).isTrue();

        parcels.sweepRetention(Instant.now());

        String goneKey = jdbc.queryForObject("select storage_key from parcel_photos where booking_id = ?", String.class, gone);
        assertThat(goneKey).isNull();
        assertThat(jdbc.queryForObject("select deleted_at is not null from parcel_photos where booking_id = ?", Boolean.class, gone)).isTrue();
        assertThat(parcelRows.findById(gone).orElseThrow().getReceiverPhone()).as("the receiver's number goes too").isNull();
        assertThat(jdbc.queryForObject("select count(*) from stored_documents where account_id = ?", Integer.class, partner))
                .as("only the disputed delivery's file is left").isEqualTo(1);

        assertThat(jdbc.queryForObject("select storage_key from parcel_photos where booking_id = ?", String.class, kept)).isNotNull();
        assertThat(parcelRows.findById(kept).orElseThrow().getReceiverPhone()).isNotNull();
    }
}
