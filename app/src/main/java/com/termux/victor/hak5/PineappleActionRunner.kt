package com.termux.zerocore.hak5

/**
 * Executes a selected module/action ONLY after it passes the per-action confirmation
 * gate (plan Phase 2 §4). This is the single choke point through which any
 * state-changing Pineapple call must go. It NEVER auto-runs: if the gate blocks the
 * action, no network request is made, and the block is recorded in the command log.
 *
 * Every attempt — executed or blocked — is appended to the persistent command log via
 * the injected [commandSink], with secrets redacted by [Hak5CommandLog].
 *
 * Pure logic over [PineappleClient] + a sink function — unit-testable with MockWebServer
 * and a temp-dir command log, no Android Context required.
 */
class PineappleActionRunner(
    private val client: PineappleClient = PineappleClient(),
    /** Receives one entry per attempt. Defaults to a no-op for callers that log elsewhere. */
    private val commandSink: (Hak5CommandLog.CommandLogEntry) -> Unit = {}
) {

    data class ActionOutcome(
        val executed: Boolean,
        val blocked: Boolean,
        val blockReason: String? = null,
        val success: Boolean = false,
        val httpStatus: Int? = null,
        val responseSnippet: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Gate, then (only if allowed) execute. [userConfirmed] MUST reflect the user having
     * passed the [Hak5ActionConfirm] dialog for this exact action. Read-only actions still
     * require authorization but not an explicit confirm; anything else requires both.
     */
    fun run(
        baseUrl: String,
        apiToken: String?,
        pending: Hak5PendingAction,
        request: PineappleApiRequest,
        authorized: Boolean,
        capabilities: CapabilityMap?,
        userConfirmed: Boolean
    ): ActionOutcome {
        val target = request.module ?: request.system ?: pending.title
        val kind = if (request.module != null) "module" else "system"
        val paramsSummary = summarizeParams(request)

        val blockReason = Hak5ActionConfirm.blockReason(authorized, pending, capabilities, userConfirmed)
        if (blockReason != null) {
            // Blocked BEFORE any network call — never auto-run.
            commandSink(
                Hak5CommandLog.entry(
                    kind = kind,
                    target = target,
                    action = request.action,
                    severity = pending.severity,
                    params = paramsSummary,
                    executed = false,
                    blocked = true,
                    blockReason = blockReason
                )
            )
            return ActionOutcome(executed = false, blocked = true, blockReason = blockReason)
        }

        val resp = client.postApi(baseUrl, request)
        val snippet = Hak5AuditLog.redact(resp.body)
        val outcome = ActionOutcome(
            executed = true,
            blocked = false,
            success = resp.ok,
            httpStatus = resp.httpStatus,
            responseSnippet = snippet,
            errorMessage = resp.errorMessage
        )
        commandSink(
            Hak5CommandLog.entry(
                kind = kind,
                target = target,
                action = request.action,
                severity = pending.severity,
                params = paramsSummary,
                executed = true,
                blocked = false,
                success = resp.ok,
                httpStatus = resp.httpStatus,
                responseSnippet = snippet
            )
        )
        return outcome
    }

    private fun summarizeParams(request: PineappleApiRequest): String? {
        if (request.extra.isEmpty()) return null
        // Never include the token; redaction at write time is a backstop, this is the first line.
        return request.extra.entries
            .filter { !it.key.equals("apiToken", ignoreCase = true) && !it.key.equals("token", ignoreCase = true) }
            .joinToString(", ") { "${it.key}=${it.value}" }
            .ifBlank { null }
    }
}
