package dev.bbkb.ime.keyboard;

import android.content.Context;

import dev.bbkb.ime.core.settings.PrefsManager;

/** The optional digit row above letter layouts. On for a new install until the user turns it off. */
public final class NumberRow {
    public static final String PREF = "pref_number_row";
    public static final String HEIGHT_PREF = "pref_number_row_height";
    public static final String HEIGHT_COMPACT = "compact";
    public static final String HEIGHT_REGULAR = "regular";
    public static final String HEIGHT_TALL = "tall";

    private NumberRow() {
    }

    public static boolean isEnabled(Context context) {
        try {
            return PrefsManager.INSTANCE.getPrefs(context).getBoolean(PREF, true);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    /** Multiplier for the digit row only. Letter rows keep the keyboard's own row height. */
    public static float heightScale(Context context) {
        String value = HEIGHT_REGULAR;
        try {
            String stored = PrefsManager.INSTANCE.getPrefs(context).getString(HEIGHT_PREF, HEIGHT_REGULAR);
            if (stored != null) {
                value = stored;
            }
        } catch (RuntimeException ignored) {
            value = HEIGHT_REGULAR;
        }
        if (HEIGHT_COMPACT.equals(value)) {
            return 0.72f;
        }
        if (HEIGHT_TALL.equals(value)) {
            return 1.28f;
        }
        return 1f;
    }
}
