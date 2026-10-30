package com.termux.victor.config.mainmenu.config;

import com.termux.victor.config.VsLinks;
import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_ONLINE_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.R;
import com.termux.app.TermuxActivity;

public class DownLoadClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_ONLINE_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.download_http);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_download_station);
    }

    @Override
    public void onClick(View view, Context context) {
        TermuxActivity termuxActivity = (TermuxActivity) context;
        termuxActivity.startHttp1(VsLinks.FEED_REPOSITORY);
    }
}
