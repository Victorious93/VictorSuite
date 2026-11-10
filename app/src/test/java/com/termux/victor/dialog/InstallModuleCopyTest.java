package com.termux.victor.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Guards against the user-visible ZeroTermux install-module copy creeping back.
 * Paths under /sdcard/xinhao/module stay on purpose for existing user data.
 */
public class InstallModuleCopyTest {

    @Test
    public void installModuleStringsAreReadableEnglish() throws IOException {
        File root = new File("..").getCanonicalFile();
        String en = read(new File(root, "app/src/main/res/values/strings.xml"));
        assertTrue(en.contains("Install Module (Download)"));
        assertTrue(en.contains("No modules in /sdcard/xinhao/module"));
        assertFalse("old DownLoad Module title returned", en.contains("DownLoad Module"));
        assertFalse("old comma-no-data empty copy returned", en.contains("sdcard/xinhao/module,no data"));
    }

    @Test
    public void beautifyDialogNoLongerExposesDeadWatermarkSwitch() throws IOException {
        File root = new File("..").getCanonicalFile();
        String layout = read(new File(root, "app/src/main/res/layout/dialog_beauify.xml"));
        assertFalse(layout.contains("back_text_show_switch"));
        assertFalse(layout.contains("vs_show_back_font"));
        String dialog = read(new File(root, "app/src/main/java/com/termux/victor/dialog/BeautifySettingDialog.kt"));
        assertFalse(dialog.contains("back_text_show_switch"));
        assertFalse(dialog.contains("OnTextCheckedChangeListener"));
    }

    @Test
    public void intentionalCompatTokensStillPresent() throws IOException {
        File root = new File("..").getCanonicalFile();
        List<String> mustKeep = Arrays.asList(
            "zt.token",
            "zt_menu_config.xml",
            "ztEditText:",
            "/ZtInfo/"
        );
        String service = read(new File(root, "app/src/main/java/com/termux/victor/config/vscommand/VsSocketService.java"));
        String merger = read(new File(root, "app/src/main/java/com/termux/victor/utils/MenuDefaultsMerger.java"));
        String fileIo = read(new File(root, "app/src/main/java/com/termux/victor/utils/FileIOUtils.kt"));
        String enMenu = read(new File(root, "app/src/main/assets/mainmenu/en/zt_menu_config.xml"));
        String blob = service + "\n" + merger + "\n" + fileIo + "\n" + enMenu;
        for (String token : mustKeep) {
            assertTrue("compat token missing: " + token, blob.contains(token));
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
