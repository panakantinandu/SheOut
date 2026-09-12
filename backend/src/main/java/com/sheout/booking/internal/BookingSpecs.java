package com.sheout.booking.internal;

import com.sheout.booking.BookingQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the booking-search predicate from whatever filters were asked for.
 * <p>
 * This replaces a single JPQL query that used the `:param IS NULL OR ...`
 * idiom for every optional filter. That idiom does not survive contact with
 * Postgres: a parameter that appears only inside an IS NULL test gives it
 * nothing to infer a type from, so it answered "could not determine data
 * type of parameter $41", and a nullable String inside CONCAT for the LIKE
 * came through as bytea, giving "operator does not exist: text ~~ bytea".
 * Both were only visible against a real database - the code compiled and
 * read correctly.
 * <p>
 * A Specification sidesteps the whole class of problem rather than papering
 * over it with casts: an absent filter contributes no predicate at all, so
 * there is no untyped parameter for the planner to puzzle over and the SQL
 * only ever contains the conditions actually requested.
 */
final class BookingSpecs {

    private BookingSpecs() {
    }

    static Specification<BookingEntity> matching(UUID customerId, UUID driverId, BookingQuery query) {
        BookingQuery q = (query == null ? BookingQuery.unfiltered() : query).normalized();
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Ownership scope, not a filter: the self-service callers always
            // pass exactly one of these, so a customer can only ever page
            // through their own trips. The ops console passes neither.
            if (customerId != null) predicates.add(cb.equal(root.get("customerId"), customerId));
            if (driverId != null) predicates.add(cb.equal(root.get("driverId"), driverId));

            if (q.status() != null) predicates.add(cb.equal(root.get("status"), q.status()));
            if (q.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.from()));
            if (q.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), q.to()));

            // normalized() guarantees a non-empty set, and a set covering
            // every category is the same as no filter - so skip it rather
            // than emit an IN over all of them.
            if (q.categories().size() < BookingQuery.allCategories().size()) {
                predicates.add(root.get("category").in(q.categories()));
            }

            if (q.text() != null) {
                String needle = "%" + q.text() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("pickup").get("label")), needle),
                        cb.like(cb.lower(root.get("drop").get("label")), needle)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
