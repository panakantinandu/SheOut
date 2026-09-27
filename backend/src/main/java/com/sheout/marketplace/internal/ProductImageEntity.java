package com.sheout.marketplace.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A product photo, kept in DocumentStorage; this row is where it is and in what order. */
@Entity
@Table(name = "seller_product_images")
public class ProductImageEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false, length = 500)
    private String storageKey;

    @Column(nullable = false)
    private int position;

    protected ProductImageEntity() {
        // JPA
    }

    ProductImageEntity(UUID productId, UUID sellerId, String storageKey, int position) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.storageKey = storageKey;
        this.position = position;
    }

    public UUID getProductId() { return productId; }
    public UUID getSellerId() { return sellerId; }
    public String getStorageKey() { return storageKey; }
    public int getPosition() { return position; }
}
