package com.termux.victor.config.vscommand.config;

import static com.termux.victor.config.vscommand.config.VsKeyConstants.VS_ID_X11_COMMAND_SHOW;

import android.content.Context;

public class X11CommandShowConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        return "";
    }

    @Override
    public int getId() {
        return VS_ID_X11_COMMAND_SHOW;
    }

    @Override
    public boolean isForWard() {
        return true;
    }

}
