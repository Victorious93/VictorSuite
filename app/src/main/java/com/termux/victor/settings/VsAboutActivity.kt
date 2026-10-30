package com.termux.victor.settings

import com.termux.victor.config.VsLinks

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.cardview.widget.CardView
import com.termux.victor.util.UUtils
import com.termux.R

class VsAboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vsabout)
        findViewById<CardView>(R.id.vs_termux_github_card_view).setOnClickListener {
            UUtils.startUrl(VsLinks.REPO)
        }

        // The VsEngineManage add-on is a third-party APK from an upstream personal
        // repo; we no longer link users to download it. Hide its card.
        findViewById<CardView>(R.id.vs_core_manage_termux_github_card_view).visibility = android.view.View.GONE
    }
}
