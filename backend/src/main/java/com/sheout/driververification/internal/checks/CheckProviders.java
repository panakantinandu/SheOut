package com.sheout.driververification.internal.checks;

import com.sheout.driververification.CheckType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which provider answers which check.
 * <p>
 * One flat setting, VERIFICATION_CHECK_PROVIDERS, as TYPE=name pairs:
 * {@code SELFIE_MATCH=acme,LICENCE=acme}. Anything not named is "manual".
 * A name with no such provider, or one that does not handle the check, fails
 * startup: a safety check silently falling back to nobody is worse than not
 * starting.
 */
@Component
public class CheckProviders {

    private static final Logger log = LoggerFactory.getLogger(CheckProviders.class);

    private final Map<CheckType, CheckProvider> byType = new EnumMap<>(CheckType.class);

    public CheckProviders(List<CheckProvider> providers,
                          @Value("${sheout.verification.check-providers:}") String assignments) {
        CheckProvider manual = providers.stream().filter(p -> ManualCheckProvider.NAME.equals(p.name())).findFirst()
                .orElseThrow(() -> new IllegalStateException("The manual check provider is missing"));
        for (CheckType type : CheckType.values()) {
            byType.put(type, manual);
        }
        for (String pair : assignments.split(",")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("VERIFICATION_CHECK_PROVIDERS: expected TYPE=name, got " + pair.trim());
            }
            CheckType type;
            try {
                type = CheckType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("VERIFICATION_CHECK_PROVIDERS names an unknown check: " + parts[0].trim());
            }
            String name = parts[1].trim();
            CheckProvider provider = providers.stream().filter(p -> p.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("VERIFICATION_CHECK_PROVIDERS names an unknown provider: " + name));
            if (!provider.handles(type)) {
                throw new IllegalArgumentException("Check provider " + name + " does not handle " + type);
            }
            byType.put(type, provider);
        }
        Map<CheckType, String> names = new EnumMap<>(CheckType.class);
        byType.forEach((type, provider) -> names.put(type, provider.name()));
        log.info("Verification check providers: {}", names);
    }

    public CheckProvider forType(CheckType type) {
        return byType.get(type);
    }
}
