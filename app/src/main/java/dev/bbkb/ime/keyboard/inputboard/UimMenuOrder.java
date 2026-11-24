package dev.bbkb.ime.keyboard.inputboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Order of the user-orderable input menu toggles. The bar keeps the keyboard key in the centre
 * and shows as many tools as fit at 48dp. The rest stay in the customize screen.
 * Stored in {@link #PREF_KEY} as comma-separated ids; {@link #getOrder} drops unknown ids and
 * duplicates and appends missing ones, so a new toggle surfaces at the end.
 */
public final class UimMenuOrder {

    public static final String PREF_KEY = "pref_uim_menu_order";
    public static final String PREF_SHOWN = "pref_uim_shown_count";

    /** Smallest touch target for a menu key. A narrower window shows fewer tools than the user asked for. */
    public static final int MIN_KEY_DP = 48;

    /** Tools on the bar for a new install. The user can raise this up to {@link #MAX_SHOWN}. */
    public static final int MIN_SHOWN = 4;
    public static final int MAX_SHOWN = 7;

    /** Same as {@link #MIN_SHOWN}. Older call sites still use this name. */
    public static final int SHOWN_COUNT = MIN_SHOWN;

    public static final String VOICE = "voice";
    public static final String EMOJI = "emoji";
    public static final String FCC = "fcc";
    public static final String CLIPBOARD = "clipboard";
    public static final String NUMBER_PAD = "numpad";
    public static final String TEXT_EDIT = "text_edit";
    public static final String TRANSLATE = "translate";
    public static final String HANDWRITING = "handwriting";

    private static final List<String> DEFAULT_ORDER = Collections.unmodifiableList(
            Arrays.asList(EMOJI, CLIPBOARD, TEXT_EDIT, TRANSLATE, VOICE, HANDWRITING, FCC, NUMBER_PAD));

    private UimMenuOrder() {
        // Static utility.
    }

    public static List<String> defaultOrder() {
        return DEFAULT_ORDER;
    }

    /** The UIM board keycode for a toggle id (the componentMap key), or 0 if unknown. */
    public static int keyCodeFor(String id) {
        switch (id) {
            case VOICE: return -27;
            case EMOJI: return -11;
            case FCC: return -42;
            case CLIPBOARD: return -25;
            case NUMBER_PAD: return -46;
            case TEXT_EDIT: return -47;
            case TRANSLATE: return -48;
            case HANDWRITING: return -49;
            default: return 0;
        }
    }

    /** The full sanitized order (all toggles, shown ones first). */
    public static List<String> getOrder(SharedPreferences prefs) {
        String stored = prefs.getString(PREF_KEY, null);
        List<String> order = new ArrayList<>(DEFAULT_ORDER.size());
        if (stored != null) {
            for (String id : stored.split(",")) {
                if (DEFAULT_ORDER.contains(id) && !order.contains(id)) {
                    order.add(id);
                }
            }
        }
        for (String id : DEFAULT_ORDER) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

    public static void saveOrder(SharedPreferences prefs, List<String> order) {
        prefs.edit().putString(PREF_KEY, String.join(",", order)).apply();
    }

    /** How many tools the user asked to show, clamped to 4–7. */
    public static int chosenCount(SharedPreferences prefs) {
        int value = MIN_SHOWN;
        try {
            value = prefs.getInt(PREF_SHOWN, MIN_SHOWN);
        } catch (RuntimeException ignored) {
            value = MIN_SHOWN;
        }
        return Math.max(MIN_SHOWN, Math.min(MAX_SHOWN, value));
    }

    public static void saveChosenCount(SharedPreferences prefs, int count) {
        int clamped = Math.max(MIN_SHOWN, Math.min(MAX_SHOWN, count));
        prefs.edit().putInt(PREF_SHOWN, clamped).apply();
    }

    /** How many tool keys fit beside the centre key without going under {@link #MIN_KEY_DP}. */
    public static int fitCount(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        float density = context.getResources().getDisplayMetrics().density;
        int minPx = Math.max(1, Math.round(MIN_KEY_DP * density));
        int slots = Math.max(1, width / minPx);
        int toggles = Math.max(1, slots - 1);
        return Math.min(toggles, MAX_SHOWN);
    }

    /** Tools actually drawn: the user's choice, but never more than fit on this width. */
    public static int shownCount(Context context) {
        SharedPreferences prefs = null;
        try {
            prefs = dev.bbkb.ime.core.settings.PrefsManager.INSTANCE.getPrefs(context);
        } catch (RuntimeException ignored) {
            prefs = null;
        }
        int chosen = prefs == null ? MIN_SHOWN : chosenCount(prefs);
        return Math.min(chosen, fitCount(context));
    }

    /** Keycodes of the tools on the bar, in order. The centre keyboard key is not included. */
    public static List<Integer> getShownKeyCodes(Context context) {
        SharedPreferences prefs;
        try {
            prefs = dev.bbkb.ime.core.settings.PrefsManager.INSTANCE.getPrefs(context);
        } catch (RuntimeException ignored) {
            prefs = null;
        }
        List<String> order = prefs == null ? defaultOrder() : getOrder(prefs);
        int count = shownCount(context);
        List<Integer> codes = new ArrayList<>(count);
        for (int i = 0; i < count && i < order.size(); i++) {
            codes.add(keyCodeFor(order.get(i)));
        }
        return codes;
    }
}
