package com.termux.victor.config.mainmenu.config

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.R
import com.termux.victor.config.mainmenu.MainMenuConfig
import com.termux.victor.shellbroker.android.ShellBrokerSettingsActivity

/** Drawer entry: owner controls for remote shell access (trusted apps, privileged switch, live sessions, revoke). */
class ShellBrokerClickConfig : BaseMenuClickConfig() {
    override fun getType(): Int = MainMenuConfig.CODE_VS_FEATURES
    override fun getIcon(context: Context?): Drawable? = context?.getDrawable(R.mipmap.link_ico)
    override fun getString(context: Context?): String? = "Remote shell access"
    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        context.startActivity(Intent(context, ShellBrokerSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
