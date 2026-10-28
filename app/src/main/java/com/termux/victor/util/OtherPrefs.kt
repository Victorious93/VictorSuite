package com.termux.victor.util

import android.content.Context

/**
 * Small string store in the `start_flash` SharedPreferences file.
 *
 * Written for VictorSuite to replace `com.example.xh_lib.utils.SaveData`. It uses the same
 * preferences file and default value, so settings saved by earlier builds are still read.
 */
object OtherPrefs {

    private const val FILE = "start_flash"
    private const val DEFAULT = "def"

    private fun prefs() = UUtils.getContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @JvmStatic
    fun saveStringOther(key: String, value: String) {
        prefs().edit().putString(key, value).apply()
    }

    /** Returns the stored value, or `"def"` when the key has never been saved. */
    @JvmStatic
    fun getStringOther(key: String): String? = prefs().getString(key, DEFAULT)
}
