package com.termux.victor.util;

import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Tagged logcat helpers, written for VictorSuite to replace {@code xh_lib.aar}'s LogUtils.
 *
 * <p>Every call logs as {@code "ZeroTermux--" + tag}. When {@link #isShow} is true (the
 * "output log" user setting) the line is also appended to
 * {@code <external storage>/xinhao/ZeroTermuxLog/ZeroTermux.log}.</p>
 */
public final class LogUtils {

    public static String TAG = "ZeroTermux";

    /** Mirrors the user's "output log to file" setting. */
    public static boolean isShow = false;

    private LogUtils() {
    }

    public static void e(String tag, String msg) {
        Log.e(fullTag(tag), msg);
        toFile("e", tag, msg);
    }

    public static void i(String tag, String msg) {
        Log.i(fullTag(tag), msg);
        toFile("i", tag, msg);
    }

    public static void d(String tag, String msg) {
        Log.d(fullTag(tag), msg);
        toFile("d", tag, msg);
    }

    private static String fullTag(String tag) {
        return TAG + "--" + tag;
    }

    private static void toFile(String level, String tag, String msg) {
        if (!isShow) return;
        Log.i(TAG, level + ": output log is open...");
        appendToLogFile(fullTag(tag) + msg);
    }

    /**
     * Appends {@code line} to the log file. Like the original this rewrites the whole file each
     * time (read + write), so it is slow for large logs; it only runs when logging is enabled.
     */
    static void appendToLogFile(String line) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/xinhao/ZeroTermuxLog");
            if (!dir.exists()) dir.mkdirs();
            File log = new File(dir, "ZeroTermux.log");
            String old = UUtils.getFileString(log);
            UUtils.setFileString(log, old + line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
