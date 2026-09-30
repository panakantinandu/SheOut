package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceViews.DirectoryFilter;
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
     * title or description, or the shop's name, ignoring case; categories
     * and area narrow by the shop, the price bounds (inclusive) by the
     * product's display price.
     */
    static Specification<ProductEntity> directory(DirectoryFilter filter) {
        return (root, query, cb) -> {
            Subquery<UUID> liveSellers = query.subquery(UUID.class);
            Root<SellerProfileEntity> seller = liveSellers.from(SellerProfileEntity.class);
            List<Predicate> sellerWhere = new ArrayList<>();
            sellerWhere.add(cb.equal(seller.get("status"), SellerStatus.ACTIVE));
            if (!filter.categories().isEmpty()) {
                sellerWhere.add(seller.get("category").in(filter.categories()));
            }
            String areaLike = likePattern(filter.area());
            if (areaLike != null) {
                sellerWhere.add(cb.like(cb.lower(seller.get("area")), areaLike, '\\'));
            }
            String like = likePattern(filter.keyword());
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (filter.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("displayPrice"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("displayPrice"), filter.maxPrice()));
            }
            if (like != null) {
                // A shop-name match brings in all of that shop's products.
                Subquery<UUID> namedSellers = query.subquery(UUID.class);
                Root<SellerProfileEntity> named = namedSellers.from(SellerProfileEntity.class);
                // Her shop's name, or what she said she sells under Other.
                namedSellers.select(named.get("id")).where(cb.or(
                        cb.like(cb.lower(named.get("businessName")), like, '\\'),
                        cb.like(cb.lower(named.get("customCategory")), like, '\\')));
                List<Predicate> matches = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\'),
                        root.get("sellerId").in(namedSellers)));
                // A product code, typed as a customer read it off a message: that exact product.
                String code = ProductCodes.normalize(filter.keyword());
                if (code != null) {
                    matches.add(cb.equal(root.get("code"), code));
                }
                where.add(cb.or(matches.toArray(new Predicate[0])));
            }
            liveSellers.select(seller.get("id")).where(sellerWhere.toArray(new Predicate[0]));
            where.add(root.get("sellerId").in(liveSellers));
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /**
     * The directory narrowed by the filter, matching any of these words in a
     * product's title or description or its shop's name. For when a search
     * "in your own words" falls back to keywords: the whole sentence as one
     * phrase ("something for a wedding under 2000") matches nothing, while
     * its words ("wedding") find what she meant.
     */
    static Specification<ProductEntity> directoryAnyWord(DirectoryFilter filter, List<String> words) {
        DirectoryFilter withoutKeyword = new DirectoryFilter(filter.categories(), null, filter.minPrice(), filter.maxPrice(), filter.area());
        Specification<ProductEntity> any = (root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();
            for (String word : words) {
                String like = likePattern(word);
                Subquery<UUID> namedSellers = query.subquery(UUID.class);
                Root<SellerProfileEntity> named = namedSellers.from(SellerProfileEntity.class);
                namedSellers.select(named.get("id")).where(cb.or(
                        cb.like(cb.lower(named.get("businessName")), like, '\\'),
                        cb.like(cb.lower(named.get("customCategory")), like, '\\')));
                matches.add(cb.like(cb.lower(root.get("title")), like, '\\'));
                matches.add(cb.like(cb.lower(root.get("description")), like, '\\'));
                matches.add(root.get("sellerId").in(namedSellers));
            }
            return cb.or(matches.toArray(new Predicate[0]));
        };
        return directory(withoutKeyword).and(any);
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
                        cb.like(cb.lower(root.get("area")), like, '\\'),
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
