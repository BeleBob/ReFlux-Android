package io.openflux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

// Palette and form controls shared by the full-screen flows opened from MainActivity.
final class FlowStyle {
    final int background, surface, text, secondary, border, accent, hint, selectedSurface;
    private final Activity activity;
    private final boolean dark;

    FlowStyle(Activity activity) {
        this.activity = activity;
        Theme.Colors c = Theme.colors(activity);
        dark = c.dark;
        background = c.background;
        surface = c.surface;
        text = c.text;
        secondary = c.secondary;
        border = c.border;
        hint = c.hint;
        accent = c.accent;
        selectedSurface = c.selectedSurface;
    }

    void applyWindow() {
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 29) activity.getWindow().setNavigationBarContrastEnforced(false);
        activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(activity, dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) shape.setStroke(dp(1), stroke);
        return shape;
    }

    void heading(TextView view) {
        view.setTextColor(text);
        view.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void body(TextView view) { view.setTextColor(secondary); }

    void primary(Button button) {
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(59, 93, 191)),
                rounded(accent, Color.TRANSPARENT, 9), null));
    }

    void secondary(Button button) {
        button.setTextColor(accent);
        button.setTextSize(14);
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(45, 79, 124, 255)),
                rounded(surface, border, 9), null));
    }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
