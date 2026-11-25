package com.termux.victor.shellbroker.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.termux.R
import com.termux.victor.shellbroker.ApprovalGate
import com.termux.victor.shellbroker.ApprovalPrompt
import com.termux.victor.shellbroker.Decision
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device approval. A request raises a high-priority notification whose only action is opening [ShellApprovalActivity]; the
 * decision is made by the owner tapping Approve there (not from the notification shade, not from a remote message). The decision is
 * one-shot, keyed by session id, and a session can only be approved once.
 */
object ApprovalRegistry {
    class Pending(val prompt: ApprovalPrompt) { val latch = CountDownLatch(1); @Volatile var decision: Decision = Decision.TIMED_OUT }
    private val pending = ConcurrentHashMap<String, Pending>()

    fun prompt(sessionId: String): ApprovalPrompt? = pending[sessionId]?.prompt

    /** Called by the approval activity. Returns false if the request is gone (answered, cancelled or expired). */
    fun decide(sessionId: String, d: Decision): Boolean {
        val p = pending[sessionId] ?: return false
        synchronized(p) { if (p.latch.count == 0L) return false; p.decision = d; p.latch.countDown() }
        return true
    }

    fun register(prompt: ApprovalPrompt): Pending = Pending(prompt).also { pending[prompt.sessionId] = it }
    fun remove(sessionId: String) { pending.remove(sessionId) }
    fun pendingPrompts(): List<ApprovalPrompt> = pending.values.map { it.prompt }
}

class NotificationApprovalGate(private val context: Context) : ApprovalGate {
    override fun request(prompt: ApprovalPrompt, timeoutMs: Long): Decision {
        val p = ApprovalRegistry.register(prompt)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Remote shell approvals", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(context, ShellApprovalActivity::class.java).putExtra(ShellApprovalActivity.EXTRA_SESSION, prompt.sessionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, prompt.sessionId.hashCode(), open, flags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        val n = b.setSmallIcon(R.drawable.ic_service_notification).setContentTitle("Shell request from ${prompt.callerLabel}")
            .setContentText("${prompt.mode.wire} ${prompt.privilege.wire} shell on this phone — tap to review")
            .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        val id = NOTIF_BASE + (prompt.sessionId.hashCode() and 0xffff)
        try { nm.notify(id, n) } catch (_: Exception) { }
        try {
            // If VictorSuite is in the foreground this opens the review at once; otherwise the notification/full-screen intent does.
            context.startActivity(open)
        } catch (_: Exception) { }
        try { p.latch.await(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
        val d = synchronized(p) { if (p.latch.count > 0) { p.decision = Decision.TIMED_OUT; p.latch.countDown() }; p.decision }
        ApprovalRegistry.remove(prompt.sessionId)
        try { nm.cancel(id) } catch (_: Exception) { }
        return d
    }

    override fun cancel(sessionId: String) {
        ApprovalRegistry.decide(sessionId, Decision.DENIED)
    }

    companion object { const val CHANNEL = "victorsuite_shell_approvals"; const val NOTIF_BASE = 0x5ee1_0000 }
}
