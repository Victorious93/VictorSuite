package com.termux.victor.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class LegacyPackageNamesTest {

    private static final String OLD_PKG = "com.termux" + ".zerocore.";

    @Test
    public void nullAndPlainTextUnchanged() {
        assertNull(LegacyPackageNames.migrate((String) null));
        assertEquals("hello", LegacyPackageNames.migrate("hello"));
        String current = "java:com.termux.victor.config.mainmenu.config.VsSettingsClickConfig";
        assertEquals(current, LegacyPackageNames.migrate(current));
    }

    @Test
    public void oldPackageStillMigrates() {
        assertEquals("java:com.termux.victor.utils.Foo",
            LegacyPackageNames.migrate("java:" + OLD_PKG + "utils.Foo"));
    }

    @Test
    public void renamedClassUnderCurrentPackage() {
        assertEquals("startActivity:com.termux.victor.settings.VsSettingsActivity",
            LegacyPackageNames.migrate("startActivity:com.termux.victor.settings." + "Zt" + "SettingsActivity"));
    }

    @Test
    public void renamedClassUnderOldPackage() {
        assertEquals("java:com.termux.victor.config.mainmenu.config.VsBBsClickConfig",
            LegacyPackageNames.migrate("java:" + OLD_PKG + "config.mainmenu.config." + "Zero" + "BBsClickConfig"));
    }

    @Test
    public void everyMappedClassMigratesFromBothPackages() {
        for (Map.Entry<String, String> e : LegacyPackageNames.RENAMED_CLASSES.entrySet()) {
            assertEquals(e.getValue(), LegacyPackageNames.migrate(e.getKey()));
            String oldPkg = OLD_PKG + e.getKey().substring(LegacyPackageNames.CURRENT_PACKAGE.length());
            assertEquals(e.getValue(), LegacyPackageNames.migrate(oldPkg));
            assertTrue(e.getValue().startsWith(LegacyPackageNames.CURRENT_PACKAGE));
        }
    }

    @Test
    public void innerClassSuffixKept() {
        assertEquals("com.termux.victor.settings.VsAboutActivity$Inner",
            LegacyPackageNames.migrate("com.termux.victor.settings." + "ZT" + "AboutActivity$Inner"));
    }

    @Test
    public void longerNameWithSamePrefixNotRewritten() {
        String other = "com.termux.victor.settings." + "ZT" + "AboutActivityExtra";
        assertEquals(other, LegacyPackageNames.migrate(other));
    }

    @Test
    public void xmlWithSeveralEntries() {
        String xml = "<item click=\"java:" + OLD_PKG + "config.mainmenu.config." + "ZT" + "SettingsClickConfig\"/>"
            + "<item click=\"startActivity:com.termux.victor.settings." + "ZT" + "InstallActivity\"/>"
            + "<item click=\"java:com.termux.victor.config.mainmenu.config.UnchangedConfig\"/>";
        String expected = "<item click=\"java:com.termux.victor.config.mainmenu.config.VsSettingsClickConfig\"/>"
            + "<item click=\"startActivity:com.termux.victor.settings.VsInstallActivity\"/>"
            + "<item click=\"java:com.termux.victor.config.mainmenu.config.UnchangedConfig\"/>";
        assertEquals(expected, LegacyPackageNames.migrate(xml));
    }

    @Test
    public void setMigratesEachValue() {
        Set<String> in = new LinkedHashSet<>(Arrays.asList(
            "com.termux.victor.settings." + "ZT" + "OnlineServerActivity", "plain"));
        Set<String> out = LegacyPackageNames.migrate(in);
        assertEquals(new LinkedHashSet<>(Arrays.asList(
            "com.termux.victor.settings.VsOnlineServerActivity", "plain")), out);
    }
}
