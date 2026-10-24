package com.termux.zerocore.url

import android.os.Environment
import com.termux.shared.termux.TermuxConstants
import java.io.File


/**
 *
 * 路径管理
 *
 *
 */
object FileUrl {

    //主目录
    public val mainFilesUrl = TermuxConstants.TERMUX_FILES_DIR_PATH
    public val mainAppUrl = TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH
    public val mainHomeUrl = TermuxConstants.TERMUX_FILES_DIR_PATH + "/home"
    public val mainBinUrl = TermuxConstants.TERMUX_FILES_DIR_PATH + "/usr/bin"
    public val mainConfigUrl = TermuxConstants.TERMUX_FILES_DIR_PATH + "/home/.termux/"
    public val mainConfigImg = TermuxConstants.TERMUX_FILES_DIR_PATH + "/home/.img/"


    //主目录
    public val victorSuiteHome = File(Environment.getExternalStorageDirectory(), "/xinhao/")
    //恢复目录
    public val victorSuiteData = File(Environment.getExternalStorageDirectory(), "/xinhao/data/")
    //APK目录
    public val victorSuiteApk = File(Environment.getExternalStorageDirectory(), "/xinhao/apk/")
    //windows目录
    public val victorSuiteWindows = File(Environment.getExternalStorageDirectory(), "/xinhao/windows/")
   //命令目录
    public val victorSuiteCommand = File(Environment.getExternalStorageDirectory(), "/xinhao/command/")
   //字体目录
    public val victorSuiteFont = File(Environment.getExternalStorageDirectory(), "/xinhao/font")
   //iso目录
    public val victorSuiteIso = File(Environment.getExternalStorageDirectory(), "/xinhao/iso")
   //mysql目录
    public val victorSuiteMysql = File(Environment.getExternalStorageDirectory(), "/xinhao/mysql")
    //online_system 目录
    public val victorSuiteOnlineSystem = File(Environment.getExternalStorageDirectory(), "/xinhao/online_system")
   //qemu目录
    public val victorSuiteQemu = File(Environment.getExternalStorageDirectory(), "/xinhao/qemu")
   //插件安装目录 (see com.termux.zerocore.plugin) - each plugin gets a subdirectory named by its id
    public val victorSuitePlugins = File(Environment.getExternalStorageDirectory(), "/xinhao/plugins")
   //server目录
    public val victorSuiteServer = File(Environment.getExternalStorageDirectory(), "/xinhao/server")
   //share目录
    public val victorSuiteShare = File(Environment.getExternalStorageDirectory(), "/xinhao/share")
    //system目录
    public val victorSuiteSystem = File(Environment.getExternalStorageDirectory(), "/xinhao/system")
    //web_config
    public val victorSuiteWebConfig = File(Environment.getExternalStorageDirectory(), "/xinhao/web_config")
    //模块包目录
    public val victorSuiteModule = File(Environment.getExternalStorageDirectory(), "/xinhao/module")
     val victorSuiteWindowsConfig = File(Environment.getExternalStorageDirectory(), "/xinhao/windows_config/")

    //官方 sources 路径[源路径]
    public val sourcesUrl = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/etc/apt/sources.list"
    //官方 science 路径[源路径]
    public val scienceUrl = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/etc/apt/sources.list.d/science.list"
    //官方 game 路径[源路径]
    public val gameUrl = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/etc/apt/sources.list.d/game.list"
    //短信工具目录
    public val smsUrl = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/smsread"
    //通讯录工具目录
    public val phoneUrl = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/readcontacts"

    //打开左边工具
    public val openLeft = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/openleftwindow"
    //打开右边工具
    public val openRight = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/openrightwindow"
    //zt通用工具
    public val zt = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/zt"
    //通道文件APK
    public val aislePathAPK = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/libexec/termux-x11/loader.apk"
    public val aislePathAPKPath = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/libexec/termux-x11"
    //通道文件执行脚本
    public val aislePathSh = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/termux-x11"
    //通道文件执行脚本
    public val aislePreferencePathSh = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/bin/termux-x11-preference"
    //通道二进制文
    public val aislePathSo = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/lib/libXlorie.so"
    //定时器目录
    public val timerTermuxDir = "${mainHomeUrl}/.timerdir"
    public val timerTermuxFile = "${mainHomeUrl}/.timerdir/termux_timer.sh"

    public val timerShellDir = "${mainHomeUrl}/.timerdir"
    public val timerShellFile = "${mainHomeUrl}/.timerdir/shell_timer.sh"
    public val timerShellLogDir = "${mainHomeUrl}/.timerdir/log"
    public val timerShellExecDir = "${mainFilesUrl}"
    public val timerShellExecFile = "${mainFilesUrl}/execTermuxEnv.sh"


    //获取短信的目录
    public val smsUrlFile = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/home/sms.txt"
    public val phoneUrlFile = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/home/phone.txt"
    //系统启动脚本目录
    ///data/data/com.victorious.suite/files/usr/etc/bash.bashrc  .xinhao_history
    public val smsBashrcFile = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/etc/bash.bashrc"
    public val smsMotdFile = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/usr/etc/motd"
    //Zero系统脚本目录
    public val smsZeroBashrcFileD = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/home/.xinhao_history"
    //Zero系统脚本
    public val smsZeroBashrcFile = "${TermuxConstants.TERMUX_FILES_DIR_PATH}/home/.xinhao_history/start_command.sh"

    //Headscale / tailnet client directory and scripts
    public val headscaleDir = "${mainHomeUrl}/.headscale"
    public val headscaleConnectScript = "${headscaleDir}/connect.sh"
    public val headscaleDisconnectScript = "${headscaleDir}/disconnect.sh"
    public val headscaleStatusScript = "${headscaleDir}/status.sh"

}
