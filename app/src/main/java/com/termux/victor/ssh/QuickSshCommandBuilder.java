package com.termux.victor.ssh;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the quick-SSH command line that is typed into the Termux terminal.
 *
 * <p>Security properties (covered by {@code QuickSshCommandBuilderTest}):
 * <ul>
 *     <li>Host keys are always checked: {@code StrictHostKeyChecking=accept-new} with an explicit
 *     {@code UserKnownHostsFile}. A changed key makes OpenSSH refuse the connection; there is no
 *     fallback to {@code StrictHostKeyChecking=no} or {@code /dev/null}.</li>
 *     <li>A password never appears in the command. It is read by {@code sshpass -f} from a one-shot
 *     FIFO in app-private storage (see {@code PasswordFifo}); without a FIFO, ssh prompts.</li>
 *     <li>Every user-supplied value is single-quoted with {@link ShellQuote}, and the destination
 *     follows {@code --} so a value starting with {@code -} cannot become an ssh option.</li>
 * </ul>
 *
 * <p>Pure Java (no Android APIs).
 */
public final class QuickSshCommandBuilder {

    private String host;
    private int port = 22;
    private String username;
    private boolean useKey;
    private String identityFile;
    private final List<String> knownHostsFiles = new ArrayList<>();
    private String passwordFifo;

    public QuickSshCommandBuilder host(String host) {
        this.host = host;
        return this;
    }

    public QuickSshCommandBuilder port(int port) {
        this.port = port;
        return this;
    }

    public QuickSshCommandBuilder username(String username) {
        this.username = username;
        return this;
    }

    /** Key authentication with the given private key file. */
    public QuickSshCommandBuilder identityFile(String identityFile) {
        this.useKey = true;
        this.identityFile = identityFile;
        return this;
    }

    /** Known-hosts files, in order. New keys are recorded in the first one (OpenSSH behaviour). */
    public QuickSshCommandBuilder knownHostsFile(String path) {
        this.knownHostsFiles.add(path);
        return this;
    }

    /** Path of a FIFO that {@code sshpass -f} reads the password from (password mode only). */
    public QuickSshCommandBuilder passwordFifo(String passwordFifo) {
        this.passwordFifo = passwordFifo;
        return this;
    }

    public String build() {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("host is empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("invalid port");
        }
        if (knownHostsFiles.isEmpty()) {
            throw new IllegalStateException("a known_hosts file is required");
        }
        StringBuilder sb = new StringBuilder();
        boolean viaSshpass = !useKey && passwordFifo != null && !passwordFifo.isEmpty();
        if (viaSshpass) {
            sb.append("sshpass -f ").append(ShellQuote.quote(passwordFifo)).append(' ');
        }
        sb.append("ssh");
        sb.append(" -o StrictHostKeyChecking=accept-new");
        StringBuilder files = new StringBuilder();
        for (String f : knownHostsFiles) {
            if (f == null || f.isEmpty() || f.matches(".*\\s.*")) {
                throw new IllegalArgumentException("known_hosts path must not be empty or contain whitespace");
            }
            if (files.length() > 0) files.append(' ');
            files.append(f);
        }
        sb.append(" -o ").append(ShellQuote.quote("UserKnownHostsFile=" + files));
        if (useKey) {
            if (identityFile == null || identityFile.isEmpty()) {
                throw new IllegalArgumentException("identity file is empty");
            }
            sb.append(" -o IdentitiesOnly=yes -i ").append(ShellQuote.quote(identityFile));
        } else if (viaSshpass) {
            // sshpass answers one prompt; fail cleanly instead of looping on a wrong password.
            sb.append(" -o NumberOfPasswordPrompts=1");
        }
        sb.append(" -p ").append(port);
        String destination = (username == null || username.isEmpty()) ? host : username + "@" + host;
        sb.append(" -- ").append(ShellQuote.quote(destination));
        return sb.toString();
    }
}
