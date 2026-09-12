package com.sheout.payments.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/** Specification support is for the paged history search - see PaymentSpecs. */
interface PaymentRepository extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {

    Optional<PaymentEntity> findByBookingId(UUID bookingId);

    Optional<PaymentEntity> findByRazorpayOrderId(String razorpayOrderId);
}
