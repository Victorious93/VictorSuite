package com.termux.victor.ssh;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;

import com.zp.z_file.util.LogUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hands one password to {@code sshpass -f <fifo>} without putting it on the command line, in the
 * environment, in shell history, or in a regular file.
 *
 * <p>A FIFO (mode 0600) is created in the app's private cache dir. A background thread blocks
 * until sshpass opens the FIFO at the password prompt, writes the password once, then deletes
 * the FIFO. If nobody reads it within {@link #TIMEOUT_MS}, it is released and deleted unread.
 */
public final class PasswordFifo {

    private static final String TAG = "QuickSsh";
    static final long TIMEOUT_MS = 120_000L;
    private static final String DIR = "victor-ssh-pass";

    private PasswordFifo() {
    }

    /** @return the FIFO path to pass to {@code sshpass -f}, or null if a FIFO could not be made. */
    public static String offer(Context context, String password) {
        File dir = new File(context.getCacheDir(), DIR);
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            Os.chmod(dir.getAbsolutePath(), 0700);
            cleanStale(dir);
            final File fifo = new File(dir, "p-" + UUID.randomUUID());
            Os.mkfifo(fifo.getAbsolutePath(), 0600);
            final byte[] data = (password + "\n").getBytes(StandardCharsets.UTF_8);
            final AtomicBoolean expired = new AtomicBoolean(false);
            final AtomicBoolean finished = new AtomicBoolean(false);

            Thread writer = new Thread(() -> {
                try (FileOutputStream out = new FileOutputStream(fifo)) { // blocks until a reader opens
                    if (!expired.get()) out.write(data);
                } catch (Exception e) {
                    LogUtils.d(TAG, "password fifo: " + QuickSshRedactor.describe(e));
                } finally {
                    java.util.Arrays.fill(data, (byte) 0);
                    finished.set(true);
                    //noinspection ResultOfMethodCallIgnored
                    fifo.delete();
                }
            }, "quickssh-pass");
            writer.setDaemon(true);
            writer.start();

            Thread reaper = new Thread(() -> {
                try {
                    Thread.sleep(TIMEOUT_MS);
                } catch (InterruptedException ignored) {
                }
                if (finished.get()) return;
                expired.set(true);
                try {
                    // Opening the read end releases the blocked writer, which then writes nothing.
                    FileDescriptor fd = Os.open(fifo.getAbsolutePath(), OsConstants.O_RDONLY | OsConstants.O_NONBLOCK, 0);
                    Os.close(fd);
                } catch (Exception ignored) {
                }
            }, "quickssh-pass-reaper");
            reaper.setDaemon(true);
            reaper.start();
            return fifo.getAbsolutePath();
        } catch (Exception e) {
            LogUtils.e(TAG, "password fifo unavailable: " + QuickSshRedactor.describe(e));
            return null;
        }
    }

    private static void cleanStale(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 2 * TIMEOUT_MS;
        for (File f : files) {
            if (f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}
