package com.termux.victor.config.mainmenu.config

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.victor.util.UUtils
import com.termux.victor.util.UUtils.FileCallback
import com.termux.R
import com.termux.victor.dialog.SwitchDialog
import com.termux.victor.url.FileUrl
import java.io.File

class DefBashClickConfig: BaseMenuClickConfig() {
    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.def_bash)
    }

    override fun getString(context: Context?): String? {
        return UUtils.getString(R.string.install_def_bash)
    }

    override fun onClick(view: View?, context: Context?) {
        val switchDialog = SwitchDialog(mContext as Activity)
        switchDialog.createSwitchDialog(UUtils.getString(R.string.install_def_bash_is_writer))
        switchDialog.ok?.setOnClickListener {
            val file = File(FileUrl.smsBashrcFile)
            val open = UUtils.getContext().assets.open("bash.bashrc")
            UUtils.writerFileRawInput(file, open, object : FileCallback {
                override fun callBack(msg: String?, state: Boolean) {
                    if (state) {
                        UUtils.showMsg(UUtils.getString(R.string.install_def_bash_msg_ok))
                    } else {
                        UUtils.showMsg(UUtils.getString(R.string.install_def_bash_msg_error))
                    }
                    switchDialog.dismiss()
                }

            })
        }
        switchDialog.show()
    }
}
