package com.termux.victor.ssh;

import java.util.regex.Pattern;

/**
 * Redacts secrets from text before it can reach logcat or a log file.
 *
 * <p>Pure Java (no Android APIs).
 */
public final class QuickSshRedactor {

    public static final String MASK = "***";

    // A shell word: a single-quoted run (with '\'' escapes), a double-quoted run, or bare text.
    private static final String WORD = "(?:'(?:[^']|'\\\\'')*'|\"(?:[^\"\\\\]|\\\\.)*\"|\\S+)";

    private static final Pattern SSHPASS_P = Pattern.compile("(sshpass\\s+-p\\s*)" + WORD);
    private static final Pattern SSHPASS_ENV = Pattern.compile("(SSHPASS=)" + WORD);
    private static final Pattern JSON_PASSWORD = Pattern.compile("(\"password(?:Enc)?\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");

    private QuickSshRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = SSHPASS_P.matcher(text).replaceAll("$1" + MASK);
        out = SSHPASS_ENV.matcher(out).replaceAll("$1" + MASK);
        out = JSON_PASSWORD.matcher(out).replaceAll("$1\"" + MASK + "\"");
        return out;
    }

    /** A safe one-line description of a throwable: class name plus redacted message. */
    public static String describe(Throwable t) {
        if (t == null) return "null";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : ": " + redact(msg));
    }
}
