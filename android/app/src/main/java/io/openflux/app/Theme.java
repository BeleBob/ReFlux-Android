package io.openflux.app;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

// The app's look: the phone's theme, light or dark, and a pure black
// background (for AMOLED screens) whenever the look is dark.
final class Theme {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String[] MODES = {SYSTEM, LIGHT, DARK};
    private static final String KEY_MODE = "theme";
    private static final String KEY_BLACK = "black_background";
    private static final String KEY_NIGHT_APPLIED = "night_mode_applied";
    // Before 1.1.0: a switch; absent meant the phone's theme.
    private static final String KEY_LEGACY_DARK = "dark_mode";

    private Theme() {
    }

    static String mode(Context context) {
        SharedPreferences prefs = prefs(context);
        String mode = prefs.getString(KEY_MODE, null);
        if (mode == null && prefs.contains(KEY_LEGACY_DARK)) {
            mode = prefs.getBoolean(KEY_LEGACY_DARK, false) ? DARK : LIGHT;
        }
        for (String m : MODES) if (m.equals(mode)) return m;
        return SYSTEM;
    }

    static boolean black(Context context) {
        return prefs(context).getBoolean(KEY_BLACK, false);
    }

    static void save(Context context, String mode, boolean black) {
        prefs(context).edit().putString(KEY_MODE, mode).putBoolean(KEY_BLACK, black)
                .remove(KEY_LEGACY_DARK).apply();
    }

    // Tells Android the theme chosen here, so the window it draws before the
    // app's first frame (the splash screen) is dark when the app is, even on
    // a light phone (values-night). Android 12+; it remembers it across
    // starts, so it is set only when it changes.
    static void applyNightMode(Context context) {
        if (Build.VERSION.SDK_INT < 31) return;
        String mode = mode(context);
        int want = DARK.equals(mode) ? UiModeManager.MODE_NIGHT_YES
                : LIGHT.equals(mode) ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_AUTO;
        if (prefs(context).getInt(KEY_NIGHT_APPLIED, -1) == want) return;
        UiModeManager ui = context.getSystemService(UiModeManager.class);
        if (ui == null) return;
        ui.setApplicationNightMode(want);
        prefs(context).edit().putInt(KEY_NIGHT_APPLIED, want).apply();
    }

    static boolean dark(Context context) {
        String mode = mode(context);
        if (DARK.equals(mode)) return true;
        if (LIGHT.equals(mode)) return false;
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static Colors colors(Context context) {
        boolean dark = dark(context);
        return new Colors(dark, dark && black(context));
    }

    static String label(Context context, String mode) {
        switch (mode) {
            case LIGHT: return context.getString(R.string.theme_light);
            case DARK: return context.getString(R.string.theme_dark);
            default: return context.getString(R.string.theme_system);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
    }

    // One look's colors. Dark is the deep navy of upstream's app; black
    // keeps its text and accent but puts the pages on #000 and the cards a
    // step above it, so that the screen's pixels stay off.
    static final class Colors {
        final boolean dark, black;
        final int background, surface, text, secondary, border, hint, accent, selectedSurface, log;
        // Small tinted panels (the version badge, the LAN address).
        final int infoSurface;

        Colors(boolean dark, boolean black) {
            this.dark = dark;
            this.black = black;
            accent = Color.rgb(79, 124, 255);
            if (black) {
                background = Color.BLACK;
                surface = Color.rgb(14, 14, 17);
                text = Color.WHITE;
                secondary = Color.rgb(142, 146, 156);
                border = Color.rgb(36, 38, 44);
                hint = Color.rgb(92, 95, 104);
                selectedSurface = Color.rgb(18, 28, 52);
                log = Color.rgb(218, 220, 224);
                infoSurface = Color.rgb(20, 28, 44);
            } else if (dark) {
                background = Color.rgb(8, 11, 18);
                surface = Color.rgb(19, 23, 34);
                text = Color.WHITE;
                secondary = Color.rgb(138, 146, 166);
                border = Color.rgb(42, 52, 70);
                hint = Color.rgb(90, 98, 114);
                selectedSurface = Color.rgb(28, 42, 70);
                log = Color.rgb(218, 220, 224);
                infoSurface = Color.rgb(38, 50, 68);
            } else {
                background = Color.rgb(248, 249, 250);
                surface = Color.WHITE;
                text = Color.rgb(32, 33, 36);
                secondary = Color.rgb(95, 99, 104);
                border = Color.rgb(218, 220, 224);
                hint = Color.rgb(128, 134, 139);
                selectedSurface = Color.rgb(232, 240, 254);
                log = Color.rgb(60, 64, 67);
                infoSurface = Color.rgb(232, 240, 254);
            }
        }
    }
}
