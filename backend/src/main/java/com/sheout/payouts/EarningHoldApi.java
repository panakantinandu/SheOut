package com.sheout.payouts;

import com.sheout.sharedkernel.Result;

import java.util.Optional;
import java.util.UUID;

/** Parcel earnings holds, for the admin console. Every decision needs a reason. */
public interface EarningHoldApi {

    Optional<EarningHold> find(UUID bookingId);

    /** Keeps it held past the dispute window, until an operator releases it. */
    Result<EarningHold, EarningHoldError> hold(UUID bookingId, UUID adminAccountId, String reason);

    /** Into her available balance now. */
    Result<EarningHold, EarningHoldError> release(UUID bookingId, UUID adminAccountId, String reason);
}
