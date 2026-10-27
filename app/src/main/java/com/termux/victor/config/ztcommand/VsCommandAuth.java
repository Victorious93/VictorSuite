package com.termux.victor.config.ztcommand;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Pure-JVM helpers for the ZT command socket: per-install token, bounded line reads,
 * and JSON escaping. The socket is loopback-only, but loopback is reachable by every
 * app on the device, so each request must start with the token stored in a file under
 * Termux's private home (readable only by the Termux user).
 */
public final class VsCommandAuth {
    public static final int MAX_LINE_CHARS = 4096;
    private static final int TOKEN_BYTES = 32;

    private VsCommandAuth() {
    }

    /** Reads the token file, creating it (owner-only) with a fresh random token if missing or empty. */
    public static synchronized String loadOrCreateToken(File file) throws IOException {
        if (file.isFile()) {
            String existing = readTokenFile(file);
            if (!existing.isEmpty()) {
                restrict(file);
                return existing;
            }
        }
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        byte[] raw = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(raw);
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        String token = hex.toString();
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((token + "\n").getBytes(StandardCharsets.UTF_8));
        }
        restrict(file);
        return token;
    }

    /**
     * Reads the (small) token file without java.nio.file, which is API 26+ and is not
     * covered by core-library desugaring on our minSdk 23. Bounded so a corrupted or
     * hostile file cannot make us buffer unbounded data.
     */
    static String readTokenFile(File file) throws IOException {
        try (Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[256];
            int n;
            while ((n = in.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > MAX_LINE_CHARS) throw new IOException("token file too large");
            }
            return sb.toString().trim();
        }
    }

    private static void restrict(File file) {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    /**
     * Reads one line of at most [maxChars] characters. Returns null on EOF with no data.
     * Throws IOException if the line is longer than the cap, so a client cannot make us
     * buffer unbounded input.
     */
    public static String readBoundedLine(Reader in, int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                if (sb.length() >= maxChars) {
                    throw new IOException("line exceeds " + maxChars + " characters");
                }
                sb.append((char) c);
            }
        }
        if (c == -1 && sb.length() == 0) return null;
        return sb.toString();
    }

    /**
     * Splits "TOKEN command..." and returns the command if TOKEN matches, else null.
     * A line with only the token yields an empty command (the help text).
     */
    public static String authenticate(String line, String expectedToken) {
        if (line == null || expectedToken == null || expectedToken.isEmpty()) return null;
        String trimmed = line.trim();
        int space = trimmed.indexOf(' ');
        String given = space < 0 ? trimmed : trimmed.substring(0, space);
        String rest = space < 0 ? "" : trimmed.substring(space + 1).trim();
        boolean ok = MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8));
        return ok ? rest : null;
    }

    /** Minimal JSON string escaping so error messages cannot break or inject into the reply. */
    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String json(int code, String message, String title) {
        return "{\"message\": \"" + escapeJson(message) + "\",\"code\": " + code
                + ",\"title\": \"" + escapeJson(title) + "\"}";
    }
}
