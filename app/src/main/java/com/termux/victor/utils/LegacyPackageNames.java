package com.termux.victor.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps class names saved by builds before the com.termux.zerocore -> com.termux.victor move.
 *
 * The user's main-menu XML (copied to $HOME on first run) and the "menu_defaults" prefs store
 * fully-qualified class names in {@code java:} / {@code startActivity:} click values. Those were
 * written under the old package and must keep resolving after an in-place update.
 *
 * It also maps classes renamed by the ZT- and Zero-prefixed to Vs-prefixed debrand (plan batch 5). Old simple names
 * are built by concatenation so later mechanical renames do not rewrite them.
 */
public final class LegacyPackageNames {

    /** Intentional leftover: the pre-move package, needed only to migrate saved data. */
    public static final String LEGACY_PACKAGE = "com.termux" + ".zerocore.";
    public static final String CURRENT_PACKAGE = "com.termux.victor.";

    /** Old fully-qualified class name (under {@link #CURRENT_PACKAGE}) -> new one. */
    public static final Map<String, String> RENAMED_CLASSES;

    private static final Pattern CLASS_NAME =
        Pattern.compile("com\\.termux\\.victor\\.[A-Za-z0-9_.$]+");

    static {
        Map<String, String> m = new LinkedHashMap<>();
        String menu = CURRENT_PACKAGE + "config.mainmenu.config.";
        String settings = CURRENT_PACKAGE + "settings.";
        m.put(menu + "AndraxOpenClickConfig", menu + "VictorDraxOpenClickConfig");
        m.put(menu + "ZT" + "CommandKeyClickConfig", menu + "VsCommandKeyClickConfig");
        m.put(menu + "Zero" + "BBsClickConfig", menu + "VsBBsClickConfig");
        m.put(menu + "ZT" + "SettingsClickConfig", menu + "VsSettingsClickConfig");
        m.put(settings + "ZT" + "InstallActivity", settings + "VsInstallActivity");
        m.put(settings + "ZT" + "AboutActivity", settings + "VsAboutActivity");
        m.put(settings + "Zt" + "SettingsActivity", settings + "VsSettingsActivity");
        m.put(settings + "ZT" + "OnlineServerActivity", settings + "VsOnlineServerActivity");
        String oldCmd = CURRENT_PACKAGE + "config." + "zt" + "command.";
        String cmd = CURRENT_PACKAGE + "config.vscommand.";
        m.put(oldCmd + "activity.SocketBaseActivity", cmd + "activity.SocketBaseActivity");
        m.put(oldCmd + "VsSocketService", cmd + "VsSocketService");
        RENAMED_CLASSES = Collections.unmodifiableMap(m);
    }

    private LegacyPackageNames() {}

    /**
     * Returns {@code text} with every legacy package prefix replaced by the current one and every
     * renamed class name (whole name only, inner classes kept) replaced by its new name.
     */
    public static String migrate(String text) {
        if (text == null) return null;
        if (text.contains(LEGACY_PACKAGE)) text = text.replace(LEGACY_PACKAGE, CURRENT_PACKAGE);
        if (!text.contains(CURRENT_PACKAGE)) return text;
        Matcher matcher = CLASS_NAME.matcher(text);
        StringBuffer out = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String name = matcher.group();
            int inner = name.indexOf('$');
            String outer = inner < 0 ? name : name.substring(0, inner);
            String renamed = RENAMED_CLASSES.get(outer);
            if (renamed != null) {
                changed = true;
                name = inner < 0 ? renamed : renamed + name.substring(inner);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(name));
        }
        if (!changed) return text;
        matcher.appendTail(out);
        return out.toString();
    }

    public static Set<String> migrate(Set<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) out.add(migrate(v));
        return out;
    }
}
