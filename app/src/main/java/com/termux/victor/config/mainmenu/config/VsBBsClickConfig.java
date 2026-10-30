package com.termux.victor.config.mainmenu.config;

import com.termux.victor.config.VsLinks;
import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_ONLINE_FEATURES;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.R;
import com.termux.victor.activity.WebViewActivity;

public class VsBBsClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_ONLINE_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.drawable.ic_ms_forum);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_help_manual);
    }

    @Override
    public void onClick(View view, Context context) {
        Intent intent2 = new Intent(context, WebViewActivity.class);
        intent2.putExtra("title", context.getString(R.string.vs_help_title));
        intent2.putExtra("content", VsLinks.HELP);
        context.startActivity(intent2);
    }
}
