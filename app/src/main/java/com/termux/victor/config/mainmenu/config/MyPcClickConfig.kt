package com.termux.victor.config.mainmenu.config

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.R
import com.termux.victor.config.mainmenu.MainMenuConfig
import com.termux.victor.pc.MyPcActivity

/**
 * Drawer entry for "My PC": a trusted PC mirrors and controls this phone with scrcpy over
 * wireless ADB (Wi-Fi or Tailscale). Opens [MyPcActivity], which shows the warning first.
 *
 * Not related to [RemoteConnectionClickConfig], which opens the in-app scrcpy viewer that shows
 * a remote device on this phone (the opposite direction).
 */
class MyPcClickConfig : BaseMenuClickConfig() {
    override fun getType(): Int {
        return MainMenuConfig.CODE_VS_FEATURES
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.link_ico)
    }

    override fun getString(context: Context?): String? {
        return context?.getString(R.string.my_pc_menu_label) ?: "My PC"
    }

    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        val intent = Intent(context, MyPcActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
