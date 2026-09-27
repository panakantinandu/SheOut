package com.sheout.users;

import java.util.List;
import java.util.UUID;

/**
 * Demand for features not built yet: the count, for the ops console, and -
 * once a feature ships - who asked to be told, so notifications can tell
 * exactly them. Account ids only; nothing else about them leaves users.
 */
public interface FeatureWaitlistApi {

    long countInterested(WaitlistFeature feature);

    /** Who tapped "Notify me" - for telling exactly them, once the feature is real. */
    List<UUID> interestedAccountIds(WaitlistFeature feature);
}
