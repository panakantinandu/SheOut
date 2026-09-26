package com.sheout.campaigns.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One discount on one trip: RESERVED at booking, CONSUMED at completion, RELEASED if the trip never happened. */
@Entity
@Table(name = "promotion_redemptions")
public class PromotionRedemptionEntity extends BaseEntity {

    enum Status { RESERVED, CONSUMED, RELEASED }

    @Column(nullable = false)
    private UUID promotionId;
    private UUID grantId;
    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false, unique = true)
    private UUID bookingId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    protected PromotionRedemptionEntity() {
    }

    PromotionRedemptionEntity(UUID promotionId, UUID grantId, UUID accountId, UUID bookingId, BigDecimal fare, BigDecimal discount) {
        this.promotionId = promotionId;
        this.grantId = grantId;
        this.accountId = accountId;
        this.bookingId = bookingId;
        this.fare = fare;
        this.discount = discount;
        this.status = Status.RESERVED;
    }

    void setStatus(Status status) { this.status = status; }
    UUID getPromotionId() { return promotionId; }
    UUID getGrantId() { return grantId; }
    UUID getAccountId() { return accountId; }
    UUID getBookingId() { return bookingId; }
    BigDecimal getDiscount() { return discount; }
    Status getStatus() { return status; }
}
