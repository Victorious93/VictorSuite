package com.termux.zerocore.config.mainmenu.config

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.R
import com.termux.zerocore.hak5.Hak5CompanionActivity

/**
 * Drawer entry for the Hak5 companion (WiFi Pineapple Tetra over Wi-Fi/LAN).
 * Opens [Hak5CompanionActivity]; Plunder Bug USB work stays in RootForge.
 */
class Hak5CompanionClickConfig : BaseMenuClickConfig() {
    override fun getType(): Int {
        return com.termux.zerocore.config.mainmenu.MainMenuConfig.CODE_COMMON_FUNCTIONS
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.link_ico)
    }

    override fun getString(context: Context?): String? {
        return context?.getString(R.string.hak5_companion_title) ?: "Hak5 Companion"
    }

    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        val intent = Intent(context, Hak5CompanionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
