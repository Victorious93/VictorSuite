package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_COMMON_FUNCTIONS;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.victor.util.UUtils;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.code.CodeString;
import com.termux.victor.dialog.LoadingDialog;
import com.termux.victor.url.FileUrl;

import java.io.File;

public class ReleaseLinuxVersionClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_COMMON_FUNCTIONS;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.linux_ico);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_release_version);
    }

    @Override
    public void onClick(View view, Context context) {
        LoadingDialog loadingDialog = new LoadingDialog(context);
        loadingDialog.show();
        loadingDialog.setCancelable(false);
        UUtils.runOnThread(new Runnable() {
            @Override
            public void run() {
                UUtils.writerFile("linux/termux_linux_toolx.zip", new File(FileUrl.INSTANCE.getMainHomeUrl(), "/termux_linux_toolx.zip"));
                UUtils.runOnThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingDialog.dismiss();
                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunLinuxSh());
                    }
                });
            }
        });
    }
}
