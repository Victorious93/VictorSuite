package com.termux.victor.config.ztcommand.config;

import static com.termux.victor.config.ztcommand.config.VsKeyConstants.ZT_ID_QR_CODE_ENCODER_CONFIG;

import android.content.Context;
import android.content.Intent;

import com.example.xh_lib.utils.UUtils;
import com.termux.victor.config.ztcommand.VsSocketService;
import com.termux.victor.config.ztcommand.activity.SocketBaseActivity;

public class QRCodeEncoderConfig extends BaseOkJsonConfig {
    private static VsSocketService.ClientHandler mClientHandler;
    @Override
    public String getCommand(Context context, String command) {
        return null;
    }

    @Override
    public int getId() {
        return ZT_ID_QR_CODE_ENCODER_CONFIG;
    }

    @Override
    public void sendSocketMessage(VsSocketService.ClientHandler clientHandler, Context context) {
        mClientHandler = clientHandler;
        UUtils.runOnUIThread(() -> {
            Intent intent = new Intent(context, SocketBaseActivity.class);
            intent.putExtra(SocketBaseActivity.OPEN_TYPE, SocketBaseActivity.OPEN_CAMERA_QR_TYPE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });
    }

    public static void sendMessage(int code, String message) {
        UUtils.runOnThread(() -> {
            mClientHandler.sendSocketMessage(new QRCodeEncoderConfig().getJson(code, message, ""));
            mClientHandler = null;
        });
    }
}
