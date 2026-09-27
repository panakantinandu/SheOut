package com.sheout.booking.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import com.sheout.sharedkernel.warmup.WarmUp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * A rider's first minutes, run once before real ones arrive: the quote, the
 * whole booking request up to and including its insert (rolled back, and
 * without the event that would start dispatch), and the same requests
 * through the web stack. See sharedkernel.warmup.WarmUp.
 */
@Component
class BookingWarmUp implements WarmUp {

    // Central Hyderabad, on roads, well inside the service area and the map.
    private static final GeoAddress PICKUP = new GeoAddress("warm-up pickup", 17.4486, 78.3908);
    private static final GeoAddress DROP = new GeoAddress("warm-up drop", 17.4600, 78.3570);
    private static final String TRIP_JSON = """
            {"type":"RIDE","category":"BIKE",\
            "pickup":{"label":"warm-up pickup","lat":17.4486,"lng":78.3908},\
            "drop":{"label":"warm-up drop","lat":17.4600,"lng":78.3570}}""";

    private final BookingService bookings;
    private final TransactionTemplate rolledBack;
    private final ObjectMapper json;

    BookingWarmUp(BookingService bookings, PlatformTransactionManager transactions, ObjectMapper json) {
        this.bookings = bookings;
        this.rolledBack = new TransactionTemplate(transactions);
        this.json = json;
    }

    @Override
    public String name() {
        return "booking";
    }

    @Override
    public void inProcess() {
        UUID nobody = UUID.randomUUID();
        bookings.quoteFare(BookingCategory.BIKE, PICKUP, DROP);
        bookings.previewPromotion(nobody, java.math.BigDecimal.valueOf(80));
        rolledBack.executeWithoutResult(status -> {
            status.setRollbackOnly();
            try {
                json.writeValueAsString(bookings.warmUpRequestPath(nobody, BookingCategory.BIKE, PICKUP, DROP));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public List<Request> requests() {
        return List.of(
                Request.post("/api/v1/bookings/quote", TRIP_JSON),
                Request.post("/api/v1/bookings", TRIP_JSON),
                Request.get("/api/v1/bookings/me"),
                Request.get("/api/v1/bookings/me/payment-hold"),
                Request.get("/api/v1/bookings/" + UUID.randomUUID()),
                Request.get("/api/v1/payments/bookings/" + UUID.randomUUID()));
    }
}
