package com.termux.victor.shellbroker.android

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.victor.shellbroker.CallerVerifier
import com.termux.victor.shellbroker.IRemoteTerminalCallback
import com.termux.victor.shellbroker.IRemoteTerminalTransport
import com.termux.victor.shellbroker.Verification
import com.termux.victor.shellbroker.RustDeskVTransport

/**
 * "Remote shell": a terminal for ONE named remote peer (e.g. a VictorLinux PC), rendered by VictorSuite's terminal view, carried by
 * rustdeskV's authenticated transport. Entry points: VictorSuite → Remote shell (peer list, [EXTRA_PEER]) and rustdeskV's Terminal
 * button (it opens its session first and hands over [EXTRA_SESSION]). Both end up here, for that exact peer.
 *
 * Safety: the transport package must be on the owner's trust list (package + pinned certificate + transport scope) BEFORE it is bound;
 * keystrokes are written only while the transport is attached and are never queued, so nothing is replayed after a disconnect; a
 * dropped transport ends the session (no silent reconnect, no retargeting to another peer).
 */
class RemoteTerminalActivity : Activity() {
    private var transport: IRemoteTerminalTransport? = null
    private var session: TerminalSession? = null
    private var sessionId: String? = null
    private var view: TerminalView? = null
    private var status: TextView? = null
    private val main = Handler(Looper.getMainLooper())
    private lateinit var peerLabel: String
    @Volatile private var attached = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { transport = IRemoteTerminalTransport.Stub.asInterface(service); onTransport() }
        override fun onServiceDisconnected(name: ComponentName) { attached = false; main.post { endSession("the connection to ${TRANSPORT_PKG} was lost", -1) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        peerLabel = intent.getStringExtra(EXTRA_PEER_NAME) ?: intent.getStringExtra(EXTRA_PEER) ?: "remote peer"
        title = "Remote shell — $peerLabel"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF000000.toInt()) }
        status = TextView(this).apply { setTextColor(0xFFB0B7C3.toInt()); setPadding(24, 12, 24, 12); text = "Connecting to $peerLabel…" }
        root.addView(status)
        setContentView(root)
        this.root = root
        // Trust gate first: nothing is bound or started until the owner's pinned transport is verified.
        val verifier = CallerVerifier(AndroidPackageIdentity(this), BrokerHolder.also { BrokerHolder.get(this) }.trust)
        when (val v = verifier.verifyProvider(TRANSPORT_PKG)) {
            is Verification.Rejected -> { status?.text = "Remote shell is not available: ${v.reason}\n\nTrust RustDeskV ($TRANSPORT_PKG) in VictorSuite → Remote shell access (enable 'terminal transport')."; return }
            is Verification.Ok -> Unit
        }
        val bind = Intent().setComponent(ComponentName(TRANSPORT_PKG, TRANSPORT_SERVICE))
        if (!bindService(bind, conn, Context.BIND_AUTO_CREATE)) status?.text = "Remote shell is not available: rustdeskV is not installed or is too old (it must provide the VictorSuite terminal transport)."
    }

    private lateinit var root: LinearLayout

    private fun onTransport() {
        val t = transport ?: return
        Thread {
            try {
                if (t.apiVersion() < 1) { main.post { status?.text = "rustdeskV's terminal transport is too old; update it." }; return@Thread }
                val handed = intent.getStringExtra(EXTRA_SESSION)
                val peer = intent.getStringExtra(EXTRA_PEER)
                if (handed == null && peer == null) { main.post { showPeers(t) }; return@Thread }
                val id = handed ?: t.openPeer(peer, 80, 24).let { r ->
                    if (!r.startsWith("session:")) { main.post { status?.text = "Could not open a terminal to $peerLabel: ${r.removePrefix("error:")}" }; return@Thread }
                    r.removePrefix("session:")
                }
                main.post { startTerminal(t, id) }
            } catch (e: Exception) { main.post { status?.text = "Remote shell failed: ${e.javaClass.simpleName}" } }
        }.start()
    }

    private fun showPeers(t: IRemoteTerminalTransport) {
        root.removeAllViews()
        root.addView(TextView(this).apply { setTextColor(-1); textSize = 18f; setPadding(24, 24, 24, 12); text = "Choose a PC for a remote shell" })
        val peers = try { t.listPeers().getParcelableArrayList<Bundle>("peers").orEmpty() } catch (e: Exception) { emptyList() }
        if (peers.isEmpty()) root.addView(TextView(this).apply { setTextColor(0xFFB0B7C3.toInt()); setPadding(24, 12, 24, 12); text = "No peers. Add your VictorLinux PC in rustdeskV first." })
        peers.forEach { p ->
            val ok = p.getBoolean("terminal_ok", false)
            root.addView(Button(this).apply {
                text = "${p.getString("name") ?: p.getString("peer_id")}  (${p.getString("platform") ?: "?"})" + if (ok) "" else "  — terminal not offered"
                isEnabled = ok
                setOnClickListener { startActivity(Intent(this@RemoteTerminalActivity, RemoteTerminalActivity::class.java).putExtra(EXTRA_PEER, p.getString("peer_id")).putExtra(EXTRA_PEER_NAME, p.getString("name"))); finish() }
            })
        }
    }

    private fun startTerminal(t: IRemoteTerminalTransport, id: String) {
        sessionId = id
        val transportHandle = object : TerminalSession.RemoteTransport {
            override fun write(data: ByteArray, offset: Int, count: Int) { if (attached) try { t.input(id, data.copyOfRange(offset, offset + count)) } catch (_: Exception) { } }   // never queued
            override fun resize(columns: Int, rows: Int) { if (attached) try { t.resize(id, columns, rows) } catch (_: Exception) { } }
            override fun close() { attached = false; try { t.close(id) } catch (_: Exception) { } }
        }
        val sessionClient = object : TermuxTerminalSessionClientBase() {
            override fun onTextChanged(changedSession: TerminalSession) { view?.onScreenUpdated() }
            override fun onSessionFinished(finishedSession: TerminalSession) { status?.text = "Session ended. Press Back to leave." }
        }
        val s = TerminalSession(transportHandle, 2000, sessionClient)
        session = s
        val tv = TerminalView(this, null)
        tv.setTerminalViewClient(object : com.termux.shared.termux.terminal.TermuxTerminalViewClientBase() {
            override fun shouldEnforceCharBasedInput() = true
            override fun isTerminalViewSelected() = true
        })
        tv.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics).toInt())
        tv.setTypeface(Typeface.MONOSPACE)
        view = tv
        root.removeAllViews()
        root.addView(status.also { it?.text = "Remote shell on $peerLabel  (end-to-end through rustdeskV; nothing is logged here)" })
        root.addView(tv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(keyRow())
        tv.attachSession(s)
        tv.requestFocus()
        val cb = object : IRemoteTerminalCallback.Stub() {
            override fun onState(sessionId: String, state: String, reason: String?) { if (state == "closed" || state == "failed") main.post { endSession(reason ?: state, -1) } }
            override fun onOutput(sessionId: String, data: ByteArray) { s.appendRemoteOutput(data, data.size) }
            override fun onExit(sessionId: String, exitStatus: Int) { s.remoteExited(exitStatus) }
        }
        Thread {
            val r = try { t.attach(id, cb) } catch (e: Exception) { "unavailable" }
            if (r.isEmpty()) attached = true else main.post { endSession("could not attach: $r", -1) }
        }.start()
    }

    private fun keyRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        fun key(label: String, bytes: ByteArray) = addView(Button(this@RemoteTerminalActivity).apply { text = label; textSize = 11f; minWidth = 0; minimumWidth = 0
            setOnClickListener { session?.write(bytes, 0, bytes.size) } }, LinearLayout.LayoutParams(0, -2, 1f))
        key("Esc", byteArrayOf(27)); key("Tab", byteArrayOf(9)); key("^C", byteArrayOf(3)); key("^D", byteArrayOf(4)); key("^Z", byteArrayOf(26))
        key("↑", "\u001b[A".toByteArray()); key("↓", "\u001b[B".toByteArray()); key("←", "\u001b[D".toByteArray()); key("→", "\u001b[C".toByteArray())
    }

    private fun endSession(reason: String, code: Int) {
        attached = false
        status?.text = "Session ended: $reason"
        session?.remoteExited(code)
    }

    override fun onDestroy() {
        attached = false
        sessionId?.let { id -> try { transport?.close(id) } catch (_: Exception) { } }
        try { unbindService(conn) } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PEER = "peer_id"; const val EXTRA_PEER_NAME = "peer_name"; const val EXTRA_SESSION = "session_id"
        const val TRANSPORT_PKG = RustDeskVTransport.PACKAGE
        const val TRANSPORT_SERVICE = RustDeskVTransport.SERVICE
    }
}
