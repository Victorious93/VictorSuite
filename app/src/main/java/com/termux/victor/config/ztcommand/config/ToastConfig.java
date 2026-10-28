package com.termux.victor.config.ztcommand.config;

import android.content.Context;
import android.widget.Toast;

import com.termux.victor.util.UUtils;

public class ToastConfig extends BaseOkJsonConfig {
    @Override
    public String getCommand(Context context, String command) {
        UUtils.runOnUIThread(() -> {
            Toast.makeText(context, command.split(" ")[1], Toast.LENGTH_SHORT).show();
        });
        return getOkJson();
    }

    @Override
    public int getId() {
        return VsKeyConstants.ZT_ID_TOAST;
    }

}
