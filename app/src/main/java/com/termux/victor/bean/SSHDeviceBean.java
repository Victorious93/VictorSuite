package com.termux.victor.bean;

import java.io.Serializable;
import java.util.UUID;

/**
 * One quick-SSH device, stored as JSON in {@code SharedPreferences("xinhao")} / {@code ssh_device_list}.
 *
 * <p>Passwords are stored only as Keystore ciphertext in {@link #passwordEnc}
 * (see {@code com.termux.victor.ssh.QuickSshStore}). {@link #password} is the legacy plaintext
 * field: it is only read from data written by older versions, is migrated away on first load,
 * and is never written by this version.
 */
public class SSHDeviceBean implements Serializable {
    /** Stable id; names the per-entry known_hosts file. */
    private String id;
    private String alias;
    private String host;
    private int port = 22;
    private String username;
    /** Legacy plaintext password (pre-migration data only). Never set by new code. */
    @Deprecated
    private String password;
    /** Keystore AES-GCM ciphertext of the password, or null. */
    private String passwordEnc;
    private String keyPath;
    private String command;
    private boolean useKey = false;

    public SSHDeviceBean() {
    }

    public SSHDeviceBean(String alias, String host, int port, String username, boolean useKey) {
        this.id = UUID.randomUUID().toString();
        this.alias = alias;
        this.host = host;
        this.port = port;
        this.username = username;
        this.useKey = useKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /** Legacy plaintext password from unmigrated data, or null. */
    @Deprecated
    public String getLegacyPassword() {
        return password;
    }

    @Deprecated
    public void clearLegacyPassword() {
        this.password = null;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    public boolean hasPassword() {
        return (passwordEnc != null && !passwordEnc.isEmpty()) || (password != null && !password.isEmpty());
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public boolean isUseKey() {
        return useKey;
    }

    public void setUseKey(boolean useKey) {
        this.useKey = useKey;
    }
}
