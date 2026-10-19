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

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

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
        }
    }

    public static synchronized void setEnabled(Context context, boolean enabled) {
        Application app = (Application) context.getApplicationContext();
        prefs(app).edit().putBoolean(KEY_ENABLED, enabled).commit();
        applyComponentState(app, enabled);
        if (enabled) {
            start(app);
        } else {
            app.stopService(new Intent(app, KeepAliveService.class));
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
