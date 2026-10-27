package com.termux.zerocore.data

import com.example.xh_lib.utils.UUtils
import com.termux.R
import java.io.File

object CommendShellData {

    public const val SHELL_DATA_WEB_LINUX = "cd ~ && pkg update -y && pkg install wget proot ttyd -y\n"
    public const val SHELL_DATA_RUN_WEB = "ttyd bash&\n"

    //备份
    //"cd ~ && cd ~ && tar -zcvf - TemporaryMark | pv -s $(($(du -sk TemporaryMark | awk '{print $1}') * 1024)) | gzip > ./storage/shared/xinhao/data/systemName
   // public var SHELL_BACKUP = "cd ~ && cd ~ && tar -TemporaryMark - /data/data/com.termux/files | pv -s $(($(du -sk /data/data/com.termux/files | awk '{print $1}') * 1024)) | gzip > ./storage/shared/xinhao/data/systemName && echo \"${UUtils.getString(R.string.backup_success)}\" \n"
    // VictorSuite: built by RestoreShell.buildBackup (archive path and message are shell-quoted).
    public fun getShellBackup(command: String, archiveName: String): String =
        RestoreShell.buildBackup(command, archiveName, UUtils.getString(R.string.backup_success))
    public const val SHELL_TAR_GZ = "zcvf"
    public const val SHELL_TAR_BZ2 = "jcvf"
    public const val SHELL_TAR_XZ = "Jcvf"
    public const val SHELL_TAR_Z = "Zcvf"

    //恢复
    public const val SHELL_TAR_RESTORE_GZ = RestoreShell.TAR_RESTORE_GZ
    public const val SHELL_TAR_RESTORE_BZ2 = RestoreShell.TAR_RESTORE_BZ2
    public const val SHELL_TAR_RESTORE_XZ = RestoreShell.TAR_RESTORE_XZ
    public const val SHELL_TAR_RESTORE_Z = RestoreShell.TAR_RESTORE_Z

    //欢迎语
    public const val SHELL_WELCOME_MESSAGE =  "echo \"+++++++++++++START+++++++++++++\" \n"
    // VictorSuite: was string concatenation with the unquoted file name (`.replace(" ","")`),
    // which let a crafted backup file name inject shell commands. Now quoted; unsafe names throw.
    public fun getShellRestore(command: String, tarFle: File, createFile: File): String =
        RestoreShell.buildRestore(command, tarFle.name, createFile.name, UUtils.getString(R.string.system_restore_success))
}
