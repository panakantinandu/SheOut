package com.sheout.sharedkernel.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list, in the one shape every list endpoint returns - so the
 * apps and the ops console parse paging once rather than three times.
 * <p>
 * Offset-based rather than cursor-based, deliberately. A cursor is the right
 * answer for a feed that grows while you read it and where deep pages are
 * common; these are a person's own trip history and an operator's recent
 * bookings, read newest-first and rarely beyond a few pages. Offsets also
 * give the ops console the total it needs to say "42 bookings" and to render
 * page numbers, which a cursor cannot without a second count query. Spring
 * Data hands all of it over for free. If a list ever outgrows this, the
 * shape can carry a cursor alongside without breaking callers.
 * <p>
 * Never exposes the Spring Data Page type across the HTTP boundary: its
 * serialised form is large, unstable between versions, and leaks internals
 * the clients should not depend on.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages,
        boolean hasMore
) {

    /** Caps how much one request can ask for, however large a client asks. */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    /** For a list that is already in hand and paged in memory. */
    public static <T> PageResponse<T> of(List<T> all, int page, int pageSize) {
        int size = normalizePageSize(pageSize);
        int from = Math.min(Math.max(page, 0) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<T> slice = all.subList(from, to);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new PageResponse<>(slice, Math.max(page, 0), size, all.size(), totalPages, to < all.size());
    }

    /**
     * A client asking for 10,000 rows gets MAX_PAGE_SIZE, and one asking for
     * zero or a negative number gets the default rather than an empty page
     * that looks like "no results".
     */
    public static int normalizePageSize(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    public static int normalizePage(Integer requested) {
        return requested == null || requested < 0 ? 0 : requested;
    }
}
