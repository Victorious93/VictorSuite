package com.termux.victor.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SSHKeyUtilsTest {

    @Test
    public void generatesEd25519WithoutEmptyPassphraseOrDelete() {
        String cmd = SSHKeyUtils.getGenerateKeyCommand("My PC");
        assertTrue(cmd, cmd.contains("ssh-keygen -t ed25519 -a 100"));
        assertTrue(cmd, cmd.contains("-f '") && cmd.contains("/.ssh/My_PC.key'"));
        assertTrue(cmd, cmd.contains("chmod 700 ") && cmd.contains("chmod 600 "));
        assertFalse(cmd, cmd.contains("-N"));
        assertFalse(cmd, cmd.contains("rsa"));
        assertFalse(cmd, cmd.contains("rm -f"));
    }

    @Test
    public void aliasIsSanitized() {
        assertTrue(SSHKeyUtils.getKeyFile("a'b;$(x)").getName().equals("a_b___x_.key"));
    }
}
