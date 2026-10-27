package com.termux.victor.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.termux.shared.logger.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only client for the VictorLaunch fleet theme (FLEET_THEME.md, schemaVersion 1).
 *
 * - Reads `content://com.victorious.launch.theme/current` off the main thread.
 * - Before and after every query, verifies the provider is owned by `com.victorious.launch`
 *   with a pinned signing cert ([FleetThemeProviderVerifier]).
 * - Listens with a [ContentObserver] on the URI and a runtime-registered, exported receiver for
 *   `com.victorious.launch.action.FLEET_THEME_CHANGED`. Both are wake-ups only: extras are never
 *   trusted, the client just re-queries and re-verifies.
 * - Any SecurityException, missing provider, pin mismatch, parse error or `schemaVersion != 1`
 *   yields `null`, which listeners treat as "use the built-in theme".
 * - Never calls insert/update/delete on the provider.
 *
 * Only View/XML chrome (drawers, settings) consumes this; terminal colours are not touched.
 */
object FleetThemeClient {

    fun interface Listener {
        /** Main thread. [snapshot] null means: use VictorSuite's built-in theme. */
        fun onFleetThemeChanged(snapshot: FleetThemeSnapshot?)
    }

    private const val LOG_TAG = "FleetThemeClient"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fleet-theme").apply { isDaemon = true }
    }
    private val refreshQueued = AtomicBoolean(false)

    // Main-thread state.
    private val listeners = LinkedHashSet<Listener>()
    private var appContext: Context? = null
    private var observer: ContentObserver? = null
    private var receiver: BroadcastReceiver? = null

    /** Last result, main thread only. null = built-in. */
    @Volatile
    var current: FleetThemeSnapshot? = null
        private set

    /** Main thread. Starts watching on the first listener and immediately delivers [current]. */
    fun addListener(context: Context, listener: Listener) {
        if (!listeners.add(listener)) return
        if (listeners.size == 1) start(context.applicationContext)
        listener.onFleetThemeChanged(current)
        requestRefresh()
    }

    /** Main thread. Stops watching once nobody is listening. */
    fun removeListener(listener: Listener) {
        if (listeners.remove(listener) && listeners.isEmpty()) stop()
    }

    private fun start(context: Context) {
        appContext = context
        val uri = Uri.parse(FleetThemeContract.URI_CURRENT)
        val obs = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) = requestRefresh()
        }
        try {
            context.contentResolver.registerContentObserver(uri, false, obs)
            observer = obs
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "ContentObserver registration failed: ${e.javaClass.simpleName}")
        }

        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != FleetThemeContract.ACTION_FLEET_THEME_CHANGED) return
                // When the platform can tell us the sender (API 34+, sender opted in), drop
                // anything that is not VictorLaunch. Otherwise it is still only a wake-up.
                if (Build.VERSION.SDK_INT >= 34) {
                    val sender = sentFromPackage
                    if (sender != null && sender != FleetThemeContract.PUBLISHER_PACKAGE) return
                }
                requestRefresh()
            }
        }
        try {
            // Exported: VictorLaunch sends explicitly to our package from another UID (spec).
            ContextCompat.registerReceiver(
                context, rcv, IntentFilter(FleetThemeContract.ACTION_FLEET_THEME_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiver = rcv
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Receiver registration failed: ${e.javaClass.simpleName}")
        }
    }

    private fun stop() {
        val ctx = appContext ?: return
        observer?.let { runCatching { ctx.contentResolver.unregisterContentObserver(it) } }
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        observer = null
        receiver = null
    }

    /** Coalesced: at most one query queued at a time. */
    fun requestRefresh() {
        val ctx = appContext ?: return
        if (!refreshQueued.compareAndSet(false, true)) return
        executor.execute {
            refreshQueued.set(false)
            val snapshot = load(ctx)
            mainHandler.post { deliver(snapshot) }
        }
    }

    private fun deliver(snapshot: FleetThemeSnapshot?) {
        if (snapshot == current) return
        current = snapshot
        for (l in listeners.toList()) {
            try {
                l.onFleetThemeChanged(snapshot)
            } catch (e: Exception) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Listener failed", e)
            }
        }
    }

    /** Worker thread. Returns null (built-in theme) on any failure. */
    private fun load(ctx: Context): FleetThemeSnapshot? {
        FleetThemeProviderVerifier.untrustedReason(ctx)?.let {
            Logger.logVerbose(LOG_TAG, "Fleet theme not used: $it")
            return null
        }
        val row: Map<String, Any?> = try {
            ctx.contentResolver.query(Uri.parse(FleetThemeContract.URI_CURRENT), null, null, null, null)
                ?.use { readFirstRow(it) }
                ?: run {
                    Logger.logVerbose(LOG_TAG, "Fleet theme query returned no cursor")
                    return null
                }
        } catch (e: SecurityException) {
            // Our package+cert is not pinned in VictorLaunch's FleetThemeConsumers.
            Logger.logInfo(LOG_TAG, "Fleet theme refused by provider (SecurityException); using built-in theme")
            return null
        } catch (e: Exception) {
            Logger.logWarn(LOG_TAG, "Fleet theme query failed: ${e.javaClass.simpleName}")
            return null
        }
        // Re-check after the query so a package swapped in between is not trusted.
        FleetThemeProviderVerifier.untrustedReason(ctx)?.let {
            Logger.logWarn(LOG_TAG, "Fleet theme provider changed during query: $it")
            return null
        }
        return when (val r = FleetThemeParser.parse(row)) {
            is FleetThemeParseResult.Ok -> r.snapshot
            is FleetThemeParseResult.Rejected -> {
                Logger.logWarn(LOG_TAG, "Fleet theme rejected: ${r.reason}")
                null
            }
        }
    }

    private fun readFirstRow(c: Cursor): Map<String, Any?> {
        if (!c.moveToFirst()) return emptyMap()
        val out = HashMap<String, Any?>()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i) ?: continue
            out[name] = when (c.getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                Cursor.FIELD_TYPE_STRING -> c.getString(i)
                else -> null // NULL or BLOB: not part of the contract
            }
        }
        return out
    }
}
