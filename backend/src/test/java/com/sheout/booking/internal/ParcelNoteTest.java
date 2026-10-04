package com.sheout.booking.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A delivery's parcel note: tidied, bounded, and never a way to pass a phone number. */
class ParcelNoteTest {

    @Test
    void aNoteIsOneTidyLineOfAtMost300Characters() {
        assertThat(BookingService.cleanParcelNote(null)).isNull();
        assertThat(BookingService.cleanParcelNote("   \n\t ")).isNull();
        assertThat(BookingService.cleanParcelNote("  Marketplace order MCSQ87K:\n  silk   saree ")).isEqualTo("Marketplace order MCSQ87K: silk saree");
        assertThat(BookingService.cleanParcelNote("x".repeat(400))).hasSize(300);
    }

    @Test
    void aPhoneNumberIsSpottedHoweverItIsTypedAndAProductCodeOrPriceIsNot() {
        for (String phone : new String[] {"call 9876543210", "+91 98765 43210", "98765-43210", "080 2345 6789"}) {
            assertThat(BookingService.PHONE_LIKE.matcher(phone).find()).as(phone).isTrue();
        }
        for (String fine : new String[] {"Marketplace order MCSQ87K: saree", "2 boxes, 1500 rupees", "Flat 302, 4th floor", "7K9M2XQ"}) {
            assertThat(BookingService.PHONE_LIKE.matcher(fine).find()).as(fine).isFalse();
        }
    }
}
