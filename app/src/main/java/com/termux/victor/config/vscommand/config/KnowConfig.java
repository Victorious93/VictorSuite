package com.termux.victor.config.vscommand.config;

import android.content.Context;

import com.termux.victor.util.UUtils;
import com.termux.R;

public class KnowConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        try {
            String[] sqlit = command.split(" ");
            if (sqlit.length >= 2) {
                return UUtils.getString(R.string.vs_cmd_error) + sqlit[0] + "\"";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return UUtils.getString(R.string.vs_cmd_error) + command + "\"";
    }

    @Override
    public int getId() {
        return VsKeyConstants.VS_ID_KNOW;
    }
}
