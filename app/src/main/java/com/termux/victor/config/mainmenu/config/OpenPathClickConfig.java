package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_VS_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.victor.util.UUtils;
import com.termux.victor.victorfiles.VictorFilesPanel;
import com.termux.R;

public class OpenPathClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_VS_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.file_launcher_icon);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_opendirectory);
    }

    @Override
    public void onClick(View view, Context context) {
        // Same target as the toolbar File Manager button. Failures (absent, SecurityException,
        // ActivityNotFoundException) all fall through to the "not installed" message.
        if (VictorFilesPanel.launchApp(context) == VictorFilesPanel.LaunchResult.STARTED) return;
        UUtils.showMsg(context.getString(R.string.vs_file_manager_not_installed));
    }
}
