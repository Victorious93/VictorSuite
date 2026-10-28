package com.termux.victor.config.ztcommand.config;

import android.content.Context;

import com.termux.victor.util.UUtils;
import com.termux.R;

public class VersionConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        return UUtils.getString(R.string.zt_command_version);
    }

    @Override
    public int getId() {
        return VsKeyConstants.ZT_ID_VERSION;
    }
}
