package com.termux.victor.engine;

import android.os.Handler;
import android.view.View;

import java.util.ArrayList;

/**
 * Hook for an optional native engine add-on (custom keyboard view, x86 container launcher,
 * file browser installer).
 *
 * VictorSuite does not ship or link to such an add-on, so every call reports "not installed":
 * {@link #getVersionName()} returns an empty string and callers fall back to their built-in
 * behaviour. The old reflection loader for the upstream add-on APK was removed in the debrand.
 */
public final class VsEngineManage {
    public static final int INSTALLING = 10002;
    public static final int INSTALL_COMPLETE = 10003;

    private VsEngineManage() {}

    public static void initEngineManage() {}

    public static ArrayList<String> getEnvironment() { return null; }

    public static ArrayList<String> getProcessArgs() { return null; }

    public static String getDataDirectory() { return null; }

    public static void setContext() {}

    public static void setEngineContext() {}

    public static void install(Handler handler) {}

    /** Empty when no engine add-on is installed (always, in VictorSuite). */
    public static String getVersionName() { return ""; }

    public static void setRunHandler(Handler handler) {}

    public static void setKeyHandler(Handler handler) {}

    public static void installFileBrowser(Handler handler) {}

    public static void initKeyView() {}

    public static View getKeyView() { return null; }
}
