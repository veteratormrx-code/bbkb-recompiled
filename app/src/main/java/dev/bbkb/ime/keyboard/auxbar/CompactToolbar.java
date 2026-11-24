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
    public static final String PREF_PINNED = "pref_toolbar_pinned";

    public static final int MORE = -1001;

    private static final String[] DEFAULT_IDS = {
            UimMenuOrder.CLIPBOARD,
            UimMenuOrder.EMOJI,
            UimMenuOrder.TEXT_EDIT,
            UimMenuOrder.TRANSLATE,
            UimMenuOrder.VOICE,
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
        return read(context, PREF_PINNED, false);
    }

    public static void setExpanded(Context context, boolean expanded) {
        PrefsManager.INSTANCE.getPrefs(context).edit().putBoolean(PREF_EXPANDED, expanded).apply();
    }

    public static void fill(LinearLayout row, Context context, Host host) {
        row.removeAllViews();
        int size = dp(context, 48);
        int icon = dp(context, 24);
        for (String id : DEFAULT_IDS) {
            int code = UimMenuOrder.keyCodeFor(id);
            if (code == 0) {
                continue;
            }
            row.addView(button(context, iconFor(id), size, icon, () -> host.onToolbarTool(code)));
        }
        row.addView(button(context, R.drawable.ic_suggestions_more, size, icon, () -> host.onToolbarTool(MORE)));
    }

    private static ImageButton button(Context context, int drawable, int size, int icon, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawable);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
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
