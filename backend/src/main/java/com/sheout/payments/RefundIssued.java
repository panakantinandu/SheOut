package com.sheout.payments;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SheOut gave money back to a rider. Published once per refund, after it is
 * recorded, so she can be told at once - by push, in her inbox, and by text
 * message: money back is something nobody should have to go looking for.
 * <ul>
 *   <li>SHEOUT_WALLET - a refund from the console, into her SheOut wallet;
 *       walletBalance is her balance after it.</li>
 *   <li>ORIGINAL_METHOD - a payment sent back to where it came from (her UPI
 *       or bank), as when a trip was paid for twice; banks take days, and she
 *       is told so. walletBalance is null.</li>
 * </ul>
 */
public class RefundIssued extends DomainEvent {

    public enum Destination { SHEOUT_WALLET, ORIGINAL_METHOD }

    private final UUID accountId;
    private final BigDecimal amount;
    private final Destination destination;
    private final UUID bookingId;
    private final BigDecimal walletBalance;

    public RefundIssued(UUID accountId, BigDecimal amount, Destination destination, UUID bookingId, BigDecimal walletBalance) {
        this.accountId = accountId;
        this.amount = amount;
        this.destination = destination;
        this.bookingId = bookingId;
        this.walletBalance = walletBalance;
    }

    public UUID accountId() { return accountId; }
    public BigDecimal amount() { return amount; }
    public Destination destination() { return destination; }
    /** The trip it was for, when it was for one. */
    public UUID bookingId() { return bookingId; }
    public BigDecimal walletBalance() { return walletBalance; }
}
