package com.termux.victor.config.ztcommand.config;

import android.content.Context;

import com.termux.victor.util.UUtils;
import com.termux.R;

public class KnowConfig extends SimpleConfig {
    @Override
    public String getCommand(Context context, String command) {
        try {
            String[] sqlit = command.split(" ");
            if (sqlit.length >= 2) {
                return UUtils.getString(R.string.zt_command_error) + sqlit[0] + "\"";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return UUtils.getString(R.string.zt_command_error) + command + "\"";
    }

    @Override
    public int getId() {
        return VsKeyConstants.ZT_ID_KNOW;
    }
}
