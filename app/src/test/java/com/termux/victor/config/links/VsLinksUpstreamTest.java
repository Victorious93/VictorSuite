package com.termux.victor.config.links;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.VictorSuiteLinks;
import com.termux.victor.config.VsLinks;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Guards against upstream ZeroTermux / Termux.org servers creeping back into user-facing links. */
public class VsLinksUpstreamTest {

    @Test
    public void pluginCatalogIsServedFromOurPagesSite() {
        assertEquals("https://victorious93.github.io/VictorSuite/plugins/catalog.json", VsLinks.PLUGIN_CATALOG);
        assertTrue(VsLinks.PLUGIN_CATALOG.startsWith(VsLinks.DOCS_SITE));
    }

    @Test
    public void helpPointsAtOwnedPagesManual() {
        assertEquals("https://victorious93.github.io/VictorSuite/MANUAL.html", VsLinks.HELP);
        assertTrue(VsLinks.HELP.startsWith(VsLinks.DOCS_SITE));
        assertEquals(VsLinks.REPO, VictorSuiteLinks.REPO);
        assertEquals(VsLinks.ISSUES, VictorSuiteLinks.ISSUES);
        assertEquals(VsLinks.DOCS_SITE, VictorSuiteLinks.DOCS_SITE);
        assertEquals(VsLinks.HELP, VictorSuiteLinks.HELP);
        assertEquals(VsLinks.RELEASES, VictorSuiteLinks.RELEASES);
    }

    @Test
    public void noIxcmstudioUrlsInSources() throws IOException {
        // Gradle runs unit tests with the module dir (app/) as working directory.
        File root = new File("..").getCanonicalFile();
        List<String> hits = new ArrayList<>();
        for (String module : new String[]{"app/src/main", "z_file/src/main", "terminal-view/src/main", "termux-shared/src/main"}) {
            scan(new File(root, module), hits, "ixcmstudio");
        }
        assertTrue("ixcmstudio URLs found: " + hits, hits.isEmpty());
    }

    @Test
    public void userFacingLinkOpenersDoNotPointAtUpstreamTermuxSites() throws IOException {
        File root = new File("..").getCanonicalFile();
        String[] paths = new String[]{
            "app/src/main/java/com/termux/app/activities/SettingsActivity.java",
            "app/src/main/java/com/termux/app/activities/HelpActivity.java",
            "app/src/main/java/com/termux/app/RunCommandService.java",
            "app/src/main/java/com/termux/app/TermuxApplication.java",
            "termux-shared/src/main/java/com/termux/shared/termux/TermuxUtils.java",
            "termux-shared/src/main/java/com/termux/shared/termux/VictorSuiteLinks.java",
            "app/src/main/java/com/termux/victor/config/VsLinks.kt",
            "app/src/main/assets/mainmenu/en/zt_menu_config.xml",
            "app/src/main/assets/mainmenu/cn/zt_menu_config.xml"
        };
        String[] needles = new String[]{
            "ixcmstudio",
            "termux.dev",
            "wiki.termux.com",
            "reddit.com/r/termux",
            "github.com/termux/termux-app",
            "www.google.com"
        };
        List<String> hits = new ArrayList<>();
        for (String rel : paths) {
            File f = new File(root, rel);
            assertTrue("missing " + rel, f.isFile());
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            for (String needle : needles) {
                if (text.contains(needle)) hits.add(rel + " contains " + needle);
            }
        }
        assertTrue("upstream or example URLs found in user-facing link files: " + hits, hits.isEmpty());
    }

    private static void scan(File f, List<String> hits, String needle) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) scan(c, hits, needle);
            return;
        }
        String n = f.getName();
        if (!(n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml") || n.endsWith(".json") || n.endsWith(".sh"))) return;
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        if (text.contains(needle)) hits.add(f.getPath());
    }
}
