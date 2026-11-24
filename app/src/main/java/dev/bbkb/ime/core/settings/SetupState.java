package dev.bbkb.ime.core.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/** The first-run checklist opens once, only while the settings store is still empty. */
public final class SetupState {

    static final String KEY_COMPLETED = "pref_setup_completed";

    private SetupState() {
    }

    public static boolean shouldShowSetup(Context context) {
        final SharedPreferences prefs = PrefsManager.INSTANCE.getPrefs(context);
        if (prefs.getBoolean(KEY_COMPLETED, false)) {
            return false;
        }
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!KEY_COMPLETED.equals(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    public static void markCompleted(Context context) {
        PrefsManager.INSTANCE.getPrefs(context).edit().putBoolean(KEY_COMPLETED, true).apply();
    }
}
