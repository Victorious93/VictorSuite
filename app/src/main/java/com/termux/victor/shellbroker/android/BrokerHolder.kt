package com.termux.victor.shellbroker.android

import android.content.Context
import com.termux.shared.logger.Logger
import com.termux.victor.shellbroker.AuditEvent
import com.termux.victor.shellbroker.CallerVerifier
import com.termux.victor.shellbroker.ShellBroker

/** Process-wide broker wiring. Audit goes to the Termux logger as metadata only (ids, state, caller) — never commands' contents. */
object BrokerHolder {
    @Volatile private var broker: ShellBroker? = null
    lateinit var settings: PrefsBrokerSettings private set
    lateinit var trust: PrefsTrustStore private set

    @Synchronized fun get(context: Context): ShellBroker {
        broker?.let { return it }
        val app = context.applicationContext
        settings = PrefsBrokerSettings(app); trust = PrefsTrustStore(app)
        val b = ShellBroker(
            verifier = CallerVerifier(AndroidPackageIdentity(app), trust), trust = trust, settings = settings,
            gate = NotificationApprovalGate(app), processes = AndroidShellProcessFactory(app),
            audit = { e: AuditEvent -> Logger.logInfo("ShellBroker", "${e.kind} session=${e.sessionId} caller=${e.caller} ${e.mode?.wire}/${e.privilege?.wire} ${e.detail ?: ""}") },
        )
        broker = b
        return b
    }
}
