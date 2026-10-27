package com.termux.zerocore.ftp.new_ftp.utils;

import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.ftpserver.ftplet.Authority;

import java.util.ArrayList;
import java.util.List;

/** Maps an FTP user's "writable" flag to Apache FtpServer authorities (read-only users get none). */
public final class FtpAuthorities {
    private FtpAuthorities() {
    }

    public static List<Authority> forUser(boolean writable) {
        List<Authority> authorities = new ArrayList<>();
        if (writable) {
            authorities.add(new WritePermission());
        }
        return authorities;
    }
}
