package com.termux.victor.ftp.new_ftp.config;

import com.termux.victor.bean.VsEngineData;
import com.termux.victor.url.FileUrl;

public class FTP_CONFIG {
    public static String USERNAME = "ftp";
    // Falls back to the same randomly generated per-install default as the FTP settings
    // dialog (see VsEngineData.FTP_DEF_PWD) instead of a guessable static literal.
    public static String PASSWORD = VsEngineData.FTP_DEF_PWD;
    public static String PATH =  FileUrl.INSTANCE.getMainFilesUrl();
    public static Boolean WRITABLE =  true;
    public static Long ID =  888888L;
}
