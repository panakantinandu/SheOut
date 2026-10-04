package com.sheout.marketplace;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Something about a seller's stock she should hear - notifications tells her.
 * <ul>
 *   <li>DELIVERED - a SheOut parcel carrying {@code quantity} of the product
 *       was delivered and her stock went down: {@code left} remain, or none
 *       and it is marked out of stock.</li>
 *   <li>ENQUIRIES - {@code quantity} buyers asked about it today; did she
 *       sell any? (A sale made off the app is one SheOut cannot see.)</li>
 * </ul>
 */
public class SellerStockNotice extends DomainEvent {

    public enum Kind { DELIVERED, ENQUIRIES }

    private final UUID accountId;
    private final Kind kind;
    private final String productTitle;
    private final String productCode;
    private final int quantity;
    private final Integer left;

    public SellerStockNotice(UUID accountId, Kind kind, String productTitle, String productCode, int quantity, Integer left) {
        this.accountId = accountId;
        this.kind = kind;
        this.productTitle = productTitle;
        this.productCode = productCode;
        this.quantity = quantity;
        this.left = left;
    }

    public UUID accountId() { return accountId; }
    public Kind kind() { return kind; }
    public String productTitle() { return productTitle; }
    public String productCode() { return productCode; }
    /** Delivered: how many the parcel carried. Enquiries: how many buyers asked. */
    public int quantity() { return quantity; }
    /** Delivered: how many are left now; 0 when it sold out. Null for enquiries. */
    public Integer left() { return left; }
}
