package com.termux.zerocore.hak5

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time authorized-use gate for the Hak5 companion (Phase 1 / Phase 4 cross-cut).
 *
 * Mirrors the plugin framework's acknowledgeAuthorizedUse pattern: companion features
 * stay locked until the user explicitly records that they own the network/device or
 * have written permission to test it. Persisted in app-private SharedPreferences
 * (acknowledgment is not a secret; tokens live in [Hak5ConfigStore]).
 */
object Hak5AuthGate {
    private const val PREFS = "hak5_auth_gate"
    private const val KEY_ACK_AT = "authorized_use_acknowledged_at"
    private const val KEY_ACK_TEXT = "authorized_use_ack_text"

    /** Canonical acknowledgment text shown to and recorded for the user. */
    const val ACK_STATEMENT =
        "I own this network/device or have written permission to test it."

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isAcknowledged(context: Context): Boolean {
        return prefs(context).contains(KEY_ACK_AT)
    }

    fun acknowledgedAt(context: Context): Long? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_ACK_AT)) return null
        return prefs.getLong(KEY_ACK_AT, 0L)
    }

    /**
     * Records the user's acknowledgment. [ackText] must match [ACK_STATEMENT]
     * (case-sensitive) so callers cannot silently invent a weaker statement.
     */
    fun acknowledge(context: Context, ackText: String = ACK_STATEMENT): Boolean {
        if (ackText != ACK_STATEMENT) return false
        prefs(context).edit()
            .putLong(KEY_ACK_AT, System.currentTimeMillis())
            .putString(KEY_ACK_TEXT, ackText)
            .apply()
        Hak5AuditLog.log(
            context,
            event = "AUTHORIZED_USE_ACKNOWLEDGED",
            detail = "ack_recorded"
        )
        return true
    }

    /** Revoke acknowledgment (kill-switch / settings). Companion features lock again. */
    fun revoke(context: Context) {
        prefs(context).edit().clear().apply()
        Hak5AuditLog.log(context, event = "AUTHORIZED_USE_REVOKED", detail = null)
    }

    /**
     * Throws [IllegalStateException] if the gate is not acknowledged.
     * Call at the start of any companion action that talks to a device.
     */
    fun requireAcknowledged(context: Context) {
        if (!isAcknowledged(context)) {
            throw IllegalStateException(
                "Hak5 companion is locked until authorized use is acknowledged"
            )
        }
    }
}
