package com.sheout.driververification.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Whether two ways of writing a name are the same person's name.
 * <p>
 * WHY SO FORGIVING. An Indian name in Latin letters has no one spelling:
 * Lakshmi and Laxmi, Swathi and Swati, Seetha and Sita are the same name.
 * Documents shorten a surname or a father's name to an initial (K. Lakshmi),
 * and put the family name first or last. A strict match would flag most
 * honest applicants, and a flag nobody can trust teaches reviewers to wave
 * flags through. So this can only answer SAME, VARIANT or DIFFERENT, and
 * even DIFFERENT only asks a person to look - it never fails a check.
 * <p>
 * HOW. Each word is reduced to a sound-alike key: ksh and ks become x, the
 * aspirated consonants lose their h (th, dh, bh, kh, gh, ph, sh, ch), long
 * vowels become short (aa, ee, oo), y becomes i, w becomes v, doubled letters
 * become single, and a final a is dropped. Words are then paired in any
 * order, a one-letter word pairs with any word starting with that letter,
 * and two longer keys one letter apart still pair (a typo, or a spelling the
 * rules above miss). Honorifics (Smt, Kumari, Mrs) are ignored.
 * <p>
 * VARIANT when every word of the shorter name pairs with one in the longer
 * (a missing surname is a variant, not a different person); SAME when the
 * words are also the same, in the same order. Otherwise DIFFERENT.
 */
final class NameMatcher {

    enum Match { SAME, VARIANT, DIFFERENT }

    private static final Set<String> HONORIFICS = Set.of("smt", "shrimati", "srimati", "sm", "kumari", "kum", "km",
            "ms", "mrs", "miss", "mr", "dr", "sri", "shri", "sree", "selvi", "thiru", "tmt");

    private NameMatcher() {
    }

    static Match compare(String first, String second) {
        List<String> a = words(first);
        List<String> b = words(second);
        if (a.isEmpty() || b.isEmpty()) {
            return Match.DIFFERENT;
        }
        if (a.equals(b)) {
            return Match.SAME;
        }
        List<String> shorter = a.size() <= b.size() ? a : b;
        List<String> longer = a.size() <= b.size() ? b : a;
        List<String> left = new ArrayList<>(longer);
        boolean wholeWordPaired = false;
        // Whole words first, so an initial cannot take the word a whole one needed.
        List<String> initials = new ArrayList<>();
        for (String word : shorter) {
            if (word.length() == 1) {
                initials.add(word);
                continue;
            }
            String paired = left.stream().filter(w -> w.length() > 1 && sameWord(w, word)).findFirst().orElse(null);
            if (paired == null) {
                return Match.DIFFERENT;
            }
            left.remove(paired);
            wholeWordPaired = true;
        }
        for (String initial : initials) {
            String paired = left.stream().filter(w -> w.charAt(0) == initial.charAt(0)).findFirst().orElse(null);
            if (paired == null) {
                return Match.DIFFERENT;
            }
            left.remove(paired);
        }
        if (!wholeWordPaired) {
            // "K. L." against "Kavitha Lakshmi" says nothing about who she is.
            return Match.DIFFERENT;
        }
        return Match.VARIANT;
    }

    /** Lower case, accents and punctuation gone, honorifics dropped. */
    static List<String> words(String name) {
        if (name == null) {
            return List.of();
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim();
        List<String> out = new ArrayList<>();
        for (String word : plain.split(" ")) {
            if (!word.isEmpty() && !HONORIFICS.contains(word)) {
                out.add(word);
            }
        }
        return out;
    }

    private static boolean sameWord(String x, String y) {
        String a = key(x);
        String b = key(y);
        if (a.equals(b)) {
            return true;
        }
        return Math.min(a.length(), b.length()) >= 4 && oneEditApart(a, b);
    }

    /** The sound-alike key - see the class comment. */
    static String key(String word) {
        String s = word;
        s = s.replace("ksh", "x").replace("ks", "x");
        for (String[] pair : new String[][]{{"th", "t"}, {"dh", "d"}, {"bh", "b"}, {"kh", "k"}, {"gh", "g"},
                {"ph", "f"}, {"jh", "j"}, {"sh", "s"}, {"ch", "c"}, {"w", "v"}, {"z", "j"}, {"q", "k"}, {"y", "i"},
                {"ee", "i"}, {"ii", "i"}, {"oo", "u"}, {"uu", "u"}, {"aa", "a"}}) {
            s = s.replace(pair[0], pair[1]);
        }
        StringBuilder single = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (single.isEmpty() || single.charAt(single.length() - 1) != c) {
                single.append(c);
            }
        }
        s = single.toString();
        if (s.length() > 2 && s.endsWith("a")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean oneEditApart(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        boolean edited = false;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (edited) {
                return false;
            }
            edited = true;
            if (a.length() > b.length()) {
                i++;
            } else if (b.length() > a.length()) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return true;
    }
}
