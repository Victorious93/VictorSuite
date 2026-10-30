package com.termux.victor.config.vscommand.config;

import android.content.Context;

import com.termux.victor.config.vscommand.VsSocketService;

public abstract class SimpleConfig implements VsConfig {
    @Override
    public boolean isForWard() {
        return false;
    }

    @Override
    public String getCommandForWard(Context context, String command) {
        return "";
    }

    @Override
    public void sendSocketMessage(VsSocketService.ClientHandler clientHandler, Context context) {

    }
}
