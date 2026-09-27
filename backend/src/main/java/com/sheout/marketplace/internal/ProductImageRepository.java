package com.sheout.marketplace.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ProductImageRepository extends JpaRepository<ProductImageEntity, UUID> {

    List<ProductImageEntity> findByProductIdOrderByPositionAsc(UUID productId);

    List<ProductImageEntity> findByProductIdInOrderByPositionAsc(Collection<UUID> productIds);

    List<ProductImageEntity> findBySellerId(UUID sellerId);

    long countBySellerId(UUID sellerId);

    long countByProductId(UUID productId);

    @Query("select coalesce(max(i.position), -1) from ProductImageEntity i where i.productId = :productId")
    int maxPosition(@Param("productId") UUID productId);
}
