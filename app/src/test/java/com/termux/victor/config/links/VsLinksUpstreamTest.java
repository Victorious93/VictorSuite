package com.termux.victor.config.links;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.victor.config.VsLinks;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Guards against upstream ZeroTermux servers creeping back into the app. */
public class VsLinksUpstreamTest {

    @Test
    public void pluginCatalogIsServedFromOurPagesSite() {
        assertEquals("https://victorious93.github.io/VictorSuite/plugins/catalog.json", VsLinks.PLUGIN_CATALOG);
        assertTrue(VsLinks.PLUGIN_CATALOG.startsWith(VsLinks.DOCS_SITE));
    }

    @Test
    public void noIxcmstudioUrlsInSources() throws IOException {
        // Gradle runs unit tests with the module dir (app/) as working directory.
        File root = new File("..").getCanonicalFile();
        List<String> hits = new ArrayList<>();
        for (String module : new String[]{"app/src/main", "z_file/src/main", "terminal-view/src/main", "termux-shared/src/main"}) {
            scan(new File(root, module), hits);
        }
        assertTrue("ixcmstudio URLs found: " + hits, hits.isEmpty());
    }

    private static void scan(File f, List<String> hits) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) scan(c, hits);
            return;
        }
        String n = f.getName();
        if (!(n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml") || n.endsWith(".json") || n.endsWith(".sh"))) return;
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        if (text.contains("ixcmstudio")) hits.add(f.getPath());
    }
}
