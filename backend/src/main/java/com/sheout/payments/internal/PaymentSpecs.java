package com.sheout.payments.internal;

import com.sheout.payments.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The payment-search predicate. Built as a Specification for the same
 * reason BookingSpecs is: the `:param IS NULL OR ...` idiom leaves Postgres
 * with untyped parameters it cannot plan, and the failure only shows
 * against a real database. An absent filter contributes no predicate here.
 */
final class PaymentSpecs {

    private PaymentSpecs() {
    }

    static Specification<PaymentEntity> matching(
            Collection<UUID> bookingIds,
            PaymentStatus status,
            Instant from,
            Instant to,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Ownership scope: resolved from the caller's token, never from
            // a request parameter.
            predicates.add(root.get("bookingId").in(bookingIds));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            if (minAmount != null) predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            if (maxAmount != null) predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
