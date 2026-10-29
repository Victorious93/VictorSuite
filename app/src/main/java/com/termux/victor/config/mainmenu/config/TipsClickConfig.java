package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_COMMON_FUNCTIONS;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.R;
import com.termux.victor.dialog.VsTipsDialog;

/** Side-menu entry: "Tips / Gestures" (gesture help + device/arch info). */
public class TipsClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_COMMON_FUNCTIONS;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.data_msg);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_tips_title);
    }

    @Override
    public void onClick(View view, Context context) {
        VsTipsDialog.show(context);
    }
}
