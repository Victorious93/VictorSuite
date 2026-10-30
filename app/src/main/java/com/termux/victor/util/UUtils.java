package com.termux.victor.util;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.core.content.FileProvider;

import com.termux.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Application-wide helpers used across VictorSuite.
 *
 * <p>Written for VictorSuite to replace the subset of the prebuilt {@code xh_lib.aar}
 * {@code UUtils} class that the app actually calls. Behaviour (return values, toasts,
 * log tags) is kept the same so callers do not notice the switch.</p>
 *
 * <p>{@link #initUUtils(Context, Handler)} must be called from {@code Application.onCreate}
 * before any other method that needs a context or handler.</p>
 */
public final class UUtils {

    private static final String TAG = "Termux--Apk:UUtils";
    private static final String READ_FAILED = "File load failed!";
    private static final int COPY_BUFFER = 1024;
    private static final int PROGRESS_BUFFER = 50 * 1024;

    private static Context sContext;
    private static Handler sHandler;

    private UUtils() {
    }

    /** Callback for {@link #writerFileRawInput(File, InputStream, FileCallback)}. */
    public interface FileCallback {
        void callBack(String msg, boolean success);
    }

    /** Progress listener for {@link #writerFileInput(File, InputStream, WriterFileListener)}. */
    public interface WriterFileListener {
        void schedule(long bytes, boolean done);
    }

    // ---------------------------------------------------------------- context / threads

    public static void initUUtils(Context context, Handler handler) {
        sContext = context;
        sHandler = handler;
    }

    public static Context getContext() {
        return sContext;
    }

    public static Handler getHandler() {
        return sHandler;
    }

    private static boolean onMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /** Runs {@code r} now when already on the main thread, otherwise posts it there. */
    public static void runOnUIThread(Runnable r) {
        if (onMainThread()) {
            r.run();
        } else {
            sHandler.post(r);
        }
    }

    /** Runs {@code r} on a new thread when called from the main thread, otherwise inline. */
    public static void runOnThread(Runnable r) {
        if (onMainThread()) {
            new Thread(r).start();
        } else {
            r.run();
        }
    }

    public static void sleepSetRunMm(Runnable r, long delayMs) {
        sHandler.postDelayed(r, delayMs);
    }

    // ---------------------------------------------------------------- resources / UI

    public static String getString(int id) {
        return sContext.getResources().getString(id);
    }

    @SuppressWarnings("deprecation")
    public static int getColor(int id) {
        return sContext.getResources().getColor(id);
    }

    public static void showMsg(String msg) {
        Toast.makeText(sContext, msg, Toast.LENGTH_SHORT).show();
    }

    public static void showLog(String msg) {
        Log.e("ada:", msg);
    }

    public static View getViewLay(@LayoutRes int layout) {
        return View.inflate(sContext, layout, null);
    }

    public static View getViewLayViewGroup(@LayoutRes int layout, ViewGroup parent) {
        return LayoutInflater.from(sContext).inflate(layout, parent, false);
    }

    /**
     * Returns {@code color}'s RGB channels passed to {@link Color#argb} as
     * {@code (red, green, blue, alpha)}. The argument order is intentionally the same as the
     * original helper (red ends up in the alpha channel); existing colour settings depend on it.
     */
    public static int calculateAlphaValue(int color, int alpha) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return Color.argb(red, green, blue, alpha);
    }

    public static int getStatusBarHeight(Activity activity) {
        Resources res = activity.getResources();
        int id = res.getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? res.getDimensionPixelSize(id) : 0;
    }

    /** Throws {@link Resources.NotFoundException} when the platform has no such dimen. */
    public static int getNavigationBarHeight(Activity activity) {
        Resources res = activity.getResources();
        int height = res.getDimensionPixelSize(res.getIdentifier("navigation_bar_height", "dimen", "android"));
        Log.v("dbw", "Navi height:" + height);
        return height;
    }

    public static void copyToClip(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) sContext.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText(text);
            showMsg(getString(R.string.vs_copied_successfully));
        } catch (Exception e) {
            showMsg(getString(R.string.vs_copy_failed));
        }
    }

    // ---------------------------------------------------------------- misc

    public static String getVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /** Formats {@code millis} as {@code yyyy-MM-dd HH:mm:ss} in UTC. */
    public static String getFtpDate(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(millis));
    }

    /** Joins the items, each followed by a single space (so the result has a trailing space). */
    public static String arrayListToStringShell(ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s).append(' ');
        }
        return sb.toString();
    }

    /** All non-loopback IPv4 addresses joined by newlines, or {@code null} if none. */
    public static String getHostIP() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                Enumeration<InetAddress> addrs = nis.nextElement().getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet6Address) continue;
                    String ip = a.getHostAddress();
                    if ("127.0.0.1".equals(ip)) continue;
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(ip);
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static void startUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sContext.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean installApk(Context context, String path) {
        try {
            if (TextUtils.isEmpty(path)) {
                Toast.makeText(context, "Directory is empty?", Toast.LENGTH_SHORT).show();
                return false;
            }
            File apk = new File(path);
            if (!apk.exists()) {
                Toast.makeText(context, "No storage permission?" + path, Toast.LENGTH_SHORT).show();
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileProvider", apk);
            } else {
                uri = Uri.fromFile(apk);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);
            return true;
        } catch (Exception | Error e) {
            e.printStackTrace();
            Toast.makeText(context, "Install failed, please download again", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ---------------------------------------------------------------- files

    /** Sets {@code file} to mode 0700. Errors are logged and ignored. */
    public static void chmod(File file) {
        try {
            Os.chmod(file.getAbsolutePath(), 0700);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Copies the asset {@code name} to {@code dest}. Errors are silently ignored. */
    public static void writerFile(String name, File dest) {
        try {
            copyAndClose(sContext.getAssets().open(name), dest, COPY_BUFFER);
        } catch (Exception ignored) {
        }
    }

    /** Copies raw resource {@code id} to {@code dest}. Errors are silently ignored. */
    public static void writerFileRaw(File dest, int id) {
        try {
            copyAndClose(sContext.getResources().openRawResource(id), dest, COPY_BUFFER);
        } catch (Exception ignored) {
        }
    }

    /** Copies {@code in} to {@code dest} and closes {@code in}. Errors are logged. */
    public static void writerFileRawInput(File dest, InputStream in) {
        Log.e(TAG, "writerFileRawInput: start");
        try {
            copyAndClose(in, dest, COPY_BUFFER);
            Log.e(TAG, "writerFileRawInput: done");
        } catch (Exception e) {
            Log.e(TAG, "writerFileRawInput: " + e);
            e.printStackTrace();
        }
    }

    /** Like {@link #writerFileRawInput(File, InputStream)} and reports success to {@code cb}. */
    public static void writerFileRawInput(File dest, InputStream in, FileCallback cb) {
        Log.e(TAG, "writerFileRawInput: start");
        boolean ok;
        try {
            copyAndClose(in, dest, COPY_BUFFER);
            ok = true;
        } catch (Exception e) {
            Log.e(TAG, "writerFileRawInput: " + e);
            e.printStackTrace();
            ok = false;
        }
        cb.callBack("", ok);
        if (ok) Log.e(TAG, "writerFileRawInput: done");
    }

    /**
     * Copies {@code in} to {@code dest}, calling {@code listener.schedule(n, false)} after each
     * chunk and {@code schedule(n, true)} once finished. As before, {@code n} counts whole
     * 50&nbsp;KiB buffers, so it can overshoot the real size on the last chunk. On error nothing
     * further is reported.
     */
    public static void writerFileInput(File dest, InputStream in, WriterFileListener listener) {
        Log.e(TAG, "writerFileRawInput: start");
        try {
            ensureExists(dest);
            long progress = 0;
            byte[] buf = new byte[PROGRESS_BUFFER];
            try (OutputStream out = new FileOutputStream(dest)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    progress += buf.length;
                    listener.schedule(progress, false);
                }
                out.flush();
            } finally {
                in.close();
            }
            listener.schedule(progress, true);
            Log.e(TAG, "writerFileRawInput: done");
        } catch (Exception e) {
            Log.e(TAG, "writerFileRawInput: " + e);
            e.printStackTrace();
        }
    }

    /** Reads {@code file} line by line (each line ends with \n). Creates it if missing. */
    public static String getFileString(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return readLines(file);
    }

    /** Like {@link #getFileString(File)} but returns "" when the file does not exist. */
    public static String getFileStringNotNewFile(File file) {
        if (!file.exists()) return "";
        return readLines(file);
    }

    /** Overwrites {@code file} with {@code text}. Returns false if it cannot be opened. */
    public static boolean setFileString(File file, String text) {
        PrintWriter w;
        try {
            w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file)));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        w.print(text);
        w.flush();
        w.close();
        return true;
    }

    private static String readLines(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return READ_FAILED + e;
        }
    }

    private static void ensureExists(File f) throws IOException {
        if (!f.exists()) {
            f.createNewFile();
            Log.e(TAG, "writerFileRawInput: createNewFile");
        }
    }

    private static void copyAndClose(InputStream in, File dest, int bufSize) throws IOException {
        try {
            ensureExists(dest);
            try (OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[bufSize];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
        } finally {
            in.close();
        }
    }

    // ---------------------------------------------------------------- content URIs

    /**
     * Best-effort conversion of a picker {@link Uri} to a filesystem path. On Android 10+ a
     * {@code content://} URI is copied into the external cache dir and that copy's path returned.
     */
    public static String getFileAbsolutePath(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return legacyPath(context, uri);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && DocumentsContract.isDocumentUri(context, uri)) {
            String authority = uri.getAuthority();
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.externalstorage.documents".equals(authority)) {
                String[] parts = docId.split(":");
                if ("primary".equalsIgnoreCase(parts[0])) {
                    return Environment.getExternalStorageDirectory() + "/" + parts[1];
                }
            } else if ("com.android.providers.downloads.documents".equals(authority)) {
                Uri content = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                return queryDataColumn(context, content, null, null);
            } else if ("com.android.providers.media.documents".equals(authority)) {
                String[] parts = docId.split(":");
                Uri base = null;
                if ("image".equals(parts[0])) base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                else if ("video".equals(parts[0])) base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                else if ("audio".equals(parts[0])) base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                return queryDataColumn(context, base, "_id=?", new String[]{parts[1]});
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return copyToCacheQ(context, uri);
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            if ("com.google.android.apps.photos.content".equals(uri.getAuthority())) {
                return uri.getLastPathSegment();
            }
            return queryDataColumn(context, uri, null, null);
        }
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private static String legacyPath(Context context, Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || "file".equals(scheme)) return uri.getPath();
        if (!"content".equals(scheme)) return null;
        try (Cursor c = context.getContentResolver().query(
            uri, new String[]{MediaStore.Images.ImageColumns.DATA}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                if (idx > -1) return c.getString(idx);
            }
        }
        return null;
    }

    private static String queryDataColumn(Context context, Uri uri, String selection, String[] args) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{"_data"}, selection, args, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(c.getColumnIndexOrThrow("_data"));
            }
        }
        return null;
    }

    private static String copyToCacheQ(Context context, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return new File(uri.getPath()).getAbsolutePath();
        }
        if (!"content".equals(uri.getScheme())) return null;
        ContentResolver resolver = context.getContentResolver();
        File copy = null;
        try (Cursor c = resolver.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(c.getColumnIndex("_display_name"));
                try (InputStream in = resolver.openInputStream(uri)) {
                    File cacheDir = context.getExternalCacheDir();
                    copy = new File(cacheDir.getAbsolutePath(), Math.round((Math.random() + 1) * 1000) + name);
                    try (OutputStream out = new FileOutputStream(copy)) {
                        android.os.FileUtils.copy(in, out);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return copy == null ? null : copy.getAbsolutePath();
    }
}
