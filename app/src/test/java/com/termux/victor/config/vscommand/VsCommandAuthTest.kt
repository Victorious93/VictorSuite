package com.termux.victor.config.vscommand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files

/** Victorious Framework: ZT command socket auth, bounded reads and JSON escaping. */
class VsCommandAuthTest {
    @Test
    fun correctTokenReturnsCommand() {
        assertEquals("toast hello", VsCommandAuth.authenticate("tok123 toast hello", "tok123"))
        assertEquals("", VsCommandAuth.authenticate("tok123", "tok123"))
        assertEquals("ln a b", VsCommandAuth.authenticate("  tok123   ln a b  ", "tok123"))
    }

    @Test
    fun missingOrWrongTokenIsRejected() {
        assertNull(VsCommandAuth.authenticate(null, "tok123"))
        assertNull(VsCommandAuth.authenticate("", "tok123"))
        assertNull(VsCommandAuth.authenticate("ln a b", "tok123"))
        assertNull(VsCommandAuth.authenticate("tok124 ln a b", "tok123"))
        assertNull(VsCommandAuth.authenticate("tok12 ln a b", "tok123"))
    }

    @Test
    fun emptyExpectedTokenNeverAuthenticates() {
        assertNull(VsCommandAuth.authenticate("anything", ""))
        assertNull(VsCommandAuth.authenticate("anything", null))
    }

    @Test
    fun legacyUnauthenticatedCommandsAreRefused() {
        // What the old `zt` helper sent: just the command.
        assertNull(VsCommandAuth.authenticate("ln /sdcard/x /data/data/com.termux/files/usr/etc/profile.d/x.sh", "a".repeat(64)))
    }

    @Test
    fun boundedLineReadsNormalLinesAndStripsCr() {
        assertEquals("hello world", VsCommandAuth.readBoundedLine(StringReader("hello world\r\nnext"), 64))
        assertNull(VsCommandAuth.readBoundedLine(StringReader(""), 64))
        assertEquals("no newline", VsCommandAuth.readBoundedLine(StringReader("no newline"), 64))
    }

    @Test
    fun oversizedLineIsRejected() {
        try {
            VsCommandAuth.readBoundedLine(StringReader("x".repeat(100)), 10)
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun tokenIsCreatedOnceAndReused() {
        val dir = Files.createTempDirectory("zt").toFile()
        val f = File(dir, ".termux/zt.token")
        val a = VsCommandAuth.loadOrCreateToken(f)
        assertEquals(64, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertEquals(a, VsCommandAuth.loadOrCreateToken(f))
        f.writeText("")
        val b = VsCommandAuth.loadOrCreateToken(f)
        assertNotEquals(a, b)
        assertEquals(b, f.readText().trim())
        dir.deleteRecursively()
    }

    @Test
    fun jsonEscapesQuotesAndControlChars() {
        val j = VsCommandAuth.json(1, "bad \"quote\" \\ \n end", "t")
        assertEquals("{\"message\": \"bad \\\"quote\\\" \\\\ \\n end\",\"code\": 1,\"title\": \"t\"}", j)
        assertFalse(j.contains("\n"))
    }

    @Test
    fun tokenFileIsReadWithoutNioAndTrimmed() {
        val f = File.createTempFile("zttoken", ".token")
        f.writeText("  abc123\n\n")
        assertEquals("abc123", VsCommandAuth.readTokenFile(f))
        assertEquals("abc123", VsCommandAuth.loadOrCreateToken(f))
        f.writeText("")
        assertEquals("", VsCommandAuth.readTokenFile(f))
        f.delete()
    }

    @Test
    fun oversizedTokenFileIsRejected() {
        val f = File.createTempFile("zttoken", ".token")
        f.writeText("a".repeat(VsCommandAuth.MAX_LINE_CHARS + 10))
        try {
            VsCommandAuth.readTokenFile(f)
            fail("expected IOException")
        } catch (expected: IOException) {
        } finally {
            f.delete()
        }
    }
}
