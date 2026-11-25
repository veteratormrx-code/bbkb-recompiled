package dev.bbkb.ime.core.suggestion;

import java.util.Locale;

/**
 * Space may replace the typed word only for one obvious slip. A dictionary neighbor that is a
 * different word (Gradle to Grade, дедлайн to делай, a dropped first letter) stays a suggestion.
 *
 * Allowed: a missing letter that is not the first or the last, an extra letter that repeats its
 * neighbor, or two adjacent letters swapped. The committed spelling keeps the case the user typed.
 *
 * Two guards come from AOSP LatinIME (and HeliBoard, which keeps the same decision): a word that
 * is mostly capitals was typed on purpose, and a suggestion that contains a space is a phrase,
 * not a spelling. LatinIME also refuses a correction whose Damerau-Levenshtein distance is past
 * {@code (length < 5 ? 2 : length / 2) + 1} once the word is at least 4 letters
 * ({@code shouldBlockAutoCorrectionBySafetyNet}).
 */
public final class AutoCorrectConfidence {

    /** Same floor as LatinIME {@code MINIMUM_SAFETY_NET_CHAR_LENGTH}. */
    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;

    private AutoCorrectConfidence() {
    }

    /**
     * The typed word with that one edit applied, or null when the correction is not that edit.
     * A hyphen or a capital after the first letter is left alone: compounds and camel-case names
     * are not slips.
     */
    public static String preservingCase(String typed, String correction, Locale locale) {
        if (typed == null || correction == null || typed.isEmpty() || correction.isEmpty()
                || typed.equals(correction)) {
            return null;
        }
        if (typed.indexOf('-') >= 0 || containsSpace(correction)) {
            return null;
        }
        if (blockedBySafetyNet(typed, correction, locale)) {
            return null;
        }
        for (int i = 1; i < typed.length(); i++) {
            if (Character.isUpperCase(typed.charAt(i))) {
                return null;
            }
        }
        Locale cmp = locale != null ? locale : Locale.ROOT;
        String loweredTyped = typed.toLowerCase(cmp);
        String loweredCorrection = correction.toLowerCase(cmp);
        if (loweredTyped.length() != typed.length() || loweredCorrection.length() != correction.length()) {
            return null;
        }
        if (loweredTyped.equals(loweredCorrection)) {
            return null;
        }
        String deleted = deleteDoubledInterior(typed, loweredTyped, loweredCorrection);
        if (deleted != null) {
            return deleted;
        }
        String inserted = insertInterior(typed, loweredTyped, correction, loweredCorrection, cmp);
        if (inserted != null) {
            return inserted;
        }
        return swapAdjacent(typed, loweredTyped, loweredCorrection);
    }

    /** Drop one interior letter that repeats the letter before or after it. */
    private static String deleteDoubledInterior(String typed, String loweredTyped, String loweredCorrection) {
        if (loweredTyped.length() != loweredCorrection.length() + 1) {
            return null;
        }
        for (int i = 1; i < loweredTyped.length() - 1; i++) {
            char dropped = loweredTyped.charAt(i);
            boolean repeatsNeighbor = dropped == loweredTyped.charAt(i - 1)
                    || dropped == loweredTyped.charAt(i + 1);
            if (!repeatsNeighbor) {
                continue;
            }
            if (loweredTyped.substring(0, i).concat(loweredTyped.substring(i + 1)).equals(loweredCorrection)) {
                return typed.substring(0, i) + typed.substring(i + 1);
            }
        }
        return null;
    }

    /** Put back one letter that is not the first or the last of the correction. */
    private static String insertInterior(String typed, String loweredTyped, String correction,
            String loweredCorrection, Locale locale) {
        if (loweredCorrection.length() != loweredTyped.length() + 1) {
            return null;
        }
        boolean typedIsLower = typed.equals(loweredTyped);
        for (int i = 1; i < loweredCorrection.length() - 1; i++) {
            if (!loweredCorrection.substring(0, i).concat(loweredCorrection.substring(i + 1)).equals(loweredTyped)) {
                continue;
            }
            char inserted = correction.charAt(i);
            if (typedIsLower) {
                inserted = Character.toLowerCase(inserted);
            } else if (typed.equals(typed.toUpperCase(locale))) {
                inserted = Character.toUpperCase(inserted);
            }
            return typed.substring(0, i) + inserted + typed.substring(i);
        }
        return null;
    }

    private static String swapAdjacent(String typed, String loweredTyped, String loweredCorrection) {
        if (loweredTyped.length() != loweredCorrection.length() || loweredTyped.length() < 2) {
            return null;
        }
        int first = -1;
        for (int i = 0; i < loweredTyped.length(); i++) {
            if (loweredTyped.charAt(i) == loweredCorrection.charAt(i)) {
                continue;
            }
            if (first < 0) {
                first = i;
                continue;
            }
            if (i != first + 1
                    || loweredTyped.charAt(first) != loweredCorrection.charAt(i)
                    || loweredTyped.charAt(i) != loweredCorrection.charAt(first)) {
                return null;
            }
            for (int k = i + 1; k < loweredTyped.length(); k++) {
                if (loweredTyped.charAt(k) != loweredCorrection.charAt(k)) {
                    return null;
                }
            }
            char[] chars = typed.toCharArray();
            char tmp = chars[first];
            chars[first] = chars[i];
            chars[i] = tmp;
            return new String(chars);
        }
        return null;
    }

    /**
     * More than half the letters are capitals, and at least two of them. LatinIME treats that as
     * careful input ({@code WordComposer.isMostlyCaps}) and does not auto-correct it.
     */
    public static boolean isMostlyCaps(String typed) {
        if (typed == null || typed.length() < 2) {
            return false;
        }
        int letters = 0;
        int caps = 0;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            letters++;
            if (Character.isUpperCase(c)) {
                caps++;
            }
        }
        return caps > 1 && letters > 0 && caps * 2 > letters;
    }

    /**
     * LatinIME's safety net: a long jump is not a correction. Words shorter than 4 letters are
     * left to the one-slip rule, because a short word's distance is large relative to its length.
     */
    public static boolean blockedBySafetyNet(String typed, String correction, Locale locale) {
        if (typed == null || correction == null || typed.length() < MINIMUM_SAFETY_NET_CHAR_LENGTH) {
            return false;
        }
        int limit = (typed.length() < 5 ? 2 : typed.length() / 2) + 1;
        return damerauDistance(typed, correction, locale) > limit;
    }

    /** Case-insensitive Damerau-Levenshtein distance, including one adjacent transposition. */
    static int damerauDistance(String typed, String correction, Locale locale) {
        Locale cmp = locale != null ? locale : Locale.ROOT;
        String s = typed.toLowerCase(cmp);
        String t = correction.toLowerCase(cmp);
        int n = s.length();
        int m = t.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1, Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
                if (i > 1 && j > 1
                        && s.charAt(i - 1) == t.charAt(j - 2)
                        && s.charAt(i - 2) == t.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + cost);
                }
            }
        }
        return dp[n][m];
    }

    private static boolean containsSpace(String correction) {
        for (int i = 0; i < correction.length(); i++) {
            if (Character.isWhitespace(correction.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
