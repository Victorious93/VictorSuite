package com.termux.victor.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;

/** The two status-bar helpers the app uses. */
public final class StatusBarCompat {

    private StatusBarCompat() {}

    /** Solid status bar of {@code color}; content is laid out below it (not behind). */
    @SuppressWarnings("deprecation")
    public static void setStatusBarColor(Activity activity, int color) {
        Window window = activity.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
        window.getDecorView().setSystemUiVisibility(0);
        ViewGroup content = window.findViewById(android.R.id.content);
        View child = content == null ? null : content.getChildAt(0);
        if (child != null) {
            ViewCompat.setFitsSystemWindows(child, false);
            ViewCompat.requestApplyInsets(child);
        }
    }

    /** Clears SYSTEM_UI_FLAG_LIGHT_STATUS_BAR (light icons on a dark bar). */
    @SuppressWarnings("deprecation")
    public static void cancelLightStatusBar(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;
        decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
}
