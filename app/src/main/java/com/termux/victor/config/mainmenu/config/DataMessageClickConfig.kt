package com.termux.victor.config.mainmenu.config

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.view.View
import com.termux.victor.util.LogUtils
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.victor.dialog.MingLShowDialog
import com.termux.victor.utils.FileIOUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataMessageClickConfig: BaseMenuClickConfig() {
    companion object {
        val TAG = DataMessageClickConfig::class.simpleName
    }
    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.data_msg)
    }

    override fun getString(context: Context?): String? {
       return  UUtils.getString(R.string.create_data_message)
    }

    override fun onClick(view: View?, context: Context?) {
        val termuxActivity: TermuxActivity = context as TermuxActivity
        val mingLShowDialog = MingLShowDialog(context!!)
        mingLShowDialog.mTitleCard.visibility = View.GONE
        mingLShowDialog.mSwitchCard.visibility = View.GONE
        mingLShowDialog.edit_text.hint = UUtils.getString(R.string.data_message_hint)
        val dataMessageFileString = FileIOUtils.getDataMessageFileString()
        if (!(dataMessageFileString.isNullOrEmpty())) {
            mingLShowDialog.edit_text.setText(dataMessageFileString)
        } else {
            mingLShowDialog.edit_text.setText(UUtils.getString(R.string.zt_data_info_content))
        }
        mingLShowDialog.start.setOnClickListener {
            mingLShowDialog.dismiss()
            val text = mingLShowDialog.edit_text.text
            //设置自动保存，关闭Dialog之后也会自动保存
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    FileIOUtils.saveDataMessageFileString(text.toString())
                }
                withContext(Dispatchers.Main) {
                    termuxActivity.initDataMsgInfo()
                }
            }
            UUtils.showMsg(UUtils.getString(R.string.保存成功))
        }
        mingLShowDialog.show()
    }
}
