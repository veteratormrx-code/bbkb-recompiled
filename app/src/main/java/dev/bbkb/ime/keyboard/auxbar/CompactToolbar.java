package dev.bbkb.ime.keyboard.auxbar;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import dev.bbkb.ime.R;
import dev.bbkb.ime.core.settings.PrefsManager;
import dev.bbkb.ime.keyboard.KeyboardColorManager;
import dev.bbkb.ime.keyboard.inputboard.UimMenuOrder;

/**
 * One compact tool row in the suggestion strip: pinned tools, then More.
 * The strip height does not change. Flick words over the letters stay the classic BlackBerry path.
 */
public final class CompactToolbar {

    public static final String PREF_EXPANDED = "pref_toolbar_expanded";
    public static final String PREF_MODE = "pref_toolbar_mode";
    public static final String PREF_SUGGESTIONS = "pref_suggestion_place";
    public static final String PREF_ORDER = "pref_toolbar_order";
    public static final String MODE_COLLAPSED = "collapsed";
    public static final String MODE_PINNED = "pinned";
    public static final String MODE_HIDDEN = "hidden";
    public static final String SUGGEST_CLASSIC = "classic";
    public static final String SUGGEST_STRIP = "strip";
    public static final String SUGGEST_BOTH = "both";
    public static final String SUGGEST_NONE = "none";

    public static final int MORE = -1001;

    private static final String[] DEFAULT_IDS = {
            UimMenuOrder.CLIPBOARD,
            UimMenuOrder.EMOJI,
            UimMenuOrder.TEXT_EDIT,
            UimMenuOrder.TRANSLATE,
            UimMenuOrder.VOICE,
            UimMenuOrder.HANDWRITING,
            UimMenuOrder.NUMBER_PAD,
    };

    public interface Host {
        void onToolbarTool(int keyCode);
    }

    private CompactToolbar() {
    }

    public static boolean isExpanded(Context context) {
        return read(context, PREF_EXPANDED, false);
    }

    public static boolean isPinned(Context context) {
        return MODE_PINNED.equals(mode(context));
    }

    public static boolean isHidden(Context context) {
        return MODE_HIDDEN.equals(mode(context));
    }

    public static String mode(Context context) {
        try {
            String value = PrefsManager.INSTANCE.getPrefs(context).getString(PREF_MODE, MODE_COLLAPSED);
            return value != null ? value : MODE_COLLAPSED;
        } catch (RuntimeException ignored) {
            return MODE_COLLAPSED;
        }
    }

    public static String suggestionPlace(Context context) {
        try {
            String value = PrefsManager.INSTANCE.getPrefs(context).getString(PREF_SUGGESTIONS, SUGGEST_CLASSIC);
            return value != null ? value : SUGGEST_CLASSIC;
        } catch (RuntimeException ignored) {
            return SUGGEST_CLASSIC;
        }
    }

    /** Flick words over the keys. Classic and combined keep them. The top strip and "none" do not. */
    public static boolean flickWords(Context context) {
        String place = suggestionPlace(context);
        return SUGGEST_CLASSIC.equals(place) || SUGGEST_BOTH.equals(place);
    }

    /** Words in the top strip while the tool row is collapsed. Classic does not duplicate them. */
    public static boolean stripWords(Context context) {
        String place = suggestionPlace(context);
        return SUGGEST_STRIP.equals(place) || SUGGEST_BOTH.equals(place);
    }

    public static void setExpanded(Context context, boolean expanded) {
        PrefsManager.INSTANCE.getPrefs(context).edit().putBoolean(PREF_EXPANDED, expanded).apply();
    }

    public static String[] orderedIds(Context context) {
        String stored = null;
        try {
            stored = PrefsManager.INSTANCE.getPrefs(context).getString(PREF_ORDER, null);
        } catch (RuntimeException ignored) {
            stored = null;
        }
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        if (stored != null) {
            for (String id : stored.split(",")) {
                if (UimMenuOrder.keyCodeFor(id) != 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        for (String id : DEFAULT_IDS) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids.toArray(new String[0]);
    }

    public static void fill(LinearLayout row, Context context, Host host) {
        row.removeAllViews();
        int size = dp(context, 48);
        int icon = dp(context, 24);
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int fit = Math.max(1, width / size - 2);
        String[] ids = orderedIds(context);
        int shown = Math.min(fit, ids.length);
        for (int i = 0; i < shown; i++) {
            int code = UimMenuOrder.keyCodeFor(ids[i]);
            row.addView(button(context, iconFor(ids[i]), size, icon, () -> host.onToolbarTool(code), label(context, ids[i])));
        }
        row.addView(button(context, R.drawable.ic_suggestions_more, size, icon, () -> host.onToolbarTool(MORE), "More"));
    }

    public static String[] overflowIds(Context context) {
        String[] ids = orderedIds(context);
        int size = dp(context, 48);
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int fit = Math.max(1, width / size - 2);
        int start = Math.min(fit, ids.length);
        String[] rest = new String[ids.length - start];
        System.arraycopy(ids, start, rest, 0, rest.length);
        return rest;
    }

    public static String label(Context context, String id) {
        switch (id) {
            case UimMenuOrder.EMOJI:
                return context.getString(R.string.settings_uim_toggle_emoji);
            case UimMenuOrder.VOICE:
                return context.getString(R.string.settings_uim_toggle_voice);
            case UimMenuOrder.TEXT_EDIT:
                return context.getString(R.string.settings_uim_toggle_text_edit);
            case UimMenuOrder.TRANSLATE:
                return context.getString(R.string.settings_uim_toggle_translate);
            case UimMenuOrder.HANDWRITING:
                return context.getString(R.string.settings_uim_toggle_handwriting);
            case UimMenuOrder.NUMBER_PAD:
                return context.getString(R.string.settings_uim_toggle_numpad);
            case UimMenuOrder.CLIPBOARD:
            default:
                return context.getString(R.string.settings_uim_toggle_clipboard);
        }
    }

    private static ImageButton button(Context context, int drawable, int size, int icon, Runnable action, String label) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawable);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setContentDescription(label);
        int pad = dp(context, 12);
        button.setPadding(pad, pad, pad, pad);
        KeyboardColorManager.INSTANCE.tint(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        button.setLayoutParams(params);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private static int iconFor(String id) {
        switch (id) {
            case UimMenuOrder.EMOJI:
                return R.drawable.ic_auxbar_emoji_enabled;
            case UimMenuOrder.VOICE:
                return R.drawable.ic_auxbar_voice_enabled;
            case UimMenuOrder.TEXT_EDIT:
                return R.drawable.ic_settings_m_edit;
            case UimMenuOrder.HANDWRITING:
                return R.drawable.ic_auxbar_voice_enabled;
            case UimMenuOrder.NUMBER_PAD:
                return R.drawable.ic_auxbar_numpad;
            case UimMenuOrder.TRANSLATE:
                return R.drawable.ic_settings_m_palette;
            case UimMenuOrder.CLIPBOARD:
            default:
                return R.drawable.ic_auxbar_clipboard_enabled;
        }
    }

    private static boolean read(Context context, String key, boolean fallback) {
        try {
            return PrefsManager.INSTANCE.getPrefs(context).getBoolean(key, fallback);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
