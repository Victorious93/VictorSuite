package com.termux.zerocore.config.mainmenu.config

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.termux.R
import com.termux.zerocore.config.mainmenu.MainMenuConfig
import com.termux.zerocore.url.FileUrl
import com.termux.zerocore.utils.SingletonCommunicationUtils
import java.io.File
import java.io.IOException

/**
 * Drawer button that logs in to a proot-distro container, installing it first if needed.
 *
 * Copies the bundled `scripts/distro-login.sh` (an app asset, see app/build.gradle) to
 * `~/distro-login.sh` and runs it in the terminal so the user sees the progress and lands in
 * the distribution's shell. The menu loads these by class name with a no-argument constructor,
 * hence one small subclass per distribution.
 */
open class DistroLoginClickConfig(private val key: String, private val label: String) : BaseMenuClickConfig() {
    override fun getType(): Int {
        return MainMenuConfig.CODE_LINUX_LOGIN
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.linux_ico)
    }

    override fun getString(context: Context?): String? {
        return label
    }

    override fun onClick(view: View?, context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        val target = File(FileUrl.mainHomeUrl, SCRIPT_NAME)
        Thread {
            try {
                appContext.assets.open(SCRIPT_NAME).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (!target.setExecutable(true, true)) {
                    throw IOException("could not mark $target executable")
                }
                mainHandler.post {
                    SingletonCommunicationUtils.getInstance()
                        .getmSingletonCommunicationListener()
                        .sendTextToTerminal("bash ~/$SCRIPT_NAME $key\n")
                }
            } catch (e: IOException) {
                mainHandler.post {
                    Toast.makeText(appContext, "Could not prepare the $label login script: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    companion object {
        private const val SCRIPT_NAME = "distro-login.sh"
    }
}

class UbuntuLoginClickConfig : DistroLoginClickConfig("ubuntu", "Ubuntu")

class KaliLoginClickConfig : DistroLoginClickConfig("kali", "Kali Linux")

class ParrotLoginClickConfig : DistroLoginClickConfig("parrot", "Parrot OS")

class BlackArchLoginClickConfig : DistroLoginClickConfig("blackarch", "BlackArch")
