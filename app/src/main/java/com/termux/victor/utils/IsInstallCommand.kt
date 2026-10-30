package com.termux.victor.utils

import android.app.Activity
import android.view.Gravity
import android.view.View
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.victor.dialog.SwitchDialog
import com.termux.victor.url.FileUrl
import com.termux.victor.url.FileUrl.smsUrl
import java.io.File

object IsInstallCommand {


    public fun isInstall(activity: Activity, command:String, commandInstall:String):Boolean{

        val file = File(FileUrl.mainBinUrl, "/${command}")

        if(!file.exists()){

            val temp = "${UUtils.getString(R.string.vs_you_are_not_currently_installed)} $command ${UUtils.getString(R.string.vs_whether_to_install)}"

            val msg: SwitchDialog = switchDialogShow(activity,UUtils.getString(R.string.vs_warning), temp)

            ///data/data/com.termux/files/usr/bin/vim
            msg.cancel!!.setOnClickListener { msg.dismiss() }
            UUtils.showLog("vim install directory: ${file.absoluteFile}")
            msg.ok!!.setOnClickListener {
                msg.dismiss()
                if (file.exists()) {
                    UUtils.showMsg(UUtils.getString(R.string.vs_you_have_installed_tools_1) + "(${command})")
                } else {
                    com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(commandInstall)
                }
            }

            return false

        }else{

            return true
        }


    }


    public fun switchDialogShow(activity: Activity,title: String, msg: String): SwitchDialog {

        val switchDialog = SwitchDialog(activity)
        switchDialog.title!!.text = title
        switchDialog.msg!!.text = msg
        switchDialog.other!!.visibility = View.GONE
        switchDialog.ok!!.text = UUtils.getString(R.string.vs_determine)
        switchDialog.cancel!!.text = UUtils.getString(R.string.vs_cancel)
        switchDialog.show()
        return switchDialog
    }

}
