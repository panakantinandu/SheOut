package com.sheout.assistant.internal;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Does this message sound like someone in danger right now?
 * <p>
 * Checked before the model is ever asked, with no network call and no API
 * key, so a woman in trouble is sent to SOS at once even if the assistant is
 * down, rate-limited or slow. It is tuned for recall, not precision: telling
 * someone who was only annoyed "if you are in danger, here is SOS" costs a
 * tap; missing someone who is being followed is not an acceptable trade.
 * The model is also told to answer EMERGENCY for anything this misses.
 * <p>
 * "help" on its own is a match - "help", "help!!", "please help" - but not
 * "help with my payment", which would send every billing question to SOS.
 * English, romanised Hindi/Telugu ("bachao", "kapadandi") and the scripts.
 * The Hindi and Telugu terms are pending native-speaker review with the rest
 * of the safety copy (docs/SAFETY_TRANSLATION_REVIEW.md).
 */
@Component
class EmergencyDetector {

    private static final List<Pattern> PATTERNS = List.of(
            // English
            Pattern.compile("^\\W*(please\\W+)?(help|sos|emergency)\\W*(me|us|now|asap|urgent(ly)?|quick(ly)?|fast|please|pls|plz)?\\W*$"),
            Pattern.compile("\\b(help|save)\\s+(me|us)\\b"),
            Pattern.compile("\\b(somebody|someone|anyone)\\s+help\\b"),
            Pattern.compile("\\bneed\\s+help\\s+(now|right\\s+now|urgently|fast|quickly|immediately)\\b"),
            Pattern.compile("\\b(i'?m|i\\s+am|feel(ing)?|so|very|really)\\s+(scared|afraid|frightened|terrified|unsafe|in\\s+danger)\\b"),
            Pattern.compile("\\b(scared|terrified|frightened)\\b"),
            Pattern.compile("\\b(following|followed|follows|stalking|stalked|chasing|chased)\\s+(me|us)\\b"),
            Pattern.compile("\\bbeing\\s+(followed|stalked|chased|watched)\\b"),
            Pattern.compile("\\b(touch(ed|ing)?|grab(bed|bing)?|hit(ting)?|hurt(ing)?|attack(ed|ing)?|threaten(ed|ing)?)\\s+me\\b"),
            Pattern.compile("\\b(harass(ed|ing|ment)?|molest(ed|ing)?|assault(ed)?|rape(d)?|kidnap(ped|ping)?|abduct(ed|ing)?)\\b"),
            Pattern.compile("\\b(won'?t|will\\s+not|not)\\s+let\\s+me\\s+(out|go|leave|get\\s+out)\\b"),
            Pattern.compile("\\blocked\\s+(me\\s+in|the\\s+doors?|in)\\b"),
            Pattern.compile("\\b(wrong|different|strange)\\s+(way|road|route|direction)\\b.*\\b(scared|afraid|won'?t\\s+stop|not\\s+stopping)\\b"),
            Pattern.compile("\\b(not|won'?t)\\s+stop(ping)?\\s+the\\s+(car|auto|bike|vehicle)\\b"),
            Pattern.compile("\\b(knife|gun|weapon)\\b"),
            Pattern.compile("\\b(in\\s+danger|unsafe\\s+right\\s+now|call\\s+(the\\s+)?police|trapped)\\b"),
            // Romanised Hindi / Hinglish
            Pattern.compile("\\b(bachao|bachaao|bacha\\s+lo|madad\\s+karo|madat\\s+karo|dar\\s+lag|darr\\s+lag|peecha|pichha|picha)\\b"),
            Pattern.compile("\\b(khatra|khatre\\s+mein|chhed|ched\\s+raha)\\b"),
            // Romanised Telugu
            Pattern.compile("\\b(kapadandi|kaapadandi|kapadu|bhayam|bayam\\s+ga|vembadi)\\b"),
            // Hindi script
            Pattern.compile("(बचाओ|बचा लो|मदद करो|मदद कीजिए|डर लग|पीछा|छेड़|ख़तरा|खतरा|ख़तरे|खतरे)"),
            // Telugu script
            Pattern.compile("(కాపాడండి|కాపాడు|రక్షించండి|సహాయం చేయండి|భయం|భయంగా|వెంబడి|ప్రమాదం)")
    );

    boolean soundsLikeEmergency(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String text = Normalizer.normalize(message, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replaceAll("\\s+", " ").trim();
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
