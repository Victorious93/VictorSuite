package com.termux.victor.config.mainmenu.config

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.victor.util.UUtils
import com.scottyab.rootbeer.RootBeer
import com.termux.R
import com.termux.victor.dialog.SwitchDialog
import com.termux.victor.shell.ExeCommand

class OpenTurnNetworkAdbClickConfig : BaseMenuClickConfig() {
    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.adb_root)
    }

    override fun getString(context: Context?): String? {
        return context?.getString(R.string.vs_network_adb_root)
    }

    override fun onClick(view: View?, context: Context?) {
        val rootBeer = RootBeer(context)
        if (rootBeer.isRooted) {
            //有ROOT
            runRootAdbHttp()
        } else {
            val switchDialog = switchDialogShow(UUtils.getString(R.string.vs_warning),UUtils.getString(R.string.vs_the_root_permission_of_the_machine_is_no))
            switchDialog.show()
            switchDialog.setCancelable(false)
            switchDialog.cancel!!.setOnClickListener { switchDialog.dismiss() }
            switchDialog.ok!!.setOnClickListener {
                switchDialog.dismiss()
                runRootAdbHttp()
            }
            //没有ROOT
        }
    }

    //执行关闭ROOT ADB
    private fun runRootAdbHttpClose(){
        val run = ExeCommand().run("setprop service.adb.tcp.port -1", 6000,true)
        var timeString = ""
        UUtils.runOnThread {
            while (run.isRunning){
                timeString+="\n${run.result}"
            }
        }
        val run1 = ExeCommand().run("stop adbd", 6000,true)
        UUtils.runOnThread {
            while (run1.isRunning){
                timeString+="\n${run.result}"
            }
        }
        val run2 = ExeCommand().run("start adbd", 6000,true)
        UUtils.runOnThread {
            while (run2.isRunning){
                timeString+="\n${run.result}"
            }
        }

        val switchDialog = switchDialogShow(UUtils.getString(R.string.vs_warning),"${UUtils.getString(R.string.vs_adb_command_closed_successfully)}\n")
        switchDialog.show()
        switchDialog.setCancelable(false)
        switchDialog.ok!!.setOnClickListener {
            switchDialog.dismiss()
        }
        switchDialog.cancel!!.setOnClickListener {
            switchDialog.dismiss()
        }
    }

    //执行ROOT ADB
    private fun runRootAdbHttp(){
        val run = ExeCommand().run("setprop service.adb.tcp.port 5555", 6000,true)
        var timeString = ""
        UUtils.runOnThread {
            while (run.isRunning){
                timeString+="\n${run.result}"
            }
        }
        val run1 = ExeCommand().run("stop adbd", 6000,true)
        UUtils.runOnThread {
            while (run1.isRunning){
                timeString+="\n${run.result}"
            }
        }
        val run2 = ExeCommand().run("start adbd", 6000,true)
        UUtils.runOnThread {
            while (run2.isRunning){
                timeString+="\n${run.result}"
            }
        }
        val switchDialog = switchDialogShow(UUtils.getString(R.string.vs_warning),"${UUtils.getString(R.string.vs_the_command_execution_is_complete_please)}\n${UUtils.getHostIP()}:5555")
        switchDialog.show()
        switchDialog.setCancelable(false)
        switchDialog.ok!!.setOnClickListener {
            switchDialog.dismiss()
        }
        switchDialog.cancel!!.setOnClickListener {
            switchDialog.dismiss()
        }
    }

    private fun switchDialogShow(title: String, msg: String): SwitchDialog {
        val switchDialog = SwitchDialog(mContext)
        switchDialog.title!!.text = title
        switchDialog.msg!!.text = msg
        switchDialog.other!!.visibility = View.GONE
        switchDialog.ok!!.text = UUtils.getString(R.string.vs_determine)
        switchDialog.cancel!!.text = UUtils.getString(R.string.vs_cancel)
        switchDialog.show()
        return switchDialog
    }
}
