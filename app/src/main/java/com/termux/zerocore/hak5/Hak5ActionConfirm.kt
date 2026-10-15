package com.termux.zerocore.hak5

/**
 * Severity tags and per-action confirmation scaffolding (Phase 1 ships the types +
 * gate helpers; Phase 2 wires them to real state-changing Pineapple actions).
 *
 * Product rule: NEVER auto-attack. Every state-changing or potentially invasive
 * action needs explicit per-action confirmation after the authorized-use gate.
 */
enum class Hak5ActionSeverity {
    READ_ONLY,
    CONFIG,
    POTENTIALLY_INVASIVE
}

data class Hak5PendingAction(
    val id: String,
    val title: String,
    val summary: String,
    val severity: Hak5ActionSeverity,
    /** Candidate key like "module:Foo:bar" — must be confirmed on current firmware. */
    val capabilityKey: String? = null
)

object Hak5ActionConfirm {
    /**
     * Returns null if the action may proceed to a confirmation UI; otherwise an
     * error message explaining why it is blocked.
     */
    fun blockReason(
        authorized: Boolean,
        action: Hak5PendingAction,
        capabilities: CapabilityMap?,
        userConfirmed: Boolean
    ): String? {
        if (!authorized) {
            return "Authorized-use acknowledgment required before any Hak5 action"
        }
        if (action.severity != Hak5ActionSeverity.READ_ONLY && !userConfirmed) {
            return "Per-action confirmation required for ${action.severity.name} actions"
        }
        val key = action.capabilityKey
        if (key != null) {
            val caps = capabilities
                ?: return "No capability map — connect and probe firmware first"
            if (caps.limitedMode && action.severity != Hak5ActionSeverity.READ_ONLY) {
                return "Unknown firmware — limited mode; state-changing actions unavailable"
            }
            if (!caps.confirmedActions.contains(key)) {
                return "Action not confirmed on this firmware: $key"
            }
        }
        return null
    }

    fun mayExecute(
        authorized: Boolean,
        action: Hak5PendingAction,
        capabilities: CapabilityMap?,
        userConfirmed: Boolean
    ): Boolean = blockReason(authorized, action, capabilities, userConfirmed) == null
}
