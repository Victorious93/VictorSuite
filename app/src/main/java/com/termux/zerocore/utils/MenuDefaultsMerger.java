package com.termux.zerocore.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.xh_lib.utils.UUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Brings new default drawer entries into the user's menu file.
 *
 * {@code ~/ZtInfo/main_menu_path.xml} is created once from the bundled default and then belongs to
 * the user; {@link XMLMergeUtils} only renames existing entries. Without this class, entries added in
 * later app versions never reach anyone who already has the file.
 *
 * Only built-in ({@code java:}) entries are handled. The set of default entries already offered is
 * remembered, so an entry is added once; if the user then deletes it, it stays deleted. On the first
 * run nothing is remembered yet, so every default entry missing from the file is added once.
 */
public class MenuDefaultsMerger {

    private static final String TAG = "MenuDefaultsMerger";
    private static final String PREFS_NAME = "menu_defaults";
    private static final String KEY_KNOWN = "known_default_clicks";
    private static final String JAVA_PREFIX = "java:";

    public static final class Result {
        public final String xml;
        public final int added;
        public final Set<String> defaultClicks;

        Result(String xml, int added, Set<String> defaultClicks) {
            this.xml = xml;
            this.added = added;
            this.defaultClicks = defaultClicks;
        }
    }

    /**
     * Pure function, no Android calls.
     *
     * @param userXml    the user's menu file
     * @param defaultXml the bundled default menu (same language as the user's file)
     * @param known      default {@code click} values already offered in earlier runs
     * @return the XML to write (the input string, unchanged, when nothing was added), how many
     *         entries were added, and every {@code java:} click in the default menu
     */
    public static Result merge(String userXml, String defaultXml, Set<String> known) {
        Document user = Jsoup.parse(userXml, "", Parser.xmlParser());
        Document defaults = Jsoup.parse(defaultXml, "", Parser.xmlParser());
        Set<String> defaultClicks = new LinkedHashSet<>();

        Element root = user.selectFirst("zt-menu");
        if (root == null) {
            return new Result(userXml, 0, collectClicks(defaults, defaultClicks));
        }

        Set<String> userClicks = new HashSet<>();
        for (Element item : user.select("item")) {
            userClicks.add(item.attr("click").trim());
        }

        int added = 0;
        String previousGroupName = null;
        for (Element defGroup : defaults.select("group")) {
            String groupName = defGroup.attr("name").trim();
            Element target = null;
            for (Element defItem : defGroup.select("item")) {
                String click = defItem.attr("click").trim();
                if (!click.startsWith(JAVA_PREFIX)) continue;
                defaultClicks.add(click);
                if (userClicks.contains(click) || known.contains(click)) continue;

                if (target == null) {
                    target = findGroup(user, groupName);
                    if (target == null) {
                        target = new Element("group").attr("name", groupName);
                        Element previous = previousGroupName == null ? null : findGroup(user, previousGroupName);
                        if (previous != null) {
                            previous.after(target);
                        } else {
                            root.appendChild(target);
                        }
                    }
                }
                target.appendChild(defItem.clone());
                userClicks.add(click);
                added++;
            }
            previousGroupName = groupName;
        }

        if (added == 0) {
            return new Result(userXml, 0, defaultClicks);
        }
        user.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        user.outputSettings().prettyPrint(false);
        user.outputSettings().charset("UTF-8");
        user.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        return new Result(user.outerHtml(), added, defaultClicks);
    }

    private static Set<String> collectClicks(Document defaults, Set<String> into) {
        for (Element item : defaults.select("item")) {
            String click = item.attr("click").trim();
            if (click.startsWith(JAVA_PREFIX)) into.add(click);
        }
        return into;
    }

    private static Element findGroup(Document doc, String name) {
        for (Element group : doc.select("group")) {
            if (group.attr("name").trim().equals(name)) return group;
        }
        return null;
    }

    /** Applies {@link #merge} to the user's menu file. Call after the file exists and was language-synced. */
    public static void apply(Context context, String targetLang) {
        File userFile = FileIOUtils.INSTANCE.getMainMenuXmlPathFile();
        if (!userFile.exists()) return;
        String assetPath = "en".equalsIgnoreCase(targetLang) ? "mainmenu/en/zt_menu_config.xml" : "mainmenu/cn/zt_menu_config.xml";
        try {
            String defaultXml = readStream(context.getAssets().open(assetPath));
            String userXml = readStream(new FileInputStream(userFile));
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> known = new HashSet<>(prefs.getStringSet(KEY_KNOWN, new HashSet<>()));

            Result result = merge(userXml, defaultXml, known);
            if (result.added > 0) {
                UUtils.setFileString(userFile, result.xml);
                Log.i(TAG, "Added " + result.added + " new default menu entries");
            }
            known.addAll(result.defaultClicks);
            prefs.edit().putStringSet(KEY_KNOWN, known).apply();
        } catch (Throwable e) {
            Log.e(TAG, "Merging new default menu entries failed", e);
        }
    }

    private static String readStream(InputStream in) throws java.io.IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
