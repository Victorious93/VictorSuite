package com.termux.victor.motd;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class VsMotdTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void bannerFitsPhoneWidth() {
        assertFalse(VsMotd.BANNER.isEmpty());
        for (String line : VsMotd.BANNER) assertTrue("too wide: " + line, line.length() <= 50);
    }

    @Test public void textHasOwnLinksAndPkgHelp() {
        String t = VsMotd.text();
        assertFalse(t.contains("termux.dev"));
        assertTrue(t.contains("https://victorious93.github.io/VictorSuite/"));
        for (String s : new String[]{"pkg update", "pkg upgrade", "pkg search", "pkg install <package>",
                "pkg install root-repo", "pkg install x11-repo", "pkg install tur-repo"})
            assertTrue(s, t.contains(s));
        for (String line : t.split("\n")) assertTrue("too wide: " + line, line.length() <= 50);
    }

    @Test public void stockDetection() {
        assertTrue(VsMotd.isStock(VsMotd.STOCK_MOTD));
        assertTrue(VsMotd.isStock(VsMotd.STOCK_MOTD.replace("\n", "\r\n") + "  \n"));
        assertFalse(VsMotd.isStock(VsMotd.STOCK_MOTD + "my note\n"));
        assertFalse(VsMotd.shouldReplace(null));
        assertFalse(VsMotd.shouldReplace(VsMotd.text()));
    }

    private File motd(String content) throws Exception {
        File etc = new File(tmp.getRoot(), "etc");
        etc.mkdirs();
        File f = new File(etc, "motd");
        if (content != null) Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void replacesStock() throws Exception {
        File f = motd(VsMotd.STOCK_MOTD);
        assertTrue(VsMotd.installIfStock(tmp.getRoot()));
        assertEquals(VsMotd.text(), read(f));
        assertFalse(VsMotd.installIfStock(tmp.getRoot())); // idempotent
    }

    @Test public void keepsUserEdited() throws Exception {
        File f = motd("hello from Don\n");
        assertFalse(VsMotd.installIfStock(tmp.getRoot()));
        assertEquals("hello from Don\n", read(f));
    }

    @Test public void leavesMissingAlone() throws Exception {
        File f = motd(null);
        assertFalse(VsMotd.installIfStock(tmp.getRoot()));
        assertFalse(f.exists());
    }
}
