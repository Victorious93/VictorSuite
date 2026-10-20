package com.termux.zerocore.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class MenuDefaultsMergerTest {

    private static final String P = "java:com.termux.zerocore.config.mainmenu.config.";

    private static final String DEFAULTS = "<zt-menu>"
        + "<group name=\"Common\">"
        + "<item tag=\"A\" name=\"A\" click=\"" + P + "AClickConfig\" />"
        + "<item tag=\"Plugin\" name=\"Plugin\" click=\"" + P + "PluginClickConfig\" />"
        + "</group>"
        + "<group name=\"Linux Login\">"
        + "<item tag=\"U\" name=\"Ubuntu\" click=\"" + P + "UbuntuLoginClickConfig\" />"
        + "<item tag=\"K\" name=\"Kali\" click=\"" + P + "KaliLoginClickConfig\" />"
        + "</group>"
        + "<group name=\"Last\">"
        + "<item tag=\"Z\" name=\"Z\" click=\"" + P + "ZClickConfig\" />"
        + "<item tag=\"Web\" name=\"Web\" click=\"jumpUrl:https://example.com\" />"
        + "</group>"
        + "</zt-menu>";

    /** An old user file: no Plugin, no Linux Login group, plus a custom shell entry. */
    private static final String OLD_USER = "<zt-menu>"
        + "<group name=\"Common\">"
        + "<item tag=\"A\" name=\"A\" click=\"" + P + "AClickConfig\" />"
        + "<item tag=\"Mine\" name=\"Mine\" click=\"ztShell:echo hi\" />"
        + "</group>"
        + "<group name=\"Last\">"
        + "<item tag=\"Z\" name=\"Z\" click=\"" + P + "ZClickConfig\" />"
        + "</group>"
        + "</zt-menu>";

    private static boolean has(String xml, String needle) {
        return xml.contains(needle);
    }

    @Test
    public void addsMissingItemsAndGroupsOnFirstRun() {
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(OLD_USER, DEFAULTS, new HashSet<>());
        assertEquals(3, r.added);
        assertTrue(has(r.xml, "PluginClickConfig"));
        assertTrue(has(r.xml, "UbuntuLoginClickConfig"));
        assertTrue(has(r.xml, "KaliLoginClickConfig"));
        assertTrue(has(r.xml, "name=\"Linux Login\""));
        // the custom entry is untouched
        assertTrue(has(r.xml, "ztShell:echo hi"));
        // new item went into the existing group, not a new one
        assertEquals(1, r.xml.split("name=\"Common\"", -1).length - 1);
        // new group sits right after the group that precedes it in the defaults
        assertTrue(r.xml.indexOf("name=\"Common\"") < r.xml.indexOf("name=\"Linux Login\""));
        assertTrue(r.xml.indexOf("name=\"Linux Login\"") < r.xml.indexOf("name=\"Last\""));
    }

    @Test
    public void nonJavaDefaultsAreNeverAdded() {
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(OLD_USER, DEFAULTS, new HashSet<>());
        assertFalse(has(r.xml, "jumpUrl:"));
        assertFalse(r.defaultClicks.contains("jumpUrl:https://example.com"));
    }

    @Test
    public void secondRunAddsNothingAndReturnsInputUnchanged() {
        MenuDefaultsMerger.Result first = MenuDefaultsMerger.merge(OLD_USER, DEFAULTS, new HashSet<>());
        Set<String> known = new HashSet<>(first.defaultClicks);
        MenuDefaultsMerger.Result second = MenuDefaultsMerger.merge(first.xml, DEFAULTS, known);
        assertEquals(0, second.added);
        assertSame(first.xml, second.xml);
    }

    @Test
    public void entryDeletedAfterBeingOfferedStaysDeleted() {
        MenuDefaultsMerger.Result first = MenuDefaultsMerger.merge(OLD_USER, DEFAULTS, new HashSet<>());
        Set<String> known = new HashSet<>(first.defaultClicks);
        String withoutKali = first.xml.replaceAll("<item[^>]*KaliLoginClickConfig[^>]*/>", "");
        assertFalse(has(withoutKali, "KaliLoginClickConfig"));
        MenuDefaultsMerger.Result again = MenuDefaultsMerger.merge(withoutKali, DEFAULTS, known);
        assertEquals(0, again.added);
        assertFalse(has(again.xml, "KaliLoginClickConfig"));
    }

    @Test
    public void entryNewInALaterVersionIsAddedOnce() {
        Set<String> known = new HashSet<>();
        known.add(P + "AClickConfig");
        known.add(P + "PluginClickConfig");
        known.add(P + "UbuntuLoginClickConfig");
        known.add(P + "ZClickConfig");
        // Kali is not in `known` (new in this version) and not in the file
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(OLD_USER, DEFAULTS, known);
        assertEquals(1, r.added);
        assertTrue(has(r.xml, "KaliLoginClickConfig"));
        assertFalse(has(r.xml, "UbuntuLoginClickConfig")); // already offered earlier, user must have removed it
    }

    @Test
    public void freshFileFromDefaultsAddsNothing() {
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(DEFAULTS, DEFAULTS, new HashSet<>());
        assertEquals(0, r.added);
        assertEquals(5, r.defaultClicks.size());
    }

    @Test
    public void renamedGroupGetsANewGroupInsteadOfBeingGuessed() {
        String renamed = OLD_USER.replace("name=\"Common\"", "name=\"My Stuff\"");
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(renamed, DEFAULTS, new HashSet<>());
        assertTrue(has(r.xml, "name=\"My Stuff\""));
        assertTrue(has(r.xml, "name=\"Common\""));
        assertTrue(has(r.xml, "PluginClickConfig"));
    }

    @Test
    public void oldDefaultGroupNameIsRenamedButCustomNamesAreNot() {
        String old = "<zt-menu><group name=\"ZT Functions\">"
            + "<item tag=\"A\" name=\"A\" click=\"" + P + "AClickConfig\" /></group>"
            + "<group name=\"ZT 功能\"><item tag=\"B\" name=\"B\" click=\"" + P + "BClickConfig\" /></group>"
            + "<group name=\"My ZT Functions\"><item tag=\"C\" name=\"C\" click=\"" + P + "CClickConfig\" /></group>"
            + "</zt-menu>";
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(old, "<zt-menu/>", new HashSet<>());
        assertTrue(has(r.xml, "name=\"VictorSuite Functions\""));
        assertTrue(has(r.xml, "name=\"VictorSuite功能\""));
        assertTrue(has(r.xml, "name=\"My ZT Functions\""));
        assertFalse(has(r.xml, "name=\"ZT Functions\""));
    }

    @Test
    public void renameIsSkippedWhenTheNewNameAlreadyExists() {
        String both = "<zt-menu><group name=\"ZT Functions\"/><group name=\"VictorSuite Functions\"/></zt-menu>";
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge(both, "<zt-menu/>", new HashSet<>());
        assertEquals(both, r.xml);
    }

    @Test
    public void garbageWithoutRootIsLeftAlone() {
        MenuDefaultsMerger.Result r = MenuDefaultsMerger.merge("<nope/>", DEFAULTS, new HashSet<>());
        assertEquals(0, r.added);
        assertEquals("<nope/>", r.xml);
    }
}
