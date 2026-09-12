package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The account-search predicate, built the same way BookingSpecs is and for
 * the same reason - see that class for what the nullable-parameter JPQL
 * idiom does to Postgres.
 */
final class AccountSpecs {

    private AccountSpecs() {
    }

    static Specification<AccountEntity> matching(Set<AccountRole> roles, Boolean blocked, String text) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Always present and never empty - the caller says which roles
            // it wants, because the ops console deliberately excludes ADMIN.
            predicates.add(root.get("role").in(roles));

            // Three-state: null leaves it alone, TRUE means blocked, FALSE
            // means active. blockedAt IS the flag - see V8.
            if (Boolean.TRUE.equals(blocked)) predicates.add(cb.isNotNull(root.get("blockedAt")));
            if (Boolean.FALSE.equals(blocked)) predicates.add(cb.isNull(root.get("blockedAt")));

            if (text != null && !text.isBlank()) {
                String needle = "%" + text.trim().toLowerCase() + "%";
                // coalesce, because exactly one of the two is null on every
                // account: a phone signup has no email and a Google signup
                // has no phone, and LIKE against null matches nothing.
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("phoneNumber"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), needle)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
