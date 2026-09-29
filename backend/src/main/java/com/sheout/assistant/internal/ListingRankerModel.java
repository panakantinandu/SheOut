package com.sheout.assistant.internal;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Optional;

/**
 * The model behind marketplace search "in your own words" - an interface so
 * ListingSearchService and its tests never depend on a live API call.
 * ClaudeListingRanker is the one implementation.
 */
interface ListingRankerModel {

    boolean available();

    /** Empty when the call failed, timed out or was refused - the caller falls back to keyword search. */
    Optional<Result> rank(String systemPrompt, String request);

    record Result(Picks picks, HelpModel.Usage usage) {
    }

    /** Structured output: references only, never product details. */
    record Picks(
            @JsonPropertyDescription("The refs (such as p3) of the listings that fit the shopper's request, best fit first. "
                    + "At most 20. Only refs that appear in the listings given. An empty list when nothing fits.")
            List<String> refs
    ) {
    }
}
