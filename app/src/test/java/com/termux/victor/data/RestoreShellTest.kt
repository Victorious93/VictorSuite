package com.termux.victor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Victorious Framework: backup/restore commands must never run text taken from a file name. */
class RestoreShellTest {
    private val nasty = listOf(
        "a;touch PWNED;b.tar.gz",
        "x\$(touch PWNED).tar.gz",
        "x`touch PWNED`.tar.gz",
        "my backup 2026.tar.gz",
        "it's mine.tar.gz",
        "'; touch PWNED; '.tar.gz",
        "\$HOME && touch PWNED | x > y.tar.gz",
    )

    @Test
    fun quoteWrapsAndEscapesSingleQuotes() {
        assertEquals("'abc'", RestoreShell.shellQuote("abc"))
        assertEquals("'it'\\''s'", RestoreShell.shellQuote("it's"))
        assertEquals("''", RestoreShell.shellQuote(""))
    }

    @Test
    fun quotedValueRoundTripsThroughRealShellWithoutExecuting() {
        val sh = File("/bin/sh")
        assumeTrue(sh.canExecute())
        val dir = Files.createTempDirectory("rq").toFile()
        try {
            for (s in nasty) {
                val p = ProcessBuilder("/bin/sh", "-c", "printf '%s' " + RestoreShell.shellQuote(s))
                    .directory(dir).redirectErrorStream(true).start()
                val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
                assertEquals(0, p.waitFor())
                assertEquals(s, out)
            }
            assertFalse("injected command ran", File(dir, "PWNED").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun restoreCommandKeepsSpacesAndQuotesName() {
        val cmd = RestoreShell.buildRestore(RestoreShell.TAR_RESTORE_GZ, "my backup.tar.gz", "files2", "done")
        assertEquals(
            "cd ~ && cd ~ && tar -v -xzvf './storage/shared/xinhao/data/my backup.tar.gz' -C ../../'files2'" +
                " && mv ../../'files2'/data/data/com.termux/files/home ../../'files2'" +
                " && mv ../../'files2'/data/data/com.termux/files/usr ../../'files2'" +
                " && rm -rf ../../'files2'/data && echo 'done' \n",
            cmd,
        )
    }

    @Test
    fun restoreCommandHasExactlyOneNewlineForAnyAcceptedName() {
        for (n in nasty) {
            val cmd = RestoreShell.buildRestore(RestoreShell.TAR_RESTORE_XZ, n, "files3", "ok")
            assertEquals(n, 1, cmd.count { it == '\n' })
            assertTrue(cmd.contains(RestoreShell.shellQuote(RestoreShell.BACKUP_DIR + n)))
        }
    }

    /** Runs the generated restore command's tar argument through a real shell with a stub tar. */
    @Test
    fun restoreCommandDoesNotExecuteInjectedText() {
        val sh = File("/bin/sh")
        assumeTrue(sh.canExecute())
        val dir = Files.createTempDirectory("rs").toFile()
        try {
            for (n in nasty) {
                val cmd = RestoreShell.buildRestore(RestoreShell.TAR_RESTORE_GZ, n, "files9", "ok")
                // Keep only the tar invocation and replace tar with a stub that prints its args.
                val tarPart = cmd.substringAfter("cd ~ && cd ~ && ").substringBefore(" && mv ")
                val script = "tar() { for a in \"\$@\"; do printf '%s\\n' \"\$a\"; done; }; $tarPart"
                val p = ProcessBuilder("/bin/sh", "-c", script).directory(dir).redirectErrorStream(true).start()
                val lines = p.inputStream.readBytes().toString(Charsets.UTF_8).lines()
                assertEquals(0, p.waitFor())
                assertEquals(listOf("-v", "-xzvf", RestoreShell.BACKUP_DIR + n, "-C", "../../files9", ""), lines)
            }
            assertFalse("injected command ran", File(dir, "PWNED").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun unsafeNamesAreRejected() {
        for (n in listOf("a\nb.tar.gz", "a\rb.tar.gz", "a\u0000.tar.gz", "a\tb.tar.gz", "a\u007fb.tar.gz", "../x.tar.gz", "", ".", "..")) {
            assertFalse(n, RestoreShell.isSafeFileName(n))
            try {
                RestoreShell.buildRestore(RestoreShell.TAR_RESTORE_GZ, n, "files2", "ok")
                fail("accepted unsafe name: $n")
            } catch (expected: IllegalArgumentException) {
            }
        }
        try {
            RestoreShell.buildRestore(RestoreShell.TAR_RESTORE_GZ, "ok.tar.gz", "files2\n", "ok")
            fail("accepted unsafe container")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            RestoreShell.buildRestore("xzvf; rm -rf ~", "ok.tar.gz", "files2", "ok")
            fail("accepted bad flag")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun formatCheckCoversEveryBackupFormatIncludingZ() {
        assertEquals(RestoreShell.TAR_RESTORE_GZ, RestoreShell.restoreFlagFor("2026_10_27.tar.gz"))
        assertEquals(RestoreShell.TAR_RESTORE_BZ2, RestoreShell.restoreFlagFor("a.tar.bz2"))
        assertEquals(RestoreShell.TAR_RESTORE_XZ, RestoreShell.restoreFlagFor("a.tar.xz"))
        assertEquals(RestoreShell.TAR_RESTORE_Z, RestoreShell.restoreFlagFor("2026_10_27.tar.Z"))
        assertNull(RestoreShell.restoreFlagFor("a.zip"))
        assertNull(RestoreShell.restoreFlagFor("a.tar"))
        assertNull(RestoreShell.restoreFlagFor("a.tar.gz.txt"))
    }

    @Test
    fun backupCommandQuotesPathAndMessage() {
        assertEquals(
            "cd ~ && cd ~ && tar -Zcvf './storage/shared/xinhao/data/2026_10_27_10_00_00.tar.Z' /data/data/com.termux/files" +
                " && echo 'done -> it'\\''s |here' \n",
            RestoreShell.buildBackup("Zcvf", "2026_10_27_10_00_00.tar.Z", "done -> it's |here"),
        )
    }
}
