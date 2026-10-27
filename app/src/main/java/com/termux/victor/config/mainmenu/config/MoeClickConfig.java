package com.termux.victor.config.mainmenu.config;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.example.xh_lib.utils.UUtils;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.code.CodeString;
import com.termux.victor.config.mainmenu.MainMenuConfig;
import com.termux.victor.dialog.SwitchDialog;
import com.termux.victor.utermux_windows.qemu.dialog.EndDialog;

// MOE全能
public class MoeClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return MainMenuConfig.CODE_COMMON_FUNCTIONS;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.moe_ico);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.MOE全能);
    }

    @Override
    public void onClick(View view, Context context) {
        SwitchDialog switchDialog = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.zt_moe_remove), context);
        switchDialog.getCancel().setOnClickListener(v1 -> switchDialog.dismiss());
        switchDialog.getOk().setOnClickListener(v12 -> {
            switchDialog.dismiss();
            com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunMoeSh());
        });
    }
}
