package dev.bbkb.ime.keyboard;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import dev.bbkb.ime.R;

import java.util.List;

/**
 * One accessibility node per drawn key, with the same bounds the touch path hits. The input menu
 * is drawn, so without these nodes a test or TalkBack cannot tell its tools apart.
 */
public final class KeyboardKeyAccessibility extends ExploreByTouchHelper {

    private final KeyboardView keyboardView;

    KeyboardKeyAccessibility(KeyboardView keyboardView) {
        super(keyboardView);
        this.keyboardView = keyboardView;
    }

    void onKeyboardChanged() {
        invalidateRoot();
    }

    /** Spoken name of a key: its label, or the tool name for an input-menu code. */
    public static String describe(Context context, int code, String label) {
        if (label != null && label.trim().length() > 0) {
            return label;
        }
        switch (code) {
            case -11:
                return context.getString(R.string.settings_uim_toggle_emoji);
            case -25:
                return context.getString(R.string.settings_uim_toggle_clipboard);
            case -27:
                return context.getString(R.string.settings_uim_toggle_voice);
            case -42:
                return context.getString(R.string.settings_uim_toggle_fcc);
            case -46:
                return context.getString(R.string.settings_uim_toggle_numpad);
            case -47:
                return context.getString(R.string.settings_uim_toggle_text_edit);
            case -48:
                return context.getString(R.string.settings_uim_toggle_translate);
            case -49:
                return context.getString(R.string.settings_uim_toggle_handwriting);
            case -3:
                return context.getString(R.string.accessibility_keyboard_letters);
            case 32:
                return context.getString(R.string.accessibility_key_space);
            default:
                if (code >= 32 && code <= 0x10FFFF && Character.isValidCodePoint(code)) {
                    return new String(Character.toChars(code));
                }
                return context.getString(R.string.accessibility_key_unnamed);
        }
    }

    @Override
    protected int getVirtualViewAt(float x, float y) {
        Keyboard keyboard = keyboardView.getKeyboard();
        if (keyboard == null) {
            return INVALID_ID;
        }
        List<Key> keys = keyboard.getKeys();
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            if (x >= key.getX() && x < key.getX() + key.getWidth()
                    && y >= key.getY() && y < key.getY() + key.getHeight()) {
                return i;
            }
        }
        return INVALID_ID;
    }

    @Override
    protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
        Keyboard keyboard = keyboardView.getKeyboard();
        if (keyboard == null) {
            return;
        }
        int count = keyboard.getKeys().size();
        for (int i = 0; i < count; i++) {
            virtualViewIds.add(i);
        }
    }

    @Override
    protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat node) {
        Keyboard keyboard = keyboardView.getKeyboard();
        List<Key> keys = keyboard == null ? null : keyboard.getKeys();
        if (keys == null || virtualViewId < 0 || virtualViewId >= keys.size()) {
            node.setContentDescription(keyboardView.getContext().getString(R.string.accessibility_key_unnamed));
            node.setBoundsInParent(new Rect(0, 0, 1, 1));
            return;
        }
        Key key = keys.get(virtualViewId);
        node.setContentDescription(describe(keyboardView.getContext(), key.getCode(), key.getLabel()));
        node.setBoundsInParent(new Rect(
                key.getX(), key.getY(), key.getX() + key.getWidth(), key.getY() + key.getHeight()));
        node.setFocusable(true);
    }

    @Override
    protected boolean onPerformActionForVirtualView(int virtualViewId, int action, Bundle arguments) {
        return false;
    }
}
