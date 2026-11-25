package com.termux.victor.shellbroker.android

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.shell.TermuxShellUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.terminal.PtyBridge
import com.termux.victor.shellbroker.Channel
import com.termux.victor.shellbroker.Privilege
import com.termux.victor.shellbroker.ShellProcess
import com.termux.victor.shellbroker.ShellProcessFactory
import com.termux.victor.shellbroker.ShellProcessListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * VictorSuite's own terminal runtime as the broker's executor: interactive shells use the same native pty path as Termux sessions
 * ([JNI.createSubprocess], Termux environment, `$PREFIX/bin/login`); one-shot commands use a plain process with separate
 * stdout/stderr and the real exit status. Nothing else (no ADB, Shizuku or direct `su` by the caller) is involved.
 */
class AndroidShellProcessFactory(private val context: Context) : ShellProcessFactory {
    private fun env(): Array<String> = ShellEnvironmentUtils.convertEnvironmentToEnviron(TermuxShellEnvironment().getEnvironment(context, false)).toTypedArray()
    private fun home(): String = TermuxConstants.TERMUX_HOME_DIR_PATH.also { File(it).mkdirs() }
    private fun login(): String = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "login").takeIf { it.canExecute() }?.path ?: "/system/bin/sh"

    override fun rootAvailable(): Boolean = SU_PATHS.any { File(it).exists() }      // passive check: never triggers a root prompt

    override fun startInteractive(privilege: Privilege, cols: Int, rows: Int, listener: ShellProcessListener): ShellProcess {
        val exe: String
        val args: Array<String>
        if (privilege == Privilege.ROOT) { exe = SU_PATHS.first { File(it).exists() }; args = arrayOf(exe) }
        else { exe = login(); args = TermuxShellUtils.setupShellCommandArguments(exe, null) }
        val pid = IntArray(1)
        val fd = PtyBridge.createSubprocess(exe, home(), args, env(), pid, rows, cols)
        val pfd = ParcelFileDescriptor.adoptFd(fd)
        val out = FileOutputStream(pfd.fileDescriptor)
        Thread({
            try {
                FileInputStream(pfd.fileDescriptor).use { ins ->
                    val b = ByteArray(4096)
                    while (true) { val n = ins.read(b); if (n < 0) break; listener.onOutput(b.copyOf(n), Channel.TTY) }
                }
            } catch (_: Exception) { }
        }, "ShellBrokerPtyReader[${pid[0]}]").apply { isDaemon = true }.start()
        Thread({
            val status = PtyBridge.waitFor(pid[0])
            try { pfd.close() } catch (_: Exception) { }
            listener.onExit(status)
        }, "ShellBrokerPtyWaiter[${pid[0]}]").apply { isDaemon = true }.start()
        return object : ShellProcess {
            override fun write(data: ByteArray) { try { out.write(data); out.flush() } catch (_: Exception) { } }
            override fun resize(cols: Int, rows: Int) { try { PtyBridge.setPtyWindowSize(fd, rows, cols) } catch (_: Throwable) { } }
            override fun signal(name: String) {
                // Control characters go through the pty line discipline so the *foreground job* receives them, exactly like a keyboard.
                when (name) {
                    "INT" -> write(byteArrayOf(3)); "QUIT" -> write(byteArrayOf(0x1c)); "TSTP" -> write(byteArrayOf(0x1a))
                    "TERM" -> kill(OsConstants.SIGTERM); "HUP" -> kill(OsConstants.SIGHUP); "CONT" -> kill(OsConstants.SIGCONT)
                }
            }
            private fun kill(sig: Int) { try { Os.kill(pid[0], sig) } catch (_: Exception) { } }
            override fun kill() { kill(OsConstants.SIGKILL) }
        }
    }

    override fun startOneShot(privilege: Privilege, command: String, listener: ShellProcessListener): ShellProcess {
        val shell = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "sh").takeIf { it.canExecute() }?.path ?: "/system/bin/sh"
        val argv = if (privilege == Privilege.ROOT) listOf(SU_PATHS.first { File(it).exists() }, "-c", command) else listOf(shell, "-c", command)
        val pb = ProcessBuilder(argv).directory(File(home()))
        pb.environment().clear()
        TermuxShellEnvironment().getEnvironment(context, false).forEach { (k, v) -> pb.environment()[k] = v }
        val proc = pb.start()
        proc.outputStream.close()                                   // a one-shot takes no input
        fun pump(ins: java.io.InputStream, ch: Channel) = Thread({
            try { val b = ByteArray(4096); while (true) { val n = ins.read(b); if (n < 0) break; listener.onOutput(b.copyOf(n), ch) } } catch (_: Exception) { }
        }, "ShellBrokerOneShot-${ch.wire}").apply { isDaemon = true }
        val t1 = pump(proc.inputStream, Channel.STDOUT).also { it.start() }
        val t2 = pump(proc.errorStream, Channel.STDERR).also { it.start() }
        Thread({ val st = proc.waitFor(); t1.join(2000); t2.join(2000); listener.onExit(st) }, "ShellBrokerOneShotWaiter").apply { isDaemon = true }.start()
        return object : ShellProcess {
            override fun write(data: ByteArray) {}
            override fun resize(cols: Int, rows: Int) {}
            override fun signal(name: String) { if (name == "INT" || name == "TERM") proc.destroy() }
            override fun kill() { proc.destroyForcibly() }
        }
    }

    companion object {
        private val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/magisk/su", "/data/adb/ksu/bin/su")
    }
}
