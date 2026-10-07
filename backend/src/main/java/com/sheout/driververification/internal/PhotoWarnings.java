package com.sheout.driververification.internal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What her phone said about a photo before she sent it anyway: BLURRY,
 * GLARE, DARK or NO_FACE. The phone checks, she decides; "Use anyway" sends
 * the photo with these, and they put the check in front of a person
 * (CheckFinding.PHOTO_QUALITY) rather than refusing it.
 * <p>
 * Kept as a short comma-separated string of known words, in a stable order,
 * so whatever a client sends cannot fill a column with anything else.
 */
final class PhotoWarnings {

    private static final Set<String> KNOWN = Set.of("BLURRY", "GLARE", "DARK", "NO_FACE");

    private PhotoWarnings() {
    }

    /** Null when there is nothing known to keep. */
    static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<String> kept = Arrays.stream(raw.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(KNOWN::contains)
                .collect(Collectors.toCollection(TreeSet::new));
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    /** As a reviewer reads it: "blurry, too dark". */
    static String plain(String cleaned) {
        if (cleaned == null) {
            return "";
        }
        return Arrays.stream(cleaned.split(",")).map(w -> switch (w) {
            case "BLURRY" -> "blurry";
            case "GLARE" -> "shiny with glare";
            case "DARK" -> "too dark";
            case "NO_FACE" -> "missing a clear face";
            default -> w.toLowerCase(Locale.ROOT);
        }).collect(Collectors.joining(", "));
    }
}
