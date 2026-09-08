package com.sheout.dispatch.internal.matching;

import com.sheout.dispatch.internal.CandidateDriver;

import java.util.List;

/**
 * The swap point for how eligible candidates get ranked/picked. Nearest-
 * first today; a future strategy factoring in driver heading, ETA, or
 * acceptance rate only means replacing the implementation - DispatchService
 * and the offer/retry workflow around it never change.
 */
public interface MatchingStrategy {

    /** candidates is already restricted to eligible (online, right vehicle type) drivers - ranks them, best first. */
    List<CandidateDriver> rank(List<CandidateDriver> candidates);
}
