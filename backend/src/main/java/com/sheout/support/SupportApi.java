package com.sheout.support;

import java.util.Optional;

public interface SupportApi {

    /**
     * The number to offer a rider or a partner who needs to speak to
     * somebody, or empty when none is configured.
     * <p>
     * Empty rather than a placeholder, so a caller can tell "we have no
     * number" from "here is a number" and hide the action instead of
     * offering a button that dials nothing.
     */
    Optional<String> supportPhoneNumber();
}
