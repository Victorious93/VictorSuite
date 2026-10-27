package com.termux.victor.http_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Victorious Framework: the HTTP file server must not leave its root. */
class HttpPathGuardTest {
    private val root = File("/data/data/com.termux/files")

    @Test
    fun plainPathsResolveUnderRoot() {
        assertEquals(File(root, "home/a.txt"), HttpPathGuard.resolveInside(root, "/home/a.txt"))
        assertEquals(root, HttpPathGuard.resolveInside(root, "/"))
        assertEquals(File(root, "home"), HttpPathGuard.resolveInside(root, "/home/./x/.."))
    }

    @Test
    fun traversalAboveRootIsRejected() {
        assertNull(HttpPathGuard.resolveInside(root, "/../shared_prefs/x.xml"))
        assertNull(HttpPathGuard.resolveInside(root, "/home/../../etc/hosts"))
        assertNull(HttpPathGuard.resolveInside(root, "../../../etc/passwd"))
        assertNull(HttpPathGuard.resolveInside(root, "/a\u0000b"))
        assertNull(HttpPathGuard.resolveInside(root, null))
    }

    @Test
    fun traversalThatStaysInsideIsFine() {
        assertEquals(File(root, "usr/bin"), HttpPathGuard.resolveInside(root, "/home/../usr/bin"))
    }

    @Test
    fun uploadNamesAreReducedToAFileName() {
        assertEquals("a.txt", HttpPathGuard.safeUploadName("a.txt"))
        assertEquals(".bashrc", HttpPathGuard.safeUploadName("../../../data/data/com.termux/files/home/.bashrc"))
        assertEquals("x.sh", HttpPathGuard.safeUploadName("C:\\temp\\x.sh"))
        assertEquals("evil", HttpPathGuard.safeUploadName("/etc/evil"))
        assertNull(HttpPathGuard.safeUploadName(".."))
        assertNull(HttpPathGuard.safeUploadName("a/.."))
        assertNull(HttpPathGuard.safeUploadName("dir/"))
        assertNull(HttpPathGuard.safeUploadName(""))
        assertNull(HttpPathGuard.safeUploadName(null))
        assertNull(HttpPathGuard.safeUploadName("a\u0000.txt"))
    }

    @Test
    fun encodedPathsRoundTripThroughUrlDecoding() {
        val names = listOf(
            "/data/data/com.termux/files/home/plain.txt",
            "/files/100%.txt",
            "/files/a#b?c.txt",
            "/files/a+b.txt",
            "/files/with space.txt",
            "/files/é中.txt",
        )
        for (n in names) {
            val encoded = HttpPathGuard.encodePath(n)
            assertFalse(encoded, encoded.contains(" ") || encoded.contains("#") || encoded.contains("?"))
            assertEquals(n, java.net.URLDecoder.decode(encoded, "UTF-8"))
        }
        assertEquals("/files/a%20b", HttpPathGuard.encodePath("/files/a b"))
        assertEquals("/files/a%2Bb", HttpPathGuard.encodePath("/files/a+b"))
        assertEquals("", HttpPathGuard.encodePath(null))
    }

    @Test
    fun htmlAndHeaderEscaping() {
        assertEquals("&lt;script&gt;&quot;x&quot;&amp;&#39;", HttpPathGuard.escapeHtml("<script>\"x\"&'"))
        val h = HttpPathGuard.headerSafeFileName("a\"b\r\nX-Evil: 1\\")
        assertFalse(h.contains("\r") || h.contains("\n") || h.contains("\""))
        assertTrue(h.startsWith("a_b"))
        assertEquals("download", HttpPathGuard.headerSafeFileName(""))
    }
}
