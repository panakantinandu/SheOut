package com.sheout.admin.internal;

import com.sheout.booking.RouteReviewItem;

/**
 * One flagged trip in the console's Route Review, with the two people on it
 * named. The trip facts come from booking unchanged; names and numbers are
 * joined here because neither booking nor users should know about the other.
 */
public record RouteReviewRow(
        RouteReviewItem trip,
        String partnerName,
        String partnerPhone,
        String riderName,
        String riderPhone
) {
}
