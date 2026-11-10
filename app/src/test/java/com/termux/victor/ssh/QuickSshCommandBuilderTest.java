package com.termux.victor.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuickSshCommandBuilderTest {

    private static final String KH = "/data/data/com.termux/files/victor-ssh/known_hosts/abc";
    private static final String UKH = "/data/data/com.termux/files/home/.ssh/known_hosts";

    private static QuickSshCommandBuilder base() {
        return new QuickSshCommandBuilder().host("192.168.1.5").port(22).username("don")
            .knownHostsFile(KH).knownHostsFile(UKH);
    }

    @Test
    public void passwordModeUsesFifoAndPinsHostKey() {
        String cmd = base().passwordFifo("/data/data/com.termux/cache/victor-ssh-pass/p-1").build();
        assertEquals("sshpass -f '/data/data/com.termux/cache/victor-ssh-pass/p-1' ssh"
            + " -o StrictHostKeyChecking=accept-new"
            + " -o 'UserKnownHostsFile=" + KH + " " + UKH + "'"
            + " -o NumberOfPasswordPrompts=1 -p 22 -- 'don@192.168.1.5'", cmd);
    }

    @Test
    public void neverDisablesHostKeyChecking() {
        String[] cmds = {
            base().build(),
            base().passwordFifo("/f").build(),
            base().identityFile("/k.key").build(),
        };
        for (String cmd : cmds) {
            assertFalse(cmd, cmd.contains("StrictHostKeyChecking=no"));
            assertFalse(cmd, cmd.contains("/dev/null"));
            assertTrue(cmd, cmd.contains("StrictHostKeyChecking=accept-new"));
            assertTrue(cmd, cmd.contains("UserKnownHostsFile=" + KH));
        }
    }

    @Test
    public void passwordNeverInCommand() {
        // The builder has no way to accept a password; only a FIFO path.
        String cmd = base().passwordFifo("/f").build();
        assertFalse(cmd.contains("sshpass -p"));
        assertFalse(cmd.contains("SSHPASS="));
        assertTrue(cmd.startsWith("sshpass -f '/f' "));
    }

    @Test
    public void withoutFifoSshPrompts() {
        String cmd = base().build();
        assertFalse(cmd.contains("sshpass"));
        assertTrue(cmd.startsWith("ssh "));
    }

    @Test
    public void keyMode() {
        String cmd = base().identityFile("/data/data/com.termux/files/home/.ssh/my_pc.key")
            .passwordFifo("/ignored").build();
        assertFalse(cmd.contains("sshpass"));
        assertTrue(cmd.contains(" -o IdentitiesOnly=yes -i '/data/data/com.termux/files/home/.ssh/my_pc.key'"));
        assertTrue(cmd.endsWith(" -p 22 -- 'don@192.168.1.5'"));
    }

    @Test
    public void hostileUsernameIsQuotedAndCannotBeAnOption() {
        String cmd = base().username("-oProxyCommand=touch /tmp/x;'$(id)").port(2222).build();
        assertTrue(cmd, cmd.endsWith(" -p 2222 -- '-oProxyCommand=touch /tmp/x;'\\''$(id)@192.168.1.5'"));
    }

    @Test
    public void emptyUsernameUsesHostOnly() {
        assertTrue(base().username("").build().endsWith(" -- '192.168.1.5'"));
    }

    @Test(expected = IllegalStateException.class)
    public void requiresKnownHostsFile() {
        new QuickSshCommandBuilder().host("h").username("u").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadPort() {
        base().port(0).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNewlineInUsername() {
        base().username("root\nrm -rf ~").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWhitespaceInKnownHostsPath() {
        new QuickSshCommandBuilder().host("h").knownHostsFile("/a b").build();
    }
}
