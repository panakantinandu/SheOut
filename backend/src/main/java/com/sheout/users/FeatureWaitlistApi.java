package com.sheout.users;

/**
 * Read-only demand figures for features not built yet, for the ops console.
 * Who signed up stays inside users; other modules see only the count.
 */
public interface FeatureWaitlistApi {

    long countInterested(WaitlistFeature feature);
}
