package com.sheout.support.internal;

import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketPriority;
import com.sheout.support.SupportTicketQuery;
import com.sheout.support.SupportTicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * The ticket-search predicate, built the same way BookingSpecs and
 * PaymentSpecs are and for the same reason: the `:param IS NULL OR ...` JPQL
 * idiom leaves Postgres with untyped parameters it cannot plan. An absent
 * filter contributes no predicate at all.
 */
final class SupportTicketSpecs {

    private SupportTicketSpecs() {
    }

    static Specification<SupportTicketEntity> matching(SupportTicketQuery query) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.raisedBy() != null) {
                predicates.add(cb.equal(root.get("raisedByAccountId"), query.raisedBy()));
            }
            // A set covering every value narrows nothing, so it adds nothing.
            if (query.statusesOrAll().size() < SupportTicketStatus.values().length) {
                predicates.add(root.get("status").in(query.statusesOrAll()));
            }
            if (query.categoriesOrAll().size() < SupportTicketCategory.values().length) {
                predicates.add(root.get("category").in(query.categoriesOrAll()));
            }
            if (query.prioritiesOrAll().size() < SupportTicketPriority.values().length) {
                predicates.add(root.get("priority").in(query.prioritiesOrAll()));
            }
            if (query.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
            if (query.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.to()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
