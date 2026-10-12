package com.sheout.booking.internal;

import com.sheout.booking.BookingError;
import com.sheout.booking.ParcelDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** What a sender must give when she books a parcel, checked before anything is created. No Spring, no database. */
class ParcelDetailsRulesTest {

    private final ParcelRules rules = ParcelRules.defaults();
    private final ParcelDeliveryService service = new ParcelDeliveryService(null, null, null, null, null, null, rules, 30);

    private ParcelDetails details(String name, String phone, String value, String version, boolean items, boolean size) {
        return new ParcelDetails(name, phone, value == null ? null : new BigDecimal(value), version, items, size);
    }

    private Optional<BookingError> check(ParcelDetails d) {
        return service.validate(d);
    }

    @Test
    void aCompleteBookingPasses() {
        assertThat(check(details("Priya", "9876543210", "2500", rules.prohibitedItemsVersion(), true, true))).isEmpty();
        assertThat(check(details("Priya", "+91 98765-43210", null, rules.prohibitedItemsVersion(), true, true))).as("value is optional").isEmpty();
    }

    @Test
    void theReceiverAndBothAgreementsAreRequired() {
        String v = rules.prohibitedItemsVersion();
        assertThat(check(null)).contains(BookingError.PARCEL_DETAILS_REQUIRED);
        assertThat(check(details(" ", "9876543210", null, v, true, true))).contains(BookingError.PARCEL_DETAILS_REQUIRED);
        assertThat(check(details("Priya", "9876543210", null, v, false, true))).contains(BookingError.PARCEL_DETAILS_REQUIRED);
        assertThat(check(details("Priya", "9876543210", null, v, true, false))).contains(BookingError.PARCEL_DETAILS_REQUIRED);
        assertThat(check(details("Call 9876543210", "9876543210", null, v, true, true)))
                .as("a number hidden in the name").contains(BookingError.PARCEL_DETAILS_REQUIRED);
    }

    @Test
    void theNumberIsATenDigitIndianMobile() {
        String v = rules.prohibitedItemsVersion();
        assertThat(check(details("Priya", "12345", null, v, true, true))).contains(BookingError.PARCEL_RECEIVER_PHONE_INVALID);
        assertThat(check(details("Priya", "5876543210", null, v, true, true))).as("Indian mobiles start 6-9").contains(BookingError.PARCEL_RECEIVER_PHONE_INVALID);
        assertThat(ParcelDeliveryService.normalisePhone("09876543210")).isEqualTo("+919876543210");
        assertThat(ParcelDeliveryService.normalisePhone("+91 98765 43210")).isEqualTo("+919876543210");
    }

    @Test
    void sheAgreesToTheListInForceAndTheValueHasACap() {
        assertThat(check(details("Priya", "9876543210", null, "2020-01-01", true, true))).contains(BookingError.PARCEL_RULES_CHANGED);
        assertThat(check(details("Priya", "9876543210", "5001", rules.prohibitedItemsVersion(), true, true))).contains(BookingError.PARCEL_DECLARED_VALUE_TOO_HIGH);
        assertThat(check(details("Priya", "9876543210", "5000", rules.prohibitedItemsVersion(), true, true))).isEmpty();
        assertThat(check(details("Priya", "9876543210", "-1", rules.prohibitedItemsVersion(), true, true))).contains(BookingError.PARCEL_DECLARED_VALUE_TOO_HIGH);
    }

    @Test
    void thePartnerSeesAFirstNameAndOperatorsAMaskedNumber() {
        assertThat(ParcelDeliveryService.firstName("Priya Sharma")).isEqualTo("Priya");
        assertThat(ParcelDeliveryService.firstName("Priya")).isEqualTo("Priya");
        assertThat(ParcelDeliveryService.mask("+919876543210")).isEqualTo("+91******3210");
    }
}
