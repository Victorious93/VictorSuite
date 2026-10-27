package com.termux.victor.http_service;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM path and text helpers for the built-in HTTP file server. The server is reachable
 * by anyone on the network, so request paths and upload names must never leave their roots.
 * Resolution is lexical (symlinks inside the root, such as ~/storage, still work).
 */
public final class HttpPathGuard {
    private HttpPathGuard() {
    }

    /**
     * Resolves a request path under [root]. "." and empty segments are dropped and ".." pops one
     * segment; a ".." that would climb above the root, or a NUL character, returns null.
     */
    public static File resolveInside(File root, String requestPath) {
        if (requestPath == null || requestPath.indexOf('\0') >= 0) return null;
        List<String> parts = new ArrayList<>();
        for (String seg : requestPath.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (parts.isEmpty()) return null;
                parts.remove(parts.size() - 1);
            } else {
                parts.add(seg);
            }
        }
        File out = root;
        for (String p : parts) out = new File(out, p);
        return out;
    }

    /**
     * Reduces a client-supplied upload name to a plain file name (no directories). Returns null if
     * nothing usable remains ("", ".", "..", NUL).
     */
    public static String safeUploadName(String name) {
        if (name == null || name.indexOf('\0') >= 0) return null;
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = cut >= 0 ? name.substring(cut + 1) : name;
        base = base.trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) return null;
        return base;
    }

    /**
     * Percent-encodes each segment of an absolute path so names containing spaces, '#', '?', '%'
     * or '+' survive inside an href. The server decodes the request path with URLDecoder, which
     * would otherwise turn a literal '+' into a space.
     */
    public static String encodePath(String path) {
        if (path == null) return "";
        String[] segments = path.split("/", -1);
        StringBuilder sb = new StringBuilder(path.length() + 16);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('/');
            try {
                sb.append(URLEncoder.encode(segments[i], "UTF-8").replace("+", "%20"));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e); // UTF-8 is always supported
            }
        }
        return sb.toString();
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Makes a file name safe inside a Content-Disposition header (no quotes, CR/LF or control chars). */
    public static String headerSafeFileName(String name) {
        if (name == null) return "download";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '"' || c == '\\') sb.append('_');
            else sb.append(c);
        }
        return sb.length() == 0 ? "download" : sb.toString();
    }
}
