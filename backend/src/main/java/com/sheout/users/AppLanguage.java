package com.sheout.users;

/**
 * The languages the apps are translated into. Stored as the lower-case code
 * the apps use, so the value round-trips without a mapping table.
 */
public enum AppLanguage {
    EN("en"),
    TE("te"),
    HI("hi");

    private final String code;

    AppLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static java.util.Optional<AppLanguage> fromCode(String code) {
        if (code == null) {
            return java.util.Optional.empty();
        }
        for (AppLanguage language : values()) {
            if (language.code.equalsIgnoreCase(code.trim())) {
                return java.util.Optional.of(language);
            }
        }
        return java.util.Optional.empty();
    }
}
