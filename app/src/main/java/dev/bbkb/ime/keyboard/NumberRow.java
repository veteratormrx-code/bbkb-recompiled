package dev.bbkb.ime.keyboard;

import android.content.Context;

import dev.bbkb.ime.core.settings.PrefsManager;

/** The optional digit row above letter layouts. On for a new install until the user turns it off. */
public final class NumberRow {
    public static final String PREF = "pref_number_row";

    private NumberRow() {
    }

    public static boolean isEnabled(Context context) {
        try {
            return PrefsManager.INSTANCE.getPrefs(context).getBoolean(PREF, true);
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
