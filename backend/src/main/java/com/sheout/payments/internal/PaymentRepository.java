package com.sheout.payments.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/** Specification support is for the paged history search - see PaymentSpecs. */
interface PaymentRepository extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {

    Optional<PaymentEntity> findByBookingId(UUID bookingId);

    Optional<PaymentEntity> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Locked for every capture. Checkout confirming, a webhook arriving and a
     * partner confirming cash can all race for the same payment; each reads
     * the status under this lock, so exactly one captures and PaymentCaptured
     * is published once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findLockedByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findLockedByRazorpayOrderId(String razorpayOrderId);
}
