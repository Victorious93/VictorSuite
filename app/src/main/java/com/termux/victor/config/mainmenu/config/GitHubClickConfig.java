package com.termux.victor.config.mainmenu.config;

import com.termux.victor.config.VsLinks;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_VS_FEATURES;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.termux.R;

public class GitHubClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_VS_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.github);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_source_code_credits);
    }

    @Override
    public void onClick(View view, Context context) {
        Intent intent = new Intent();
        intent.setData(Uri.parse(VsLinks.REPO));//Url 就是你要打开的网址
        intent.setAction(Intent.ACTION_VIEW);
        context.startActivity(intent); //启动浏览器
    }

    @Override
    public void setTextView(TextView textView) {
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
    }
}
