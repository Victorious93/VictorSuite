package com.termux.victor.ssh;

/**
 * POSIX shell quoting for text that is typed into the Termux terminal.
 *
 * <p>Pure Java (no Android APIs) so it can be unit tested on the JVM.
 */
public final class ShellQuote {

    private ShellQuote() {
    }

    /**
     * Wraps {@code value} in single quotes, escaping embedded single quotes as {@code '\''}.
     *
     * @throws IllegalArgumentException if {@code value} is null or contains a control character
     *                                  (for example a newline, which would submit the line early).
     */
    public static String quote(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is null");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new IllegalArgumentException("control character in shell argument");
            }
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
