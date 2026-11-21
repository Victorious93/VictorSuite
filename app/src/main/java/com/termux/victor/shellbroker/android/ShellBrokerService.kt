package com.termux.victor.shellbroker.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import com.termux.victor.shellbroker.BrokerApi
import com.termux.victor.shellbroker.Channel
import com.termux.victor.shellbroker.IShellBroker
import com.termux.victor.shellbroker.IShellSessionCallback
import com.termux.victor.shellbroker.Mode
import com.termux.victor.shellbroker.OpenAck
import com.termux.victor.shellbroker.Privilege
import com.termux.victor.shellbroker.SessionRequest
import com.termux.victor.shellbroker.SessionSink
import com.termux.victor.shellbroker.SessionState

/**
 * Bound service for fleet shell actions. Exported (other apps must reach it) but every call is authorised by
 * [Binder.getCallingUid] → package → pinned signing certificate on the owner's trust list. A caller that goes away ends its shells.
 */
class ShellBrokerService : Service() {
    override fun onBind(intent: Intent?): IBinder = stub

    private val stub = object : IShellBroker.Stub() {
        override fun apiVersion() = BrokerApi.VERSION

        override fun capabilities(): Bundle {
            val c = BrokerHolder.get(applicationContext).capabilities()
            return Bundle().apply {
                putInt("api_version", c["api_version"] as Int); putBoolean("enabled", c["enabled"] as Boolean)
                putBoolean("root_allowed", c["root_allowed"] as Boolean); putBoolean("root_available", c["root_available"] as Boolean)
                putString("implementation", "victorsuite-shell-broker")
            }
        }

        override fun open(req: Bundle?, cb: IShellSessionCallback?): String {
            if (req == null || cb == null) return "bad_request"
            val uid = Binder.getCallingUid()
            val r = SessionRequest(
                sessionId = req.getString("session_id").orEmpty(), peer = req.getString("peer").orEmpty(), target = req.getString("target").orEmpty(),
                mode = Mode.of(req.getString("mode")) ?: return "bad_request", privilege = Privilege.of(req.getString("privilege")) ?: return "bad_request",
                command = req.getString("command"), cols = req.getInt("cols", 80), rows = req.getInt("rows", 24), nonce = req.getString("nonce").orEmpty(),
                expiresMs = req.getLong("expires_ms", 0), binding = req.getString("binding"), callerApiVersion = req.getInt("caller_api_version", 1),
            )
            val broker = BrokerHolder.get(applicationContext)
            val sink = object : SessionSink {
                override fun onState(sessionId: String, state: SessionState, reason: String?, binding: String?) = safe { cb.onState(sessionId, state.wire, reason, binding) }
                override fun onOutput(sessionId: String, data: ByteArray, channel: Channel) = safe { cb.onOutput(sessionId, data, channel.wire) }
                override fun onExit(sessionId: String, status: Int) = safe { cb.onExit(sessionId, status) }
                private fun safe(f: () -> Unit) { try { f() } catch (e: RemoteException) { broker.close(uid, r.sessionId, "caller disconnected") } }
            }
            // A caller that dies takes its shell with it: nothing keeps running unattended.
            try { cb.asBinder().linkToDeath({ broker.close(uid, r.sessionId, "caller process died") }, 0) } catch (_: RemoteException) { return "no_such_session" }
            return when (val a = broker.open(uid, r, sink)) {
                is OpenAck.Accepted -> ""
                is OpenAck.Rejected -> a.code.wire
            }
        }

        override fun input(sessionId: String, data: ByteArray) = BrokerHolder.get(applicationContext).input(Binder.getCallingUid(), sessionId, data)?.wire ?: ""
        override fun resize(sessionId: String, cols: Int, rows: Int) = BrokerHolder.get(applicationContext).resize(Binder.getCallingUid(), sessionId, cols, rows)?.wire ?: ""
        override fun signal(sessionId: String, name: String) = BrokerHolder.get(applicationContext).signal(Binder.getCallingUid(), sessionId, name)?.wire ?: ""
        override fun close(sessionId: String, reason: String?) = BrokerHolder.get(applicationContext).close(Binder.getCallingUid(), sessionId, reason ?: "closed by caller")?.wire ?: ""
    }
}
