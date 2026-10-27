package com.termux.zerocore.config.mainmenu.config;

import static com.termux.zerocore.config.mainmenu.MainMenuConfig.CODE_ZT_FEATURES;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.example.xh_lib.utils.UUtils;
import com.termux.R;

public class OpenPathClickConfig extends BaseMenuClickConfig {
    private static final String[] FILE_MANAGER_PACKAGES = {
        "ai.victorious.filemanager",
        "ai.victorious.filemanager.debug"
    };

    @Override
    public int getType() {
        return CODE_ZT_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.file_launcher_icon);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.打开目录);
    }

    @Override
    public void onClick(View view, Context context) {
        // Same target as the toolbar File Manager button: VictorFiles.
        PackageManager pm = context.getPackageManager();
        for (String pkg : FILE_MANAGER_PACKAGES) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(launch);
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        UUtils.showMsg(context.getString(R.string.vs_file_manager_not_installed));
    }
}
