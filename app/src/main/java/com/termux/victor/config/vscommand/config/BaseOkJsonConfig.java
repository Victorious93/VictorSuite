package com.termux.victor.config.vscommand.config;

import com.termux.victor.util.UUtils;
import com.termux.R;

public abstract class BaseOkJsonConfig extends SimpleConfig {
    String getOkJson() {
        return getJson(0, UUtils.getString(R.string.vs_success), "");
    }
    String getJson(int code, String message, String title) {
        return com.termux.victor.config.vscommand.VsCommandAuth.json(code, message, title);
    }
}
