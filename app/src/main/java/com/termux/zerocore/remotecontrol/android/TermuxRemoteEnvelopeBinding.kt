package com.termux.zerocore.remotecontrol.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.StatFs
import com.termux.shared.android.PackageUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.plugins.TermuxPluginUtils
import com.termux.zerocore.remotecontrol.ContainerInfo
import com.termux.zerocore.remotecontrol.ContainerSummary
import com.termux.zerocore.remotecontrol.FleetProtocol
import com.termux.zerocore.remotecontrol.NodeConfigBackup
import com.termux.zerocore.remotecontrol.NodeConfigSink
import com.termux.zerocore.remotecontrol.NodeConfigSource
import com.termux.zerocore.remotecontrol.NodeStatusSnapshot
import com.termux.zerocore.remotecontrol.NodeStatusSnapshotCodec
import com.termux.zerocore.remotecontrol.RemoteClock
import com.termux.zerocore.remotecontrol.RemoteControlConfig
import com.termux.zerocore.remotecontrol.RemoteEnvelopeDispatcher
import com.termux.zerocore.remotecontrol.RemoteErrorCategory
import com.termux.zerocore.remotecontrol.RemoteResponse
import com.termux.zerocore.remotecontrol.ReplayGuard
import com.termux.zerocore.remotecontrol.StatusTrayState
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * TERMUX_IPC binding: one JSON-blob extra ([FleetProtocol.TERMUX_ENVELOPE_EXTRA]) on
 * `com.termux.RUN_COMMAND`. Thin layer: authenticate the caller, hand the raw blob to
 * [RemoteEnvelopeDispatcher], and send the response back. Never executes a shell command.
 *
 * Caller identity (never from the envelope):
 *  - the manifest already requires `com.termux.permission.RUN_COMMAND` to reach the service;
 *  - `allow-external-apps=true` in termux.properties (the existing Termux allow-list policy);
 *  - the calling package is the **creator of the result PendingIntent** (system-attested,
 *    [PendingIntent.getCreatorPackage]) and must still hold RUN_COMMAND and own the creator uid;
 *  - that package must have a pairing row; its `sourceNodeRef` comes from the row.
 *
 * Requests without a result PendingIntent cannot be authenticated and are dropped
 * (there is also nowhere to send a response).
 */
object TermuxRemoteEnvelopeBinding {
    private const val LOG_TAG = "RemoteEnvelope"

    @Volatile
    private var dispatcher: RemoteEnvelopeDispatcher? = null
    private val replayGuard = ReplayGuard(RemoteClock.SYSTEM)

    fun isEnvelopeIntent(intent: Intent?): Boolean = intent?.hasExtra(FleetProtocol.TERMUX_ENVELOPE_EXTRA) == true

    /**
     * Single background thread for envelope work: auth + dispatch (the status op scans /proc)
     * must not run on the main thread from onStartCommand. One thread keeps requests ordered,
     * so replay-guard and config import/export behave exactly as when they ran inline.
     */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RemoteEnvelope").apply { isDaemon = true }
    }

    /**
     * Handles the envelope intent. Always consumes it (never falls through to command execution).
     * Extras are read here; authentication, dispatch and the PendingIntent reply run on [worker].
     */
    fun handle(context: Context, intent: Intent) {
        val app = context.applicationContext
        val raw = try { intent.getStringExtra(FleetProtocol.TERMUX_ENVELOPE_EXTRA) } catch (e: Exception) { null }
        val pending: PendingIntent? = try {
            intent.getParcelableExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT)
        } catch (e: Exception) { null }
        if (pending == null) {
            Logger.logError(LOG_TAG, "Dropped remote envelope: no result PendingIntent, caller cannot be authenticated")
            return
        }
        try {
            worker.execute {
                // An escaping exception would kill the worker thread and crash the app process.
                try { process(app, pending, raw) } catch (t: Throwable) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Remote envelope worker error", t)
                }
            }
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Could not queue remote envelope: ${e.message}")
        }
    }

    /** Runs on [worker]. Same logic as before, just off the main thread. */
    internal fun process(app: Context, pending: PendingIntent, raw: String?) {
        val response = try {
            authenticateAndDispatch(app, pending, raw)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Remote envelope failed", e)
            RemoteResponse(null, RemoteControlSettings.nodeId(app), ok = false, errorCategory = RemoteErrorCategory.INTERNAL, errorMessage = "internal error")
        }
        try {
            pending.send(app, 0, Intent().putExtra(FleetProtocol.TERMUX_RESPONSE_EXTRA, response.toJson()))
        } catch (e: Exception) {
            Logger.logError(LOG_TAG, "Could not deliver remote envelope response: ${e.message}")
        }
    }

    private fun authenticateAndDispatch(app: Context, pending: PendingIntent, raw: String?): RemoteResponse {
        val nodeId = RemoteControlSettings.nodeId(app)
        val creatorPackage = pending.creatorPackage
        val creatorUid = pending.creatorUid
        val pm = app.packageManager
        val ownsUid = creatorPackage != null && pm.getPackagesForUid(creatorUid)?.contains(creatorPackage) == true
        val holdsPermission = creatorPackage != null &&
            pm.checkPermission(TermuxConstants.PERMISSION_RUN_COMMAND, creatorPackage) == PackageManager.PERMISSION_GRANTED
        if (!ownsUid || !holdsPermission) {
            return RemoteResponse(null, nodeId, ok = false, errorCategory = RemoteErrorCategory.UNAUTHORIZED,
                errorMessage = "caller must hold ${TermuxConstants.PERMISSION_RUN_COMMAND}")
        }
        if (TermuxPluginUtils.checkIfAllowExternalAppsPolicyIsViolated(app, LOG_TAG) != null) {
            return RemoteResponse(null, nodeId, ok = false, errorCategory = RemoteErrorCategory.UNAUTHORIZED,
                errorMessage = "allow-external-apps is not enabled in termux.properties")
        }
        val cert = PackageUtils.getSigningCertificateSHA256DigestForPackage(app, creatorPackage!!)
        Logger.logInfo(LOG_TAG, "Remote envelope from $creatorPackage")
        return dispatcher(app).handleRemote(creatorPackage, cert, raw)
    }

    @Synchronized
    fun dispatcher(context: Context): RemoteEnvelopeDispatcher {
        dispatcher?.let { return it }
        val app = context.applicationContext
        val d = RemoteEnvelopeDispatcher(
            nodeId = RemoteControlSettings.nodeId(app),
            nodePackage = TermuxConstants.TERMUX_PACKAGE_NAME,
            config = { RemoteControlSettings.load(app) },
            replayGuard = replayGuard,
            clock = RemoteClock.SYSTEM,
            statusProvider = { now -> AndroidNodeStatus.snapshot(app, now) },
            configSource = AndroidNodeConfig.Source(app),
            configSink = AndroidNodeConfig.Sink(app),
        )
        dispatcher = d
        return d
    }
}

