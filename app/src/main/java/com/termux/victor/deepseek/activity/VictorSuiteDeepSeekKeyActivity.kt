package com.termux.victor.deepseek.activity

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.termux.R

class VictorSuiteDeepSeekKeyActivity : AppCompatActivity() {
    private val mTitle1 by lazy { findViewById<TextView>(R.id.title_1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_victor_suite_deep_seek_key)
        // Do not open an external website from this screen.
        mTitle1.text = getString(R.string.deepseek_settings_ai_get_deepseek_key_1)
    }
}
