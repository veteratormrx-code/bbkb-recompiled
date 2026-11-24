package dev.bbkb.ime.core.device.config;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;

/**
 * Panel size as {@code short x long}, for example {@code 720x720}. Hardware modes survive a
 * {@code wm size} override. The largest mode is the panel; scaled metrics are the fallback.
 */
public final class PhysicalDisplay {

    private static volatile Context appContext;
    private static volatile String cached;

    private PhysicalDisplay() {
    }

    public static void attach(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
    }

    static void clearForTest() {
        cached = null;
        appContext = null;
    }

    /** {@code short x long}, or null when nothing has been attached or the size cannot be read. */
    public static String resolution() {
        final String known = cached;
        if (known != null) {
            return known;
        }
        final Context context = appContext;
        if (context == null) {
            return null;
        }
        String value = fromPhysicalModes(context);
        if (value == null) {
            value = fromScaledMetrics(context);
        }
        cached = value;
        return value;
    }

    static String format(int a, int b) {
        return Math.min(a, b) + "x" + Math.max(a, b);
    }

    private static String fromPhysicalModes(Context context) {
        final DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (manager == null) {
            return null;
        }
        final Display display = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return null;
        }
        final Display.Mode[] modes = display.getSupportedModes();
        if (modes == null || modes.length == 0) {
            return null;
        }
        int bestWidth = 0;
        int bestHeight = 0;
        long bestArea = -1L;
        for (Display.Mode mode : modes) {
            final int width = mode.getPhysicalWidth();
            final int height = mode.getPhysicalHeight();
            final long area = (long) width * height;
            if (width > 0 && height > 0 && area > bestArea) {
                bestArea = area;
                bestWidth = width;
                bestHeight = height;
            }
        }
        if (bestArea <= 0L) {
            return null;
        }
        return format(bestWidth, bestHeight);
    }

    private static String fromScaledMetrics(Context context) {
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            return null;
        }
        return format(metrics.widthPixels, metrics.heightPixels);
    }
}
