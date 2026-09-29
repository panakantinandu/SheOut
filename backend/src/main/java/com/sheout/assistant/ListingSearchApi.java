package com.sheout.assistant;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Searching marketplace listings "in your own words", through the same
 * model, key and usage records as SheOut Help.
 * <p>
 * The caller hands over real listings, each under a short reference it
 * chose, and gets back references only - in order of fit, and only ones it
 * handed over. The model never supplies product data; it can only point at
 * what it was given. Anything else - no key, the day's cap reached, a slow or
 * failed call - is an outcome other than OK, and the caller falls back to its
 * ordinary search.
 */
public interface ListingSearchApi {

    Ranking rank(UUID accountId, String query, List<Candidate> candidates);

    /** One listing as the model sees it. Text is the seller's own and treated as data, never as instructions. */
    record Candidate(String ref, String title, String category, BigDecimal price, BigDecimal originalPrice,
                     String description, String area, String shop) {
    }

    /** refs is empty unless outcome is OK; OK with no refs means nothing fitted. */
    record Ranking(Outcome outcome, List<String> refs, int remainingToday) {
    }

    enum Outcome { OK, UNAVAILABLE, LIMIT_REACHED, FAILED }
}
