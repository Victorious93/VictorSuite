package com.termux.victor.pc

import android.content.Context

/**
 * Records that the user read the wireless ADB / scrcpy warning. The My PC setup actions stay
 * locked until then. Bumping [WARNING_VERSION] asks everyone to read the warning again.
 * The acknowledgment is not a secret, so plain app-private SharedPreferences is enough.
 */
object MyPcAckGate {
    const val WARNING_VERSION = 1
    private const val PREFS = "my_pc_mirror_gate"
    private const val KEY_VERSION = "ack_version"
    private const val KEY_AT = "ack_at"

    /** Pure rule, tested on the JVM. */
    fun isValid(storedVersion: Int, storedAtMillis: Long): Boolean =
        storedVersion == WARNING_VERSION && storedAtMillis > 0L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAcknowledged(context: Context): Boolean {
        val p = prefs(context)
        return isValid(p.getInt(KEY_VERSION, 0), p.getLong(KEY_AT, 0L))
    }

    fun acknowledgedAt(context: Context): Long? =
        if (isAcknowledged(context)) prefs(context).getLong(KEY_AT, 0L) else null

    fun acknowledge(context: Context) {
        prefs(context).edit()
            .putInt(KEY_VERSION, WARNING_VERSION)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    fun revoke(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
