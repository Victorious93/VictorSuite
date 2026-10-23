package com.termux.zerocore.remotecontrol

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.StringReader
import java.util.Properties

/** Victorious Framework: tests for config.import property-injection and multi-line round-trip. */
class ConfigPropertiesSafetyTest {
    private fun bundle(termux: JsonObject) = JsonObject().apply {
        addProperty("version", 1)
        addProperty("createdAtMs", 1L)
        addProperty("sourceNodeRef", Fx.NODE_ID)
        addProperty("wrapping", "none")
        add("entries", com.google.gson.JsonArray().also { a ->
            a.add(JsonObject().apply {
                addProperty("packageName", NodeConfigBackup.NODE_PACKAGE)
                addProperty("schemaVersion", 1)
                addProperty("clearPayload", JsonObject().apply { add("termuxProperties", termux) }.toString())
            })
        })
    }.toString()

    private fun import(termux: JsonObject, live: String): String {
        val sink = RecordingSink()
        val src = FakeConfigSource(mapOf(NodeConfigBackup.TERMUX_PROPERTIES to live))
        NodeConfigBackup.import(bundle(termux), src, sink, null, BackupConflictPolicy.SKIP, dryRun = false, confirmed = true)
        return NodeConfigBackup.renderProperties(sink.termux!!, "t")
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("expected RemoteControlException")
        } catch (e: RemoteControlException) {
            assertEquals(RemoteErrorCategory.INVALID_PARAMETERS, e.category)
        }
    }

    private fun load(text: String) = Properties().apply { load(StringReader(text)) }

    @Test
    fun leadingSpaceKeyCannotSetAllowExternalApps() {
        val t = JsonObject().apply { addProperty(" allow-external-apps", "true") }
        assertRejected { import(t, "bell-character=vibrate\n") }
    }

    @Test
    fun newlineInValueCannotInjectAllowExternalApps() {
        val t = JsonObject().apply { addProperty("harmless", "x\nallow-external-apps=true") }
        assertRejected { import(t, "bell-character=vibrate\n") }
    }

    @Test
    fun separatorInKeyIsRejected() {
        assertRejected { import(JsonObject().apply { addProperty("a=b", "c") }, "") }
        assertRejected { import(JsonObject().apply { addProperty("a:b", "c") }, "") }
    }

    @Test
    fun renderThenLoadRoundTripsAwkwardValues() {
        val m = linkedMapOf("k1" to " lead", "k2" to "back\\slash", "k3" to "a=b:c", "k4" to "#hash", "k5" to "x\ty", "k.6" to "v")
        assertEquals(m, NodeConfigBackup.parseProperties(NodeConfigBackup.renderProperties(m, "t")))
    }

    @Test
    fun multiLineExtraKeysSurviveImport() {
        val live = "extra-keys = [ \\\n  ['ESC','TAB'] \\\n]\nbell-character=vibrate\n"
        val before = load(live).getProperty("extra-keys")
        val rendered = import(JsonObject().apply { addProperty("use-black-ui", "true") }, live)
        assertEquals(before, load(rendered).getProperty("extra-keys"))
    }
}
