package com.termux.victor.config.ztcommand

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
class ZTCommandAuthTest {
    @Test
    fun correctTokenReturnsCommand() {
        assertEquals("toast hello", ZTCommandAuth.authenticate("tok123 toast hello", "tok123"))
        assertEquals("", ZTCommandAuth.authenticate("tok123", "tok123"))
        assertEquals("ln a b", ZTCommandAuth.authenticate("  tok123   ln a b  ", "tok123"))
    }

    @Test
    fun missingOrWrongTokenIsRejected() {
        assertNull(ZTCommandAuth.authenticate(null, "tok123"))
        assertNull(ZTCommandAuth.authenticate("", "tok123"))
        assertNull(ZTCommandAuth.authenticate("ln a b", "tok123"))
        assertNull(ZTCommandAuth.authenticate("tok124 ln a b", "tok123"))
        assertNull(ZTCommandAuth.authenticate("tok12 ln a b", "tok123"))
    }

    @Test
    fun emptyExpectedTokenNeverAuthenticates() {
        assertNull(ZTCommandAuth.authenticate("anything", ""))
        assertNull(ZTCommandAuth.authenticate("anything", null))
    }

    @Test
    fun legacyUnauthenticatedCommandsAreRefused() {
        // What the old `zt` helper sent: just the command.
        assertNull(ZTCommandAuth.authenticate("ln /sdcard/x /data/data/com.termux/files/usr/etc/profile.d/x.sh", "a".repeat(64)))
    }

    @Test
    fun boundedLineReadsNormalLinesAndStripsCr() {
        assertEquals("hello world", ZTCommandAuth.readBoundedLine(StringReader("hello world\r\nnext"), 64))
        assertNull(ZTCommandAuth.readBoundedLine(StringReader(""), 64))
        assertEquals("no newline", ZTCommandAuth.readBoundedLine(StringReader("no newline"), 64))
    }

    @Test
    fun oversizedLineIsRejected() {
        try {
            ZTCommandAuth.readBoundedLine(StringReader("x".repeat(100)), 10)
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun tokenIsCreatedOnceAndReused() {
        val dir = Files.createTempDirectory("zt").toFile()
        val f = File(dir, ".termux/zt.token")
        val a = ZTCommandAuth.loadOrCreateToken(f)
        assertEquals(64, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertEquals(a, ZTCommandAuth.loadOrCreateToken(f))
        f.writeText("")
        val b = ZTCommandAuth.loadOrCreateToken(f)
        assertNotEquals(a, b)
        assertEquals(b, f.readText().trim())
        dir.deleteRecursively()
    }

    @Test
    fun jsonEscapesQuotesAndControlChars() {
        val j = ZTCommandAuth.json(1, "bad \"quote\" \\ \n end", "t")
        assertEquals("{\"message\": \"bad \\\"quote\\\" \\\\ \\n end\",\"code\": 1,\"title\": \"t\"}", j)
        assertFalse(j.contains("\n"))
    }

    @Test
    fun tokenFileIsReadWithoutNioAndTrimmed() {
        val f = File.createTempFile("zttoken", ".token")
        f.writeText("  abc123\n\n")
        assertEquals("abc123", ZTCommandAuth.readTokenFile(f))
        assertEquals("abc123", ZTCommandAuth.loadOrCreateToken(f))
        f.writeText("")
        assertEquals("", ZTCommandAuth.readTokenFile(f))
        f.delete()
    }

    @Test
    fun oversizedTokenFileIsRejected() {
        val f = File.createTempFile("zttoken", ".token")
        f.writeText("a".repeat(ZTCommandAuth.MAX_LINE_CHARS + 10))
        try {
            ZTCommandAuth.readTokenFile(f)
            fail("expected IOException")
        } catch (expected: IOException) {
        } finally {
            f.delete()
        }
    }
}