/** Builds this node's STATUS-TRAY snapshot from on-disk facts only (no history, no env). */
object AndroidNodeStatus {
    private const val LOW_STORAGE_BYTES = 200L * 1024 * 1024

    fun snapshot(context: Context, nowMs: Long): NodeStatusSnapshot {
        val prefix = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        val bootstrapOk = File(prefix, "bin/sh").exists()
        val free = try { StatFs(TermuxConstants.TERMUX_FILES_DIR_PATH).availableBytes } catch (e: Exception) { -1L }
        val (health, detail) = when {
            !bootstrapOk -> StatusTrayState.DOWN to "bootstrap missing"
            free in 0 until LOW_STORAGE_BYTES -> StatusTrayState.DEGRADED to "storage low"
            free < 0 -> StatusTrayState.OK to "bootstrap ok"
            else -> StatusTrayState.OK to "bootstrap ok, storage ok"
        }
        val (cState, cDetail) = ContainerSummary.summarize(containers())
        return NodeStatusSnapshotCodec.buildTermuxSnapshot(
            nodeId = RemoteControlSettings.nodeId(context),
            packageName = TermuxConstants.TERMUX_PACKAGE_NAME,
            updatedAtMs = nowMs,
            nodeHealth = health,
            nodeHealthDetail = detail,
            containerState = cState,
            containerDetail = cDetail,
        )
    }

    /**
     * proot-distro containers: installed = dirs under `$PREFIX/var/lib/proot-distro/installed-rootfs`.
     * Running = some process of ours has `proot` in argv[0] and the rootfs path in its cmdline.
     * Cmdlines are only matched, never reported.
     */
    private fun containers(): List<ContainerInfo>? {
        val root = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "var/lib/proot-distro/installed-rootfs")
        if (!root.isDirectory) return emptyList()
        val names = root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: return null
        val cmdlines = try {
            File("/proc").listFiles()?.filter { it.name.all(Char::isDigit) }?.mapNotNull {
                try { File(it, "cmdline").readText().replace('\u0000', ' ') } catch (e: Exception) { null }
            }?.filter { it.substringBefore(' ').endsWith("proot") } ?: emptyList()
        } catch (e: Exception) { emptyList() }
        return names.map { n -> ContainerInfo(n, cmdlines.any { it.contains("installed-rootfs/$n") }) }
    }
}

/** Reads ONLY the allow-listed config files; writes imported values with a `.bak` copy first. */
object AndroidNodeConfig {
    class Source(private val context: Context) : NodeConfigSource {
        override fun files(): Map<String, String> {
            val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
            return NodeConfigBackup.EXPORT_ALLOWLIST.mapNotNull { rel ->
                val f = File(home, rel)
                if (f.isFile && f.length() < 256 * 1024) rel to f.readText() else null
            }.toMap()
        }

        override fun remoteControlConfig(): RemoteControlConfig = RemoteControlSettings.load(context)
    }

    class Sink(private val context: Context) : NodeConfigSink {
        override fun writeTermuxProperties(values: Map<String, String>) =
            write(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH, values)

        override fun writeColorsProperties(values: Map<String, String>) =
            write(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE_PATH, values)

        override fun saveRemoteControlConfig(config: RemoteControlConfig) =
            RemoteControlSettings.saveImported(context, config)

        private fun write(path: String, values: Map<String, String>) {
            val f = File(path)
            f.parentFile?.mkdirs()
            if (f.exists()) f.copyTo(File("$path.bak-${System.currentTimeMillis()}"), overwrite = true)
            // Write a sibling temp file then rename, so a crash mid-write cannot leave a truncated config.
            val text = NodeConfigBackup.renderProperties(values, "Imported by VictorSuite config.import")
            val tmp = File("$path.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
        }
    }
}
