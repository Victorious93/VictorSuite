package com.termux.victor.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class QuickSshRedactorTest {

    @Test
    public void redactsLegacySshpassCommand() {
        String in = " clear && sshpass -p 'hunter2' ssh -o StrictHostKeyChecking=no -p 22 root@10.0.0.2";
        assertEquals(" clear && sshpass -p *** ssh -o StrictHostKeyChecking=no -p 22 root@10.0.0.2",
            QuickSshRedactor.redact(in));
    }

    @Test
    public void redactsQuotedPasswordWithEscapedQuote() {
        String out = QuickSshRedactor.redact("sshpass -p 'it'\\''s secret' ssh h");
        assertEquals("sshpass -p *** ssh h", out);
    }

    @Test
    public void redactsBareAndDoubleQuotedAndEnv() {
        assertEquals("sshpass -p *** ssh h", QuickSshRedactor.redact("sshpass -p hunter2 ssh h"));
        assertEquals("sshpass -p *** ssh h", QuickSshRedactor.redact("sshpass -p \"a b\\\"c\" ssh h"));
        assertEquals("SSHPASS=*** sshpass -e ssh h", QuickSshRedactor.redact("SSHPASS='pw' sshpass -e ssh h"));
    }

    @Test
    public void redactsJsonPasswords() {
        String out = QuickSshRedactor.redact("[{\"alias\":\"pc\",\"password\":\"p\\\"w\",\"passwordEnc\":\"ks1:a:b\"}]");
        assertEquals("[{\"alias\":\"pc\",\"password\":\"***\",\"passwordEnc\":\"***\"}]", out);
    }

    @Test
    public void leavesSafeTextAlone() {
        String cmd = "ssh -o StrictHostKeyChecking=accept-new -p 22 -- 'root@h'";
        assertEquals(cmd, QuickSshRedactor.redact(cmd));
        assertNull(QuickSshRedactor.redact(null));
    }

    @Test
    public void describeRedactsMessage() {
        String d = QuickSshRedactor.describe(new RuntimeException("bad: sshpass -p 'x' ssh"));
        assertEquals("RuntimeException: bad: sshpass -p *** ssh", d);
        assertFalse(d.contains("'x'"));
    }
}
