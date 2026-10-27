package com.termux.victor.utils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps class names saved by builds before the com.termux.zerocore -> com.termux.victor move.
 *
 * The user's main-menu XML (copied to $HOME on first run) and the "menu_defaults" prefs store
 * fully-qualified class names in {@code java:} / {@code startActivity:} click values. Those were
 * written under the old package and must keep resolving after an in-place update.
 */
public final class LegacyPackageNames {

    /** Intentional leftover: the pre-move package, needed only to migrate saved data. */
    public static final String LEGACY_PACKAGE = "com.termux" + ".zerocore.";
    public static final String CURRENT_PACKAGE = "com.termux.victor.";

    private LegacyPackageNames() {}

    /** Returns {@code text} with every legacy package prefix replaced by the current one. */
    public static String migrate(String text) {
        if (text == null || !text.contains(LEGACY_PACKAGE)) return text;
        return text.replace(LEGACY_PACKAGE, CURRENT_PACKAGE);
    }

    public static Set<String> migrate(Set<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) out.add(migrate(v));
        return out;
    }
}
