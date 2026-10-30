package com.termux.victor.config.mainmenu.config

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.victor.utils.PackageMsg

class UnInstallClickConfig: BaseMenuClickConfig() {
    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.uninstall)
    }

    override fun getString(context: Context?): String? {
        return UUtils.getString(R.string.vs_uninstall_label)
    }

    override fun onClick(view: View?, context: Context?) {
        PackageMsg.unInstallALLApk(mContext as Activity)
    }
}
