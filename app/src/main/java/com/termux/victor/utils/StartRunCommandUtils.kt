package com.termux.victor.utils

import com.termux.victor.util.OtherPrefs
import android.system.Os
import com.termux.victor.util.UUtils
import com.termux.victor.util.UUtils.FileCallback
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.victor.code.CodeString
import com.termux.victor.url.FileUrl
import java.io.File

object StartRunCommandUtils {
    private var isRun = false
    public fun startRun(){
        val smsBashrcFile = File(FileUrl.smsBashrcFile)
        val smsZeroBashrcFileD = File(FileUrl.smsZeroBashrcFileD)
        val smsZeroBashrcFile = File(FileUrl.smsZeroBashrcFile)

        if(!smsBashrcFile.exists()){
            return
        }
        if(!smsZeroBashrcFileD.exists()){
            smsZeroBashrcFileD.mkdirs()
        }
        var fileString = UUtils.getFileString(smsBashrcFile)
        fileString += "\n cd ~ > /dev/null && ./.xinhao_history/start_command.sh"
        UUtils.setFileString(smsBashrcFile,fileString)
        OtherPrefs.saveStringOther("start_command","true")
        UUtils.writerFile("runcommand/start_command.sh",smsZeroBashrcFile)
        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.runstartSh)
        try {
            Os.chmod(FileUrl.smsZeroBashrcFile, 448)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        UUtils.showMsg(UUtils.getString(R.string.已打开开机启动命令))
        isRun = true

    }

    public fun isRun():Boolean{
        val stringOther = OtherPrefs.getStringOther("start_command")
        if(stringOther == null || stringOther.isEmpty() || stringOther == "def"){
            isRun = false
        }else{
            isRun = true
        }
        return isRun
    }

    public fun endRun(){
        val file = File(FileUrl.smsBashrcFile)
        val open = UUtils.getContext().assets.open("bash.bashrc")
        UUtils.writerFileRawInput(file, open, object : FileCallback {
            override fun callBack(msg: String?, state: Boolean) {
                if (state) {
                    UUtils.showMsg(UUtils.getString(R.string.install_def_bash_msg_ok))
                    OtherPrefs.saveStringOther("start_command","def")
                    isRun = false
                } else {
                    UUtils.showMsg(UUtils.getString(R.string.install_def_bash_msg_error))
                }
            }
        })
    }
}
