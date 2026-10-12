package com.sheout.booking;

import java.math.BigDecimal;

/**
 * What a sender says about a parcel when she books it, beyond the note:
 * who it is for, an optional declared value (for records only - SheOut
 * promises no compensation against it), and that she has read the
 * prohibited-items list and the size limit. Required for every delivery.
 *
 * @param prohibitedItemsVersion the version of the list she was shown; a
 *                               booking against an older list is refused,
 *                               so she always agrees to the list in force
 */
public record ParcelDetails(
        String receiverName,
        String receiverPhone,
        BigDecimal declaredValue,
        String prohibitedItemsVersion,
        boolean prohibitedItemsAcknowledged,
        boolean sizeAcknowledged
) {
}
