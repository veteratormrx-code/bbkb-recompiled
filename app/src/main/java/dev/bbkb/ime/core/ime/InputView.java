package dev.bbkb.ime.core.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.RelativeLayout;

import dev.bbkb.ime.R;

import androidx.core.view.WindowCompat;

import dev.bbkb.ime.keyboard.KeyboardColorManager;
import dev.bbkb.ime.core.settings.PrefsManager;

/**
 * The IME's root input view (input_view.xml). Paints the nav-bar padding strip
 * ({@link ImeInsets#installNavBarInsetListener}) with the keyboard background while content is
 * shown, or before Android 15 sets the window's nav-bar color, and marks light backgrounds so the
 * system's IME buttons draw dark.
 */
public final class InputView extends RelativeLayout {
    public static final String ONE_HAND_PREF = "pref_one_hand";
    private static final float ONE_HAND_SCALE = 0.80f;
    private Button fullWidthButton;

    private final Paint navBarFill = new Paint();
    private boolean navBarFillShown;
    private Window imeWindow;

    private final kotlin.jvm.functions.Function0<kotlin.Unit> colorObserver = () -> {
        applyNavBarColors();
        return kotlin.Unit.INSTANCE;
    };

    public InputView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet, 0);
        navBarFill.setColor(KeyboardColorManager.INSTANCE.getBackgroundColor());
    }

    /** Before Android 15 the system paints under the nav bar from the window's nav-bar color, so it follows the keyboard. */
    public void setImeWindow(Window window) {
        imeWindow = window;
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        applyNavBarColors();
        initFullWidthButton();
    }

    private void initFullWidthButton() {
        if (fullWidthButton != null) return;
        fullWidthButton = new Button(getContext());
        fullWidthButton.setText("↔");
        fullWidthButton.setContentDescription(getResources().getString(R.string.one_hand_full));
        fullWidthButton.setMinWidth(0);
        fullWidthButton.setMinimumWidth(0);
        fullWidthButton.setPadding(0, 0, 0, 0);
        fullWidthButton.setVisibility(GONE);
        fullWidthButton.setOnClickListener(view -> {
            PrefsManager.INSTANCE.getPrefs(getContext()).edit().putString(ONE_HAND_PREF, "off").apply();
            refreshOneHandMode();
        });
        int size = Math.round(44 * getResources().getDisplayMetrics().density);
        LayoutParams params = new LayoutParams(size, size);
        params.addRule(ALIGN_PARENT_BOTTOM);
        params.addRule(ALIGN_PARENT_RIGHT);
        addView(fullWidthButton, params);
    }

    public void refreshOneHandMode() {
        View frame = findViewById(R.id.main_keyboard_frame);
        if (frame == null || fullWidthButton == null) return;
        String side = PrefsManager.INSTANCE.getPrefs(getContext()).getString(ONE_HAND_PREF, "off");
        View keyboard = findViewById(R.id.keyboard_view);
        boolean active = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                && keyboard != null && keyboard.getVisibility() == VISIBLE
                && ("left".equals(side) || "right".equals(side));
        boolean left = "left".equals(side);
        frame.setPivotX(left ? 0 : frame.getWidth());
        frame.setScaleX(active ? ONE_HAND_SCALE : 1f);
        int visibility = active ? VISIBLE : GONE;
        if (fullWidthButton.getVisibility() != visibility) fullWidthButton.setVisibility(visibility);
        LayoutParams params = (LayoutParams) fullWidthButton.getLayoutParams();
        int edge = left ? ALIGN_PARENT_RIGHT : ALIGN_PARENT_LEFT;
        if (params.getRule(edge) == 0) {
            params.removeRule(ALIGN_PARENT_RIGHT);
            params.removeRule(ALIGN_PARENT_LEFT);
            params.addRule(edge);
            fullWidthButton.setLayoutParams(params);
        }
        fullWidthButton.setTextColor(KeyboardColorManager.INSTANCE.getTextColor());
        fullWidthButton.setBackgroundColor(KeyboardColorManager.INSTANCE.getKeyColor());
    }

    View getFullWidthButton() {
        return fullWidthButton;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        refreshOneHandMode();
    }

    /** From {@link ImeInsets#compute}: fill the strip only while content is shown. */
    void setNavBarFillShown(boolean shown) {
        if (navBarFillShown != shown) {
            navBarFillShown = shown;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyboardColorManager.INSTANCE.addObserver(colorObserver);
        applyNavBarColors();
    }

    @Override
    protected void onDetachedFromWindow() {
        KeyboardColorManager.INSTANCE.removeObserver(colorObserver);
        super.onDetachedFromWindow();
    }

    private void applyNavBarColors() {
        int background = KeyboardColorManager.INSTANCE.getBackgroundColor();
        navBarFill.setColor(background);
        invalidate();
        // No window yet: setImeWindow() comes straight after the view is installed and applies this.
        if (imeWindow == null) return;
        imeWindow.setNavigationBarColor(background);
        WindowCompat.getInsetsController(imeWindow, this)
                .setAppearanceLightNavigationBars(Color.luminance(background) > 0.5f);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // The gesture inset and any gap under the keys are not a toolbar. Paint them with the
        // keyboard color so the app's field cannot show through, and do not put controls there.
        if (!navBarFillShown) return;
        int top = getHeight() - getPaddingBottom();
        View keys = findViewById(R.id.keyboard_view);
        View frame = findViewById(R.id.keyboard_frame);
        if (keys != null && keys.isShown() && frame != null) {
            top = Math.min(top, frame.getTop() + keys.getBottom());
        }
        if (top < getHeight()) {
            canvas.drawRect(0, top, getWidth(), getHeight(), navBarFill);
        }
    }
}
