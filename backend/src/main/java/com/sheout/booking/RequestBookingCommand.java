package com.sheout.booking;

import java.util.UUID;

public record RequestBookingCommand(
        UUID customerId,
        BookingType type,
        BookingCategory category,
        GeoAddress pickup,
        GeoAddress drop
) {
}
