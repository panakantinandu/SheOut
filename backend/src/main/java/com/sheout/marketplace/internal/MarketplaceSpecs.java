package com.sheout.marketplace.internal;

import com.sheout.marketplace.SellerCategory;
import com.sheout.marketplace.SellerStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The directory and the console's seller list, as criteria rather than
 * JPQL: an optional category and an optional keyword are simply absent
 * predicates, instead of "(:x is null or ...)" clauses Postgres cannot type.
 */
final class MarketplaceSpecs {

    private MarketplaceSpecs() {
    }

    /**
     * Active products of live sellers. The keyword matches the product's
     * title or description, or the shop's name, ignoring case.
     */
    static Specification<ProductEntity> directory(SellerCategory category, String keyword) {
        return (root, query, cb) -> {
            Subquery<UUID> liveSellers = query.subquery(UUID.class);
            Root<SellerProfileEntity> seller = liveSellers.from(SellerProfileEntity.class);
            List<Predicate> sellerWhere = new ArrayList<>();
            sellerWhere.add(cb.equal(seller.get("status"), SellerStatus.ACTIVE));
            if (category != null) {
                sellerWhere.add(cb.equal(seller.get("category"), category));
            }
            String like = likePattern(keyword);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (like != null) {
                // A shop-name match brings in all of that shop's products.
                Subquery<UUID> namedSellers = query.subquery(UUID.class);
                Root<SellerProfileEntity> named = namedSellers.from(SellerProfileEntity.class);
                namedSellers.select(named.get("id")).where(cb.like(cb.lower(named.get("businessName")), like, '\\'));
                where.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\'),
                        root.get("sellerId").in(namedSellers)));
            }
            liveSellers.select(seller.get("id")).where(sellerWhere.toArray(new Predicate[0]));
            where.add(root.get("sellerId").in(liveSellers));
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    static Specification<SellerProfileEntity> sellers(SellerStatus status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            String like = likePattern(keyword);
            if (like != null) {
                where.add(cb.or(
                        cb.like(cb.lower(root.get("businessName")), like, '\\'),
                        cb.like(root.get("contactPhone"), like, '\\')));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /** "%sari%" for "Sari", with % and _ in what she typed matched literally. Null for nothing to match. */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
