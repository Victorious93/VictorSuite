package com.termux.victor.utils;

import com.termux.shared.termux.TermuxConstants;
import com.termux.victor.ssh.ShellQuote;

import java.io.File;

public class SSHKeyUtils {
    public static File getSSHDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, ".ssh");
    }

    public static String safeName(String alias) {
        return alias.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public static File getKeyFile(String alias) {
        return new File(getSSHDir(), safeName(alias) + ".key");
    }

    public static boolean isKeyExist(String alias) {
        return getKeyFile(alias).exists();
    }

    /**
     * Command (typed into the terminal; POSIX sh and fish compatible) that generates an Ed25519 key pair for {@code alias}.
     *
     * <p>Replaces the old unencrypted RSA-2048 ({@code -N ''}) key. {@code -N} is not passed, so
     * ssh-keygen prompts in the terminal for a passphrase (recommended) and the passphrase never
     * appears on the command line. {@code -a 100} hardens the passphrase KDF. The key is created
     * in the app-private Termux home ({@code ~/.ssh}, mode 0700; key 0600). An
     * existing key is no longer deleted first: ssh-keygen asks before overwriting it.
     */
    public static String getGenerateKeyCommand(String alias) {
        String keyPath = getKeyFile(alias).getAbsolutePath();
        String sshDir = ShellQuote.quote(getSSHDir().getAbsolutePath());
        return "mkdir -p " + sshDir + " && chmod 700 " + sshDir + " && "
            + "ssh-keygen -t ed25519 -a 100 -C " + ShellQuote.quote("victorsuite-" + safeName(alias))
            + " -f " + ShellQuote.quote(keyPath) + " && chmod 600 " + ShellQuote.quote(keyPath);
    }

    public static String getCatPublicKeyCommand(String alias) {
        return "cat " + ShellQuote.quote(getKeyFile(alias).getAbsolutePath() + ".pub");
    }
}
