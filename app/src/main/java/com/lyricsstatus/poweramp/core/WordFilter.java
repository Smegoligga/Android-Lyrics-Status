package com.lyricsstatus.poweramp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional censoring of the status text before it is sent to Discord.
 *
 * Two independent switches, both off by default; with both off the text is returned untouched:
 *  - censor: built-in list of strong swears and slurs
 *  - custom filter: the user's own comma/newline-separated words (whole-word, case-insensitive)
 *
 * Masking keeps the first letter: "fucking" becomes "f***ing", "bullshit" becomes "bulls***".
 * Mild words (damn, hell, crap…) aren't in the built-in list; add them to the custom filter if wanted.
 */
public final class WordFilter {

    /**
     * Matched anywhere inside a word: unambiguous roots, so compounds are caught too
     * (motherfucker, bullshit, shitty) without hitting innocent words.
     */
    private static final String[] ANYWHERE = {
            "fuck", "shit", "bitch", "asshole", "bastard", "whore", "slut", "wank", "piss",
            "cocksuck", "dickhead", "nigg(?:az|ahs?|as?|ers?)?", "faggot", "retard", "tranny", "wetback",
    };

    /**
     * Whole words only (with common endings), because these letters also appear inside ordinary words
     * (class, Scunthorpe, Dickens, peacock, spice, raccoon, Titus…).
     */
    private static final String[] WHOLE = {
            "ass(es)?", "cunt(s|y)?", "dick(s)?", "cock(s)?", "tit(s|ties)?", "twat(s)?", "prick(s)?", "fag(s|got|gots)?",
            "spic(s)?", "chink(s)?", "kike(s)?", "dyke(s)?", "coon(s)?", "gook(s)?", "beaner(s)?", "jizz",
    };

    private static final String NOT_LETTER_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{N}])";
    private static final Pattern BUILT_IN;

    static {
        StringBuilder sb = new StringBuilder("(?iu)");
        for (String w : ANYWHERE) sb.append(w).append('|');
        sb.append(NOT_LETTER_BEFORE).append("(?:");
        for (int i = 0; i < WHOLE.length; i++) sb.append(i == 0 ? "" : "|").append(WHOLE[i]);
        sb.append(')').append(NOT_LETTER_AFTER);
        BUILT_IN = Pattern.compile(sb.toString());
    }

    // cache of the last compiled custom pattern
    private static String cachedSource;
    private static Pattern cachedCustom;

    private WordFilter() {}

    /** Apply the filters enabled in settings. Returns the text unchanged when both are off. */
    public static String apply(String text, Settings s) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        if (s.censor) out = mask(BUILT_IN, out);
        if (s.customFilterOn) {
            Pattern p = custom(s.customFilter);
            if (p != null) out = mask(p, out);
        }
        return out;
    }

    static synchronized Pattern custom(String source) {
        if (source == null || source.trim().isEmpty()) return null;
        if (source.equals(cachedSource)) return cachedCustom;
        List<String> words = new ArrayList<>();
        for (String w : source.split("[,;\\n\\r]+")) {
            String t = w.trim();
            if (!t.isEmpty()) words.add(Pattern.quote(t));
        }
        Pattern p = null;
        if (!words.isEmpty()) {
            p = Pattern.compile("(?iu)" + NOT_LETTER_BEFORE + "(?:" + String.join("|", words) + ")" + NOT_LETTER_AFTER);
        }
        cachedSource = source;
        cachedCustom = p;
        return p;
    }

    /** Keep the first character of each match, star the rest (spaces inside a custom phrase are kept). */
    private static String mask(Pattern p, String text) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start());
            String w = m.group();
            int first = w.offsetByCodePoints(0, 1);
            sb.append(w, 0, first);
            for (int i = first; i < w.length(); ) {
                int cp = w.codePointAt(i);
                sb.append(Character.isWhitespace(cp) ? new String(Character.toChars(cp)) : "*");
                i += Character.charCount(cp);
            }
            last = m.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }
}
