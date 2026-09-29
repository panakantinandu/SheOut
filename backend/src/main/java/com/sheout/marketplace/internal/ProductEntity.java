package com.sheout.marketplace.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One product or service in a seller's shop. The price is shown, never charged. */
@Entity
@Table(name = "seller_products")
public class ProductEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal displayPrice;

    /** The price before a discount, shown struck through. Null when there is no discount; above displayPrice when set. */
    @Column(precision = 10, scale = 2)
    private BigDecimal originalPrice;

    /** Short reference code, e.g. 7K9M2XQ - see ProductCodes. Set once, never changed. */
    @Column(nullable = false, unique = true, length = 8, updatable = false)
    private String code;

    @Column(nullable = false)
    private boolean active;

    protected ProductEntity() {
        // JPA
    }

    ProductEntity(UUID sellerId, String code, String title, String description, BigDecimal displayPrice,
                  BigDecimal originalPrice, boolean active) {
        this.sellerId = sellerId;
        this.code = code;
        update(title, description, displayPrice, originalPrice, active);
    }

    void update(String title, String description, BigDecimal displayPrice, BigDecimal originalPrice, boolean active) {
        this.title = title;
        this.description = description;
        this.displayPrice = displayPrice;
        this.originalPrice = originalPrice;
        this.active = active;
    }

    public UUID getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getDisplayPrice() { return displayPrice; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public String getCode() { return code; }
    public boolean isActive() { return active; }
}
