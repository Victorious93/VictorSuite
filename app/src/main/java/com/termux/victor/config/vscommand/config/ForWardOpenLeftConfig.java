package com.termux.victor.config.vscommand.config;

import static com.termux.victor.config.vscommand.config.VsKeyConstants.VS_ID_LEFT;

import android.content.Context;

public class ForWardOpenLeftConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        return "";
    }

    @Override
    public int getId() {
        return VS_ID_LEFT;
    }

    @Override
    public boolean isForWard() {
        return true;
    }

}
