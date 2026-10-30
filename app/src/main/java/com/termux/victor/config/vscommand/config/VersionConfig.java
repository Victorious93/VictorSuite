package com.termux.victor.config.vscommand.config;

import android.content.Context;

import com.termux.victor.util.UUtils;
import com.termux.R;

public class VersionConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        return UUtils.getString(R.string.vs_command_version);
    }

    @Override
    public int getId() {
        return VsKeyConstants.VS_ID_VERSION;
    }
}
