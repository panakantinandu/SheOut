package com.sheout.notifications.internal;

import com.sheout.users.AppLanguage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue stays whole: every message in every language, and each
 * translation asking for exactly the values the English does - a Hindi line
 * that dropped "{fare}" would tell her there is a fare without saying what.
 */
class NotificationCopyTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");

    @SuppressWarnings("unchecked")
    private static Map<AppLanguage, Map<String, String>> text() throws Exception {
        Field f = NotificationCopy.class.getDeclaredField("TEXT");
        f.setAccessible(true);
        return (Map<AppLanguage, Map<String, String>>) f.get(null);
    }

    private static Set<String> placeholders(String s) {
        Set<String> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(s);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyLanguageHasEveryMessageWithTheSamePlaceholders() throws Exception {
        Map<AppLanguage, Map<String, String>> text = text();
        Map<String, String> english = text.get(AppLanguage.EN);
        for (AppLanguage language : AppLanguage.values()) {
            Map<String, String> local = text.get(language);
            assertThat(local.keySet()).as("keys in %s", language).isEqualTo(english.keySet());
            for (Map.Entry<String, String> e : english.entrySet()) {
                assertThat(placeholders(local.get(e.getKey())))
                        .as("placeholders of %s in %s", e.getKey(), language)
                        .isEqualTo(placeholders(e.getValue()));
            }
        }
    }

    @Test
    void aMessageComesInHerLanguageWithTheEnglishAlongsideForSms() {
        UUID rider = UUID.randomUUID();
        NotificationCopy copy = new NotificationCopy(id -> Optional.of(AppLanguage.HI));
        NotificationCopy.Localized m = copy.render(rider, "bookingCompleted", language -> Map.of("fare", "₹77.09"));
        assertThat(m.body()).contains("₹77.09").contains("किराया");
        assertThat(m.englishBody()).isEqualTo("Fare ₹77.09. Pay in the app, from your SheOut wallet or online, to finish the trip.");
    }

    @Test
    void noLanguageChosenMeansEnglish() {
        NotificationCopy copy = new NotificationCopy(id -> Optional.empty());
        assertThat(copy.render(UUID.randomUUID(), "noDrivers", language -> Map.of()).title()).isEqualTo("No partners available right now");
    }

    @Test
    void safetyTextKeepsTheEnglishBeneathUntilReviewed() {
        NotificationCopy copy = new NotificationCopy(id -> Optional.of(AppLanguage.TE));
        String text = copy.safety(AppLanguage.TE, "sosNoneReached", Map.of());
        assertThat(text).contains("112").endsWith("We could not text any of your emergency contacts. Call 112 if you are in danger.");
        assertThat(copy.safety(AppLanguage.EN, "sosNoneReached", Map.of())).doesNotContain("\n");
    }
}
