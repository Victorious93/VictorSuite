package com.termux.victor.config.vscommand.config;

import static com.termux.victor.config.vscommand.config.VsKeyConstants.VS_ID_X11_KEYBOARD_SHOW;

import android.content.Context;

public class X11KeyBoardShowConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        return "";
    }

    @Override
    public int getId() {
        return VS_ID_X11_KEYBOARD_SHOW;
    }

    @Override
    public boolean isForWard() {
        return true;
    }
}
