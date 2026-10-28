package com.termux.victor.config.mainmenu.config

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.victor.dialog.PluginListDialog

/**
 * Opens the plugin manager (com.termux.victor.plugin) - registering, enabling, and
 * launching external tools the user has installed and authorized themselves. See
 * PLUGINS.md for the framework's scope and non-goals.
 */
class PluginManagerClickConfig : BaseMenuClickConfig() {
    companion object {
        val TAG: String = PluginManagerClickConfig::class.java.simpleName
    }

    override fun getType(): Int {
        return com.termux.victor.config.mainmenu.MainMenuConfig.CODE_COMMON_FUNCTIONS
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.settings)
    }

    override fun getString(context: Context?): String? {
        return UUtils.getString(R.string.plugin_manager_title)
    }

    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        PluginListDialog(context).show()
    }
}
