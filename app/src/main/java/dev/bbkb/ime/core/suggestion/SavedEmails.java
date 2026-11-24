package dev.bbkb.ime.core.suggestion;

import android.content.Context;
import android.content.SharedPreferences;

import dev.bbkb.ime.core.engine.Dictionary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Addresses the user typed into settings. Stored in their own preferences file, which the backup
 * allowlist does not include. Nothing here is learned, logged, or filled from the keyboard.
 */
public final class SavedEmails {

    public static final String PREF_ENABLED = "pref_saved_emails_enabled";

    private static final String FILE = "bbkb_saved_emails";
    private static final String KEY_ADDRESSES = "addresses";
    private static final int MAX_MATCHES = 3;
    private static final int MIN_PREFIX = 2;

    private SavedEmails() {
    }

    public static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(PREF_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    public static List<String> addresses(Context context) {
        Set<String> stored = preferences(context).getStringSet(KEY_ADDRESSES, Collections.emptySet());
        ArrayList<String> copy = new ArrayList<>(stored);
        Collections.sort(copy, String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    public static boolean add(Context context, String address) {
        String kept = canonical(address);
        if (kept == null) {
            return false;
        }
        Set<String> next = new LinkedHashSet<>(addresses(context));
        next.add(kept);
        preferences(context).edit().putStringSet(KEY_ADDRESSES, next).apply();
        return true;
    }

    public static void remove(Context context, String address) {
        Set<String> next = new LinkedHashSet<>(addresses(context));
        next.remove(address);
        preferences(context).edit().putStringSet(KEY_ADDRESSES, next).apply();
    }

    public static boolean contains(Context context, String word) {
        if (word == null) {
            return false;
        }
        for (String address : addresses(context)) {
            if (address.equals(word)) {
                return true;
            }
        }
        return false;
    }

    /** Up to three saved addresses whose prefix matches {@code typed}, in the saved spelling. */
    public static List<String> match(Collection<String> addresses, String typed) {
        if (typed == null || typed.length() < MIN_PREFIX || addresses == null) {
            return Collections.emptyList();
        }
        String needle = typed.toLowerCase(Locale.ROOT);
        ArrayList<String> matches = new ArrayList<>();
        for (String address : addresses) {
            if (address != null && address.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(address);
                if (matches.size() == MAX_MATCHES) {
                    break;
                }
            }
        }
        return matches;
    }

    public static SuggestedWords suggestions(Context context, String typed) {
        List<String> matches = match(addresses(context), typed);
        if (matches.isEmpty()) {
            return SuggestedWords.EMPTY;
        }
        ArrayList<SuggestedWords.SuggestedWordInfo> list = new ArrayList<>(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            list.add(new SuggestedWords.SuggestedWordInfo(
                    matches.get(i), 0, 0, Dictionary.DICTIONARY_USER_TYPED, -1, i, null));
        }
        return new SuggestedWords(list, false, false, 0);
    }

    /** A single address with {@code @} and a dot in the domain, or null. */
    public static String canonical(String address) {
        if (address == null) {
            return null;
        }
        String trimmed = address.trim();
        int at = trimmed.indexOf('@');
        int dot = trimmed.lastIndexOf('.');
        if (at <= 0 || dot <= at + 1 || dot >= trimmed.length() - 1 || trimmed.indexOf('@', at + 1) >= 0
                || trimmed.indexOf(' ') >= 0) {
            return null;
        }
        return trimmed;
    }
}
