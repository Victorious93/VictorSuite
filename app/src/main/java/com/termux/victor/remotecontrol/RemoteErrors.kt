package com.termux.victor.remotecontrol

/**
 * Wire error categories for the fleet remote-control envelope v1
 * (BRIDGE_PROTOCOL 1.1). Names are the frozen snake_case set from
 * droidcommand-AI `docs/specs/REMOTE_CONTROL.md` §3.
 *
 * Pure JVM: no Android imports in this package except `android/`.
 */
enum class RemoteErrorCategory(val wire: String) {
    INVALID_PARAMETERS("invalid_parameters"),
    NOT_PAIRED("not_paired"),
    NOT_FOUND("not_found"),
    INTERNAL("internal"),
    UNAUTHORIZED("unauthorized"),
    REPLAY_DETECTED("replay_detected"),
    EXPIRED("expired"),
    EXTERNAL_CONTROL_DISABLED("external_control_disabled"),
    CONFIRMATION_REQUIRED("confirmation_required"),
    UNSUPPORTED_OP("unsupported_op"),
    CAPABILITY_OFF("capability_off"),
    ;

    companion object {
        fun fromWire(value: String?): RemoteErrorCategory? = entries.firstOrNull { it.wire == value }
    }
}

/** Thrown anywhere in the pipeline; the dispatcher turns it into an error response. */
class RemoteControlException(
    val category: RemoteErrorCategory,
    message: String,
) : RuntimeException(message)

internal fun invalid(message: String): Nothing =
    throw RemoteControlException(RemoteErrorCategory.INVALID_PARAMETERS, message)

/** Injectable wall clock (epoch ms) so replay / expiry logic is unit-testable. */
fun interface RemoteClock {
    fun nowMs(): Long

    companion object {
        val SYSTEM: RemoteClock = RemoteClock { System.currentTimeMillis() }
    }
}
