package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_BEAUTIFICATION_FUNCTION;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.R;
import com.termux.victor.activity.FontActivity;

public class FontSettingsClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_BEAUTIFICATION_FUNCTION;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.ziti_font_ico);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_font_settings);
    }

    @Override
    public void onClick(View view, Context context) {
        context.startActivity(new Intent(context, FontActivity.class));
    }
}
