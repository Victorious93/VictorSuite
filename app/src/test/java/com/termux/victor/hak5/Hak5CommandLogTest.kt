package com.termux.zerocore.hak5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Hak5CommandLogTest {

    private fun tempDir(): File = createTempDir(prefix = "hak5log")

    private fun sampleEntry(action: String = "enablePineAP") = Hak5CommandLog.entry(
        kind = "module",
        target = "PineAP",
        action = action,
        severity = Hak5ActionSeverity.POTENTIALLY_INVASIVE,
        params = "ssid=demo",
        executed = true,
        success = true,
        httpStatus = 200,
        responseSnippet = """{"success":true,"apiToken":"super-secret"}"""
    )

    @Test
    fun append_and_read_persists() {
        val dir = tempDir()
        try {
            Hak5CommandLog.append(dir, sampleEntry("a"))
            Hak5CommandLog.append(dir, sampleEntry("b"))
            Hak5CommandLog.append(dir, sampleEntry("c"))
            val entries = Hak5CommandLog.read(dir)
            assertEquals(3, entries.size)
            assertEquals("a", entries[0].action)
            assertEquals("c", entries[2].action)
            assertTrue(entries[0].executed)
            assertEquals(200, entries[0].httpStatus)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun append_redactsTokenInResponseSnippet() {
        val dir = tempDir()
        try {
            Hak5CommandLog.append(dir, sampleEntry())
            val raw = Hak5CommandLog.readRaw(dir)
            assertEquals(1, raw.size)
            assertFalse(raw[0].contains("super-secret"))
            assertTrue(raw[0].contains("***"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun append_redactsTokenInParams() {
        val dir = tempDir()
        try {
            val entry = Hak5CommandLog.entry(
                kind = "system",
                target = "setup",
                action = "setApiToken",
                severity = Hak5ActionSeverity.CONFIG,
                params = "apiToken=leaked-value"
            )
            Hak5CommandLog.append(dir, entry)
            val raw = Hak5CommandLog.readRaw(dir)
            assertFalse(raw[0].contains("leaked-value"))
            assertTrue(raw[0].contains("***"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blockedEntry_recordedWithReason() {
        val dir = tempDir()
        try {
            Hak5CommandLog.append(
                dir,
                Hak5CommandLog.entry(
                    kind = "module",
                    target = "Evil",
                    action = "run",
                    severity = Hak5ActionSeverity.POTENTIALLY_INVASIVE,
                    executed = false,
                    blocked = true,
                    blockReason = "Per-action confirmation required"
                )
            )
            val entries = Hak5CommandLog.read(dir)
            assertEquals(1, entries.size)
            assertTrue(entries[0].blocked)
            assertFalse(entries[0].executed)
            assertTrue(entries[0].blockReason!!.contains("confirmation"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun clear_removesLog() {
        val dir = tempDir()
        try {
            Hak5CommandLog.append(dir, sampleEntry())
            assertEquals(1, Hak5CommandLog.read(dir).size)
            Hak5CommandLog.clear(dir)
            assertEquals(0, Hak5CommandLog.read(dir).size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
