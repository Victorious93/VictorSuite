package com.termux.api;

// VictorSuite add {@
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.system.ErrnoException;
import android.system.Os;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * On/off switch for the Termux:API code embedded in VictorSuite.
 *
 * Every {@code com.termux.api.*} manifest component is declared {@code android:enabled="false"}.
 * Turning the feature on enables them and starts {@link SocketListener}; turning it off disables
 * them again and makes the listener and {@link TermuxApiReceiver} refuse requests.
 * The setting defaults to off.
 */
public final class TermuxApiEmbed {

    private static final String LOG_TAG = "TermuxApiEmbed";

    private static final String PREFS_NAME = "termux_api_embed";
    private static final String KEY_ENABLED = "enabled";

    /** All embedded components live under this package, so they can be found without a list. */
    private static final String COMPONENT_PACKAGE_PREFIX = "com.termux.api.";

    /** Name the bundled helper executable has in the app's native library directory. */
    private static final String HELPER_LIB_NAME = "libtermux-api-helper.so";

    private TermuxApiEmbed() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    /** Call from {@code Application.onCreate()}. Brings component state in line with the setting. */
    public static synchronized void init(Application app) {
        boolean enabled = isEnabled(app);
        if (isReceiverEnabled(app) != enabled) {
            applyComponentState(app, enabled);
        }
        if (enabled) {
            start(app);
            installHelperAsync(app);
        }
    }

    public static synchronized void setEnabled(Context context, boolean enabled) {
        Application app = (Application) context.getApplicationContext();
        prefs(app).edit().putBoolean(KEY_ENABLED, enabled).commit();
        applyComponentState(app, enabled);
        if (enabled) {
            start(app);
            installHelperAsync(app);
        } else {
            app.stopService(new Intent(app, KeepAliveService.class));
        }
    }

    private static void installHelperAsync(Context context) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> installHelper(appContext), "TermuxApiHelperInstall").start();
    }

    /**
     * Replaces {@code $PREFIX/libexec/termux-api-broadcast} (and the {@code termux-api} link the termux-*
     * scripts call) with the helper bundled in this APK. The bundled helper talks to the receiver embedded
     * here instead of {@code com.termux.api}, which is what makes the commands work on Android 14+.
     *
     * Only acts when the {@code termux-api} package is already installed, so nothing is left behind that
     * could clash with {@code pkg install termux-api}. Reinstalling or upgrading that package puts the
     * stock helper back; this runs again at the next app start or when the setting is switched on.
     */
    private static synchronized void installHelper(Context context) {
        File libexec = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "libexec");
        File target = new File(libexec, "termux-api-broadcast");
        File link = new File(libexec, "termux-api");
        if (!target.exists() && !link.exists()) {
            Logger.logInfo(LOG_TAG, "termux-api package not installed, not installing the helper");
            return;
        }
        File source = new File(context.getApplicationInfo().nativeLibraryDir, HELPER_LIB_NAME);
        if (!source.isFile()) {
            Logger.logError(LOG_TAG, "Bundled helper missing: " + source.getAbsolutePath());
            return;
        }
        try {
            if (!sameContent(source, target)) {
                File temp = new File(libexec, ".termux-api-broadcast.tmp");
                copy(source, temp);
                Os.chmod(temp.getAbsolutePath(), 0755);
                Os.rename(temp.getAbsolutePath(), target.getAbsolutePath());
                Logger.logInfo(LOG_TAG, "Installed termux-api helper to " + target.getAbsolutePath());
            }
            if (!"termux-api-broadcast".equals(readLink(link))) {
                link.delete();
                Os.symlink("termux-api-broadcast", link.getAbsolutePath());
            }
        } catch (IOException | ErrnoException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to install termux-api helper", e);
        }
    }

    private static String readLink(File link) {
        try {
            return Os.readlink(link.getAbsolutePath());
        } catch (ErrnoException e) {
            return null; // not a symlink, or missing
        }
    }

    private static boolean sameContent(File a, File b) throws IOException {
        if (!a.isFile() || !b.isFile() || a.length() != b.length()) return false;
        return Arrays.equals(readAll(a), readAll(b)); // files are a few KB
    }

    private static byte[] readAll(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            int total = 0;
            while (total < data.length) {
                int n = in.read(data, total, data.length - total);
                if (n < 0) throw new IOException("Unexpected end of file: " + file);
                total += n;
            }
        }
        return data;
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }
    }

    private static void start(Application app) {
        ResultReturner.setContext(app);
        SocketListener.createSocketListener(app);
    }

    private static boolean isReceiverEnabled(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(context, TermuxApiReceiver.class));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    @SuppressWarnings("deprecation")
    private static void applyComponentState(Context context, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try {
            // GET_DISABLED_COMPONENTS is needed so components that are currently disabled are listed.
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES
                    | PackageManager.GET_PROVIDERS | PackageManager.GET_DISABLED_COMPONENTS);
            int count = 0;
            count += setState(pm, pkg, info.activities, state);
            count += setState(pm, pkg, info.receivers, state);
            count += setState(pm, pkg, info.services, state);
            count += setState(pm, pkg, info.providers, state);
            Logger.logInfo(LOG_TAG, "Termux:API components " + (enabled ? "enabled" : "disabled") + ": " + count);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to list components of " + pkg, e);
        }
    }

    private static int setState(PackageManager pm, String pkg, ComponentInfo[] components, int state) {
        if (components == null) return 0;
        int count = 0;
        for (ComponentInfo component : components) {
            if (component.name.startsWith(COMPONENT_PACKAGE_PREFIX)) {
                pm.setComponentEnabledSetting(new ComponentName(pkg, component.name), state,
                        PackageManager.DONT_KILL_APP);
                count++;
            }
        }
        return count;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
// @}
