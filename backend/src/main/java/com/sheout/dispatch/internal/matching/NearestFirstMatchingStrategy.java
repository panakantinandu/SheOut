package com.sheout.dispatch.internal.matching;

import com.sheout.dispatch.internal.CandidateDriver;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Placeholder strategy - Redis GEOSEARCH already returns candidates sorted by distance, so this is mostly a defensive re-sort. */
@Component
public class NearestFirstMatchingStrategy implements MatchingStrategy {

    @Override
    public List<CandidateDriver> rank(List<CandidateDriver> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(CandidateDriver::distanceKm))
                .toList();
    }
}
