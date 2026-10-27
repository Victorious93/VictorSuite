package com.termux.zerocore.data

/**
 * Pure (no Android) helpers that build the backup/restore commands typed into the terminal.
 *
 * Everything that comes from a file name is single-quoted with [shellQuote], so names with
 * spaces, `;`, `$()`, backticks or `'` are passed to tar literally instead of being run.
 * Names containing control characters (a newline would submit the command early) or a path
 * separator are rejected by [isSafeFileName] before anything is built.
 */
object RestoreShell {
    const val BACKUP_DIR = "./storage/shared/xinhao/data/"

    // tar flags per archive type (same values as before).
    const val TAR_RESTORE_GZ = "xzvf"
    const val TAR_RESTORE_BZ2 = "xjf"
    const val TAR_RESTORE_XZ = "xvJf"
    const val TAR_RESTORE_Z = "xZf"

    /** POSIX single-quote: wrap in '...' and turn each embedded ' into '\''. */
    @JvmStatic
    fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** A plain file name we are willing to put on a command line (quoted). */
    @JvmStatic
    fun isSafeFileName(name: String?): Boolean {
        if (name.isNullOrEmpty() || name == "." || name == "..") return false
        return name.none { it < ' ' || it == '\u007f' || it == '/' }
    }

    /**
     * Returns the tar extract flags for a supported backup archive name, or null if the
     * format is not supported. Use this BEFORE creating any container directory.
     */
    @JvmStatic
    fun restoreFlagFor(name: String): String? = when {
        name.endsWith("tar.gz") -> TAR_RESTORE_GZ
        name.endsWith("tar.bz2") -> TAR_RESTORE_BZ2
        name.endsWith("tar.xz") -> TAR_RESTORE_XZ
        name.endsWith("tar.Z") -> TAR_RESTORE_Z
        else -> null
    }

    /**
     * Builds the restore command. [tarFlag] must be one of the TAR_RESTORE_* constants.
     * @throws IllegalArgumentException for an unknown flag or an unsafe archive/container name.
     */
    @JvmStatic
    fun buildRestore(tarFlag: String, archiveName: String, containerName: String, successMessage: String): String {
        require(tarFlag in setOf(TAR_RESTORE_GZ, TAR_RESTORE_BZ2, TAR_RESTORE_XZ, TAR_RESTORE_Z)) { "unknown tar flag" }
        require(isSafeFileName(archiveName)) { "unsafe archive name" }
        require(isSafeFileName(containerName)) { "unsafe container name" }
        val archive = shellQuote(BACKUP_DIR + archiveName)
        val dir = "../../" + shellQuote(containerName)
        return "cd ~ && cd ~ && tar -v -$tarFlag $archive -C $dir" +
            " && mv $dir/data/data/com.termux/files/home $dir" +
            " && mv $dir/data/data/com.termux/files/usr $dir" +
            " && rm -rf $dir/data" +
            " && echo ${shellQuote(successMessage)} \n"
    }

    /** Builds the backup command; [tarFlag] is e.g. "zcvf", [archiveName] the generated file name. */
    @JvmStatic
    fun buildBackup(tarFlag: String, archiveName: String, successMessage: String): String {
        require(tarFlag.all { it.isLetter() }) { "bad tar flag" }
        require(isSafeFileName(archiveName)) { "unsafe archive name" }
        return "cd ~ && cd ~ && tar -$tarFlag ${shellQuote(BACKUP_DIR + archiveName)} /data/data/com.termux/files" +
            " && echo ${shellQuote(successMessage)} \n"
    }
}
