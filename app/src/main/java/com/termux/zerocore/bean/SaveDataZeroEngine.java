package com.termux.zerocore.bean;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.xh_lib.utils.LogUtils;
import com.example.xh_lib.utils.UUtils;
import com.termux.zerocore.utils.FileIOUtils;

import java.security.SecureRandom;

public class SaveDataZeroEngine {
    public static int FTP_START_SUCCESS = 8000;
    public static int FTP_START_FAIL = 8001;
    public static int FTP_STARTING = 8000;
    public static int FTP_MIN_PORT = 1001;
    public static int FTP_MAX_PORT = 65534;
    //FTP
    public static String FTP_PASS_WORD = "ftpPassWord";
    public static String FTP_USER_NAME = "ftpUserName";
    public static String FTP_PORT = "ftpPort";
    public static String FTP_CHROOT = "ftpChroot";
    public static String FTP_DEF_USER = "ftp";
    // Random per-install default password instead of a guessable "ftp"/"ftp" pair;
    // shown pre-filled in the FTP settings dialog so the user can see/keep/change it.
    public static String FTP_DEF_PWD = generateDefaultPassword();
    public static String FTP_DEF_PORT = "2121";

    private static String generateDefaultPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    public static String FTP_SDCARD_ROOT = FileIOUtils.INSTANCE.getSdcardPath();
    public static String FTP_ZERO_TERMUX_FILE = FileIOUtils.INSTANCE.getFilePath();

    public static String TAG = "SaveDataZeroEngine";
    public static void putStringData(Context mContext, String key, String values) {
        LogUtils.d(TAG, "putStringData----- key:" + key + " values:" + values);
        SharedPreferences mZeroEngineData = mContext.getSharedPreferences("ZeroEngineData", Context.MODE_PRIVATE);
        mZeroEngineData.edit().putString(key, values).apply();
    }

    public static String getStringData(Context mContext, String key) {
        SharedPreferences mZeroEngineData = mContext.getSharedPreferences("ZeroEngineData", Context.MODE_PRIVATE);
        return mZeroEngineData.getString(key, "def");
    }

    public static boolean isEmpty(Context mContext, String key) {
        String stringData = getStringData(mContext, key);
        return TextUtils.isEmpty(stringData) || "def".equals(stringData);
    }

}
