package com.termux.victor.ssh;

import android.content.Context;
import android.system.Os;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.termux.victor.bean.SSHDeviceBean;
import com.termux.victor.util.UUtils;
import com.termux.victor.utils.SSHKeyUtils;
import com.termux.victor.utils.SaveData;
import com.zp.z_file.util.LogUtils;

import java.io.File;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage and command building for the quick-SSH device list.
 *
 * <p>On every load the stored list is passed through {@link QuickSshMigration} (idempotent); if it
 * changed, the result is written back synchronously. Passwords are only ever stored as
 * {@link KeystoreSecretCipher} ciphertext.
 */
public final class QuickSshStore {

    private static final String TAG = "QuickSsh";
    public static final String KEY_SSH_LIST = "ssh_device_list";
    private static final String KNOWN_HOSTS_DIR = "victor-ssh/known_hosts";

    private QuickSshStore() {
    }

    private static Context ctx() {
        return UUtils.getContext();
    }

    /** Loads the device list, migrating legacy plaintext passwords first. Never returns null. */
    public static synchronized List<SSHDeviceBean> load() {
        String json = SaveData.getData(KEY_SSH_LIST);
        if (QuickSshMigration.isEmptyValue(json)) return new ArrayList<>();
        try {
            QuickSshMigration.Result r = QuickSshMigration.migrate(json, KeystoreSecretCipher.get(),
                () -> UUID.randomUUID().toString());
            if (r.changed) {
                if (SaveData.saveDataSync(KEY_SSH_LIST, r.json)) {
                    json = r.json;
                    LogUtils.i(TAG, "quick ssh: device list migrated (passwords encrypted)");
                } else {
                    LogUtils.e(TAG, "quick ssh: could not write migrated device list; will retry");
                }
            }
        } catch (QuickSshMigration.MigrationException e) {
            // Leave the stored value untouched (lossless); retried on the next load.
            LogUtils.e(TAG, "quick ssh: migration skipped: " + QuickSshRedactor.describe(e)
                + (e.getCause() != null ? " (" + QuickSshRedactor.describe(e.getCause()) + ")" : ""));
        }
        tightenKeyPermissions();
        try {
            Type listType = new TypeToken<List<SSHDeviceBean>>() {}.getType();
            List<SSHDeviceBean> list = new Gson().fromJson(json, listType);
            if (list == null) return new ArrayList<>();
            for (SSHDeviceBean b : list) {
                // Only when migration could not run: a stable fallback so the pin does not move.
                if (TextUtils.isEmpty(b.getId())) b.setId(fallbackId(b));
            }
            return list;
        } catch (Exception e) {
            LogUtils.e(TAG, "quick ssh: list parse error: " + e.getClass().getSimpleName());
            return new ArrayList<>();
        }
    }

    public static synchronized void save(List<SSHDeviceBean> list) {
        SaveData.saveData(KEY_SSH_LIST, new Gson().toJson(list));
    }

    /**
     * Sets (or clears, for empty input) the bean's password as Keystore ciphertext.
     *
     * @return false if the password could not be encrypted; nothing is stored in that case.
     */
    @SuppressWarnings("deprecation")
    public static boolean setPassword(SSHDeviceBean bean, String plaintext) {
        bean.clearLegacyPassword();
        if (TextUtils.isEmpty(plaintext)) {
            bean.setPasswordEnc(null);
            return true;
        }
        try {
            bean.setPasswordEnc(KeystoreSecretCipher.get().encrypt(plaintext));
            return true;
        } catch (GeneralSecurityException | RuntimeException e) {
            bean.setPasswordEnc(null);
            LogUtils.e(TAG, "quick ssh: password encryption failed: " + QuickSshRedactor.describe(e));
            return false;
        }
    }

    /** Decrypted password, the legacy value for unmigrated data, or null. */
    @SuppressWarnings("deprecation")
    public static String getPassword(SSHDeviceBean bean) {
        if (!TextUtils.isEmpty(bean.getPasswordEnc())) {
            try {
                return KeystoreSecretCipher.get().decrypt(bean.getPasswordEnc());
            } catch (GeneralSecurityException | RuntimeException e) {
                LogUtils.e(TAG, "quick ssh: password decryption failed: " + QuickSshRedactor.describe(e));
                return null;
            }
        }
        String legacy = bean.getLegacyPassword();
        return TextUtils.isEmpty(legacy) ? null : legacy;
    }

    /** Per-entry known_hosts file in app-private storage ({@code files/victor-ssh/known_hosts/<id>}). */
    public static File knownHostsFile(SSHDeviceBean bean) {
        String id = bean.getId();
        if (TextUtils.isEmpty(id)) {
            id = fallbackId(bean);
            bean.setId(id);
        }
        return new File(new File(ctx().getFilesDir(), KNOWN_HOSTS_DIR), id.replaceAll("[^a-zA-Z0-9_\\-]", "_"));
    }

    private static String fallbackId(SSHDeviceBean b) {
        String seed = b.getHost() + ":" + b.getPort();
        return "h-" + UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The user's own ~/.ssh/known_hosts, consulted second so keys trusted there still count. */
    public static File userKnownHostsFile() {
        return new File(SSHKeyUtils.getSSHDir(), "known_hosts");
    }

    public static boolean hasPinnedHostKey(SSHDeviceBean bean) {
        File f = knownHostsFile(bean);
        return f.isFile() && f.length() > 0;
    }

    /** Explicit "forget host key": removes the pinned key so the next connect trusts on first use. */
    public static boolean forgetHostKey(SSHDeviceBean bean) {
        File f = knownHostsFile(bean);
        return !f.exists() || f.delete();
    }

    /**
     * Builds the command to type into the terminal. Never contains the password.
     *
     * @param passwordFifo FIFO path from {@link PasswordFifo#offer}, or null to let ssh prompt.
     */
    public static String buildConnectCommand(SSHDeviceBean bean, String passwordFifo) {
        File known = knownHostsFile(bean);
        File dir = known.getParentFile();
        if (dir != null && !dir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        try {
            if (dir != null) Os.chmod(dir.getAbsolutePath(), 0700);
        } catch (Exception ignored) {
        }
        QuickSshCommandBuilder b = new QuickSshCommandBuilder()
            .host(bean.getHost())
            .port(bean.getPort())
            .username(bean.getUsername())
            .knownHostsFile(known.getAbsolutePath())
            .knownHostsFile(userKnownHostsFile().getAbsolutePath());
        if (bean.isUseKey()) {
            b.identityFile(SSHKeyUtils.getKeyFile(bean.getAlias()).getAbsolutePath());
        } else if (!TextUtils.isEmpty(passwordFifo)) {
            b.passwordFifo(passwordFifo);
        }
        return b.build();
    }

    /** Key migration: keep existing keys working, but make sure ~/.ssh is 0700 and keys are 0600. */
    private static void tightenKeyPermissions() {
        File dir = SSHKeyUtils.getSSHDir();
        if (!dir.isDirectory()) return;
        try {
            Os.chmod(dir.getAbsolutePath(), 0700);
            File[] keys = dir.listFiles((d, name) -> name.endsWith(".key"));
            if (keys != null) {
                for (File k : keys) Os.chmod(k.getAbsolutePath(), 0600);
            }
        } catch (Exception e) {
            LogUtils.d(TAG, "quick ssh: chmod: " + e.getClass().getSimpleName());
        }
    }
}
