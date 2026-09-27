package com.sheout.marketplace.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

interface ProductRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    List<ProductEntity> findBySellerIdOrderByCreatedAtAsc(UUID sellerId);

    long countBySellerId(UUID sellerId);
}
