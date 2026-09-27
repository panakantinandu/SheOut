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

    @Column(nullable = false)
    private boolean active;

    protected ProductEntity() {
        // JPA
    }

    ProductEntity(UUID sellerId, String title, String description, BigDecimal displayPrice, boolean active) {
        this.sellerId = sellerId;
        update(title, description, displayPrice, active);
    }

    void update(String title, String description, BigDecimal displayPrice, boolean active) {
        this.title = title;
        this.description = description;
        this.displayPrice = displayPrice;
        this.active = active;
    }

    public UUID getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getDisplayPrice() { return displayPrice; }
    public boolean isActive() { return active; }
}
