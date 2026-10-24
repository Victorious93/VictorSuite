package com.termux.zerocore.remotecontrol

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class ConfigBackupTest {
    private val termuxProps = "# comment\nextra-keys = [['ESC','TAB']]\nbell-character=vibrate\nallow-external-apps=false\n"
    private val dangerousFiles = mapOf(
        NodeConfigBackup.TERMUX_PROPERTIES to termuxProps,
        NodeConfigBackup.COLORS_PROPERTIES to "background=#000000\n",
        ".bash_history" to "curl -H 'Authorization: Bearer HISTORY_SECRET_1' https://x",
        ".zsh_history" to ": 1:0;echo HISTORY_SECRET_2",
        ".ssh/id_ed25519" to "-----BEGIN OPENSSH PRIVATE KEY-----\nSSH_SECRET_3\n-----END OPENSSH PRIVATE KEY-----",
        ".ssh/authorized_keys" to "ssh-ed25519 AAAA SSH_SECRET_4",
        ".env" to "OPENAI_API_KEY=ENV_SECRET_5",
        ".bashrc" to "export ANTHROPIC_API_KEY=ENV_SECRET_6",
        ".termux/termux.env" to "GITHUB_TOKEN=ENV_SECRET_7",
    )

    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun clearBundle(payload: JsonObject, source: String = Fx.NODE_ID, pkg: String = NodeConfigBackup.NODE_PACKAGE) = JsonObject().apply {
        addProperty("version", 1)
        addProperty("createdAtMs", 1L)
        addProperty("sourceNodeRef", source)
        addProperty("wrapping", "none")
        add("entries", com.google.gson.JsonArray().also { a ->
            a.add(JsonObject().apply {
                addProperty("packageName", pkg)
                addProperty("schemaVersion", 1)
                addProperty("clearPayload", payload.toString())
            })
        })
    }.toString()

    private fun payload(externalControl: Boolean = true, extraProps: Map<String, String> = emptyMap()) = JsonObject().apply {
        add("termuxProperties", JsonObject().apply {
            addProperty("bell-character", "beep")
            addProperty("allow-external-apps", "true")
            addProperty("use-black-ui", "true")
            extraProps.forEach { (k, v) -> addProperty(k, v) }
        })
        add("remoteControl", JsonObject().apply {
            addProperty("externalControlEnabled", externalControl)
            add("pairings", com.google.gson.JsonParser.parseString("""[{"sourceNodeRef":"controller","packageName":"ai.droidcommand.app"},{"sourceNodeRef":"victorlaunch","packageName":"com.victorious.launch"}]"""))
        })
    }

    // --- export ---

    @Test
    fun exportExcludesHistoryEnvSecretsAndSshKeys() {
        val src = FakeConfigSource(dangerousFiles, Fx.pairedConfig())
        val bundle = NodeConfigBackup.export(src, Fx.NODE_ID, 5L, aead = null, clearPayload = true)
        for (i in 1..7) assertFalse("leaked secret $i: $bundle", bundle.contains("SECRET_$i"))
        listOf("bash_history", "zsh_history", "id_ed25519", "authorized_keys", "OPENAI", "ANTHROPIC", "GITHUB_TOKEN", "BEGIN").forEach {
            assertFalse("leaked '$it'", bundle.contains(it))
        }
        val b = Fx.json(bundle)
        assertEquals(1, b.get("version").asInt)
        assertEquals("none", b.get("wrapping").asString)
        assertEquals(Fx.NODE_ID, b.get("sourceNodeRef").asString)
        val entry = b.getAsJsonArray("entries")[0].asJsonObject
        assertEquals("com.victorious.suite", entry.get("packageName").asString)
        val p = Fx.json(entry.get("clearPayload").asString)
        assertEquals("vibrate", p.getAsJsonObject("termuxProperties").get("bell-character").asString)
        assertEquals("#000000", p.getAsJsonObject("colorsProperties").get("background").asString)
    }

    @Test
    fun onlyAllowListedPathsAreExportable() {
        assertTrue(NodeConfigBackup.isExportable(".termux/termux.properties"))
        listOf(".bash_history", ".ssh/id_rsa", ".env", ".bashrc", ".termux/termux.env", ".termux/shell_history").forEach {
            assertFalse(it, NodeConfigBackup.isExportable(it))
        }
    }

    @Test
    fun exportRefusesSecretNamedPropertyRatherThanStripping() {
        val src = FakeConfigSource(mapOf(NodeConfigBackup.TERMUX_PROPERTIES to "api_key=abc\n"))
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { NodeConfigBackup.export(src, Fx.NODE_ID, 1L, null, clearPayload = true) }
        val spoof = FakeConfigSource(mapOf(NodeConfigBackup.TERMUX_PROPERTIES to "twilioAccountSid=AC1\n"))
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { NodeConfigBackup.export(spoof, Fx.NODE_ID, 1L, null, clearPayload = true) }
    }

    @Test
    fun encryptedExportRoundTripsAndBindsAad() {
        val k = key()
        val src = FakeConfigSource(dangerousFiles, Fx.pairedConfig())
        val bundle = NodeConfigBackup.export(src, Fx.NODE_ID, 5L, JceFleetBackupAead(k), clearPayload = false)
        assertFalse(bundle.contains("vibrate"))
        val b = Fx.json(bundle)
        assertEquals("raw-aes-256-gcm", b.get("wrapping").asString)
        assertTrue(b.getAsJsonArray("entries")[0].asJsonObject.get("ciphertext").asString.startsWith("v1."))
        val report = NodeConfigBackup.import(bundle, FakeConfigSource(), RecordingSink(), JceFleetBackupAead(k), BackupConflictPolicy.SKIP, dryRun = true, confirmed = false)
        assertTrue(report.added.contains("termuxProperties:bell-character"))
        // Wrong key or tampered exporter id -> fail closed as internal (tamper).
        Fx.expectCategory(RemoteErrorCategory.INTERNAL) {
            NodeConfigBackup.import(bundle, FakeConfigSource(), RecordingSink(), JceFleetBackupAead(key()), BackupConflictPolicy.SKIP, true, false)
        }
        val tampered = bundle.replace(Fx.NODE_ID, "victorsuite-" + "f".repeat(32))
        Fx.expectCategory(RemoteErrorCategory.INTERNAL) {
            NodeConfigBackup.import(tampered, FakeConfigSource(), RecordingSink(), JceFleetBackupAead(k), BackupConflictPolicy.SKIP, true, false)
        }
    }

    @Test
    fun encryptedExportWithoutKeyIsRefused() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { NodeConfigBackup.export(FakeConfigSource(), Fx.NODE_ID, 1L, null, clearPayload = false) }
    }

    // --- import ---

    @Test
    fun importDryRunWritesNothing() {
        val sink = RecordingSink()
        val r = NodeConfigBackup.import(clearBundle(payload()), FakeConfigSource(), sink, null, BackupConflictPolicy.SKIP, dryRun = true, confirmed = false)
        assertTrue(r.dryRun)
        assertFalse(r.applied)
        assertEquals(0, sink.writes)
        assertTrue(r.added.contains("pairing:controller"))
    }

    @Test
    fun importWithoutConfirmationIsConfirmationRequired() {
        val sink = RecordingSink()
        Fx.expectCategory(RemoteErrorCategory.CONFIRMATION_REQUIRED) {
            NodeConfigBackup.import(clearBundle(payload()), FakeConfigSource(), sink, null, BackupConflictPolicy.SKIP, dryRun = false, confirmed = false)
        }
        assertEquals(0, sink.writes)
    }

    @Test
    fun importNeverEnablesExternalControlOrAllowExternalApps() {
        val sink = RecordingSink()
        val live = FakeConfigSource(mapOf(NodeConfigBackup.TERMUX_PROPERTIES to "allow-external-apps=false\nbell-character=vibrate\n"),
            RemoteControlConfig(externalControlEnabled = true, pairings = emptyList()))
        val r = NodeConfigBackup.import(clearBundle(payload(externalControl = true)), live, sink, null, BackupConflictPolicy.OVERWRITE, dryRun = false, confirmed = true)
        assertTrue(r.applied)
        assertTrue(r.externalControlForcedOff)
        assertFalse(sink.rc!!.externalControlEnabled)
        assertEquals("false", sink.termux!!["allow-external-apps"])
        assertEquals("beep", sink.termux!!["bell-character"]) // overwrite
        assertTrue(r.ignored.contains("termuxProperties:allow-external-apps"))
        assertTrue(r.ignored.contains("remoteControl:externalControlEnabled"))
        assertEquals(setOf("controller", "victorlaunch"), sink.rc!!.pairings.map { it.sourceNodeRef }.toSet())
    }

    @Test
    fun importSkipPolicyKeepsLiveValues() {
        val sink = RecordingSink()
        val live = FakeConfigSource(mapOf(NodeConfigBackup.TERMUX_PROPERTIES to "bell-character=vibrate\n"),
            RemoteControlConfig(false, listOf(DevicePairing("controller", "ai.droidcommand.app", "AA"))))
        val r = NodeConfigBackup.import(clearBundle(payload()), live, sink, null, BackupConflictPolicy.SKIP, dryRun = false, confirmed = true)
        assertEquals("vibrate", sink.termux!!["bell-character"])
        assertEquals("true", sink.termux!!["use-black-ui"])
        assertEquals("AA", sink.rc!!.pairings.single { it.sourceNodeRef == "controller" }.certSha256)
        assertTrue(r.skipped.contains("termuxProperties:bell-character"))
        assertTrue(r.skipped.contains("pairing:controller"))
        assertTrue(r.added.contains("pairing:victorlaunch"))
    }

    @Test
    fun importRejectsBadBundles() {
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { NodeConfigBackup.decodeBundle(clearBundle(payload()).replace("\"version\":1", "\"version\":2")) }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { NodeConfigBackup.decodeBundle(clearBundle(payload()).replace("\"none\"", "\"rot13\"")) }
        val dup = Fx.json(clearBundle(payload()))
        dup.getAsJsonArray("entries").add(dup.getAsJsonArray("entries")[0])
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) { NodeConfigBackup.decodeBundle(dup.toString()) }
        Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
            NodeConfigBackup.decodeBundle(clearBundle(payload()).replace("\"wrapping\":\"none\"", "\"wrapping\":\"raw-aes-256-gcm\""))
        }
        Fx.expectCategory(RemoteErrorCategory.NOT_FOUND) {
            NodeConfigBackup.import(clearBundle(payload(), pkg = "com.other"), FakeConfigSource(), RecordingSink(), null, BackupConflictPolicy.SKIP, true, false)
        }
    }

    @Test
    fun importRefusesSecretsHistoryAndKeys() {
        listOf(
            mapOf("api_token" to "x"),
            mapOf("shell-history" to "x"),
            mapOf("ssh-key" to "x"),
            mapOf("note" to "-----BEGIN RSA PRIVATE KEY-----"),
            mapOf("twilio-sid" to "x"),
        ).forEach { extra ->
            Fx.expectCategory(RemoteErrorCategory.INVALID_PARAMETERS) {
                NodeConfigBackup.import(clearBundle(payload(extraProps = extra)), FakeConfigSource(), RecordingSink(), null, BackupConflictPolicy.SKIP, true, false)
            }
        }
    }

    @Test
    fun localConfigImportViaDispatcherSupportsDryRunAndRequiresConfirm() {
        val sink = RecordingSink()
        val d = Fx.dispatcher(sink = sink)
        val base = { dry: Boolean ->
            FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_CONFIG_IMPORT, actionRef = null) {
                it.add("params", JsonObject().apply {
                    addProperty("bundle", clearBundle(payload()))
                    if (dry) addProperty("dryRun", "true")
                })
            })
        }
        val dry = d.handleLocal(base(true), confirmed = false)
        assertTrue(dry.toJson(), dry.ok)
        assertTrue(dry.result!!.get("dryRun").asBoolean)
        assertEquals(0, sink.writes)
        Fx.assertError(d.handleLocal(base(false), confirmed = false), RemoteErrorCategory.CONFIRMATION_REQUIRED)
        assertEquals(0, sink.writes)
        val applied = d.handleLocal(base(false), confirmed = true)
        assertTrue(applied.toJson(), applied.ok)
        assertFalse(sink.rc!!.externalControlEnabled)
    }

    @Test
    fun localConfigExportViaDispatcher() {
        val d = Fx.dispatcher(source = FakeConfigSource(dangerousFiles, Fx.pairedConfig()))
        val e = FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_CONFIG_EXPORT, actionRef = null) {
            it.add("params", Fx.json("""{"clearPayload":"true"}"""))
        })
        val r = d.handleLocal(e, confirmed = false)
        assertTrue(r.toJson(), r.ok)
        assertFalse(r.toJson().contains("SECRET_"))
        Fx.assertError(d.handleLocal(FleetEnvelopeCodec.parse(Fx.blob(operation = FleetProtocol.OP_CONFIG_EXPORT, actionRef = null) {
            it.add("params", Fx.json("""{"packageName":"com.other"}"""))
        }), false), RemoteErrorCategory.NOT_FOUND)
    }

    @Test
    fun conflictPolicyDefaultsToSkip() {
        assertEquals(BackupConflictPolicy.SKIP, BackupConflictPolicy.fromWire(null))
        assertEquals(BackupConflictPolicy.MERGE_BY_ID, BackupConflictPolicy.fromWire("merge-by-id"))
        assertNull(BackupConflictPolicy.fromWire("clobber"))
    }
}
