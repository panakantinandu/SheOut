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

    /** A seller's fee for one month - at most one, which uq_payments_seller_period keeps true. */
    Optional<PaymentEntity> findBySellerIdAndSellerPeriod(UUID sellerId, Integer sellerPeriod);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findLockedBySellerIdAndSellerPeriod(UUID sellerId, Integer sellerPeriod);

    /** Every fee a seller has started or paid, oldest month first. */
    java.util.List<PaymentEntity> findBySellerIdOrderBySellerPeriodAsc(UUID sellerId);

    /** The payment a partner's UPI QR is for. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findLockedByRazorpayQrId(String razorpayQrId);
}
