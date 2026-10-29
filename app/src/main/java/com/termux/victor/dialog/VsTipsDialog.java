package com.termux.victor.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.app.TermuxInstaller;

/**
 * Readable "Tips / Gestures" dialog (replaces the faint watermark that used to sit behind the
 * terminal). Opened from the side menu, and shown once automatically on first launch.
 */
public final class VsTipsDialog {
    private static final String PREFS = "vs_tips";
    static final String KEY_SHOWN = "first_launch_tips_shown";

    private VsTipsDialog() {}

    public static String deviceInfo(Context context) {
        String arch;
        try {
            arch = TermuxInstaller.determineTermuxArchName().toUpperCase();
        } catch (Throwable t) {
            arch = "?";
        }
        return context.getString(R.string.vs_tips_device, Build.MANUFACTURER, Build.MODEL,
            Build.VERSION.RELEASE, arch);
    }

    public static void show(Context context) {
        show(context, false);
    }

    private static void show(Context context, boolean firstLaunch) {
        String msg = context.getString(R.string.vs_tips_body) + "\n\n" + deviceInfo(context);
        AlertDialog.Builder b = new AlertDialog.Builder(context)
            .setTitle(R.string.vs_tips_title)
            .setMessage(msg)
            .setPositiveButton(R.string.vs_tips_ok, (d, w) -> d.dismiss());
        if (firstLaunch) b.setCancelable(true);
        b.show();
    }

    /** Show the tips once, the first time the terminal is ready. Safe to call on every start. */
    public static void maybeShowFirstLaunch(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_SHOWN, false)) return;
        p.edit().putBoolean(KEY_SHOWN, true).apply();
        try {
            show(activity, true);
        } catch (Throwable ignored) {
            // Window not attached yet; the tips stay available from the side menu.
        }
    }
}
