package com.termux.victor.config.vscommand.config;

import static com.termux.victor.config.vscommand.config.VsKeyConstants.VS_ID_HELP;

import android.content.Context;

import com.termux.victor.util.UUtils;
import com.termux.R;

public class HelpConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        return UUtils.getString(R.string.vs_command_help);
    }

    @Override
    public int getId() {
        return VS_ID_HELP;
    }
}
