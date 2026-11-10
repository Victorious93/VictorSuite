package com.termux.victor.ssh;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM with a non-exportable key held in the Android Keystore.
 *
 * <p>Ciphertext format: {@code ks1:<base64 iv>:<base64 ciphertext+tag>}. The key never leaves the
 * Keystore, so a copy of the preferences file alone cannot be decrypted.
 */
public final class KeystoreSecretCipher implements SecretCipher {

    private static final String KEYSTORE = "AndroidKeyStore";
    /** Keystore alias. Deliberately outside the reserved {@code victorpc.} prefix. */
    static final String KEY_ALIAS = "victorsuite.quickssh.password.v1";
    private static final String PREFIX = "ks1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private static KeystoreSecretCipher sInstance;

    public static synchronized KeystoreSecretCipher get() {
        if (sInstance == null) sInstance = new KeystoreSecretCipher();
        return sInstance;
    }

    private KeystoreSecretCipher() {
    }

    private synchronized SecretKey key() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
            return gen.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("keystore unavailable", e);
        }
    }

    @Override
    public String encrypt(String plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    @Override
    public String decrypt(String ciphertext) throws GeneralSecurityException {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new GeneralSecurityException("unknown ciphertext format");
        }
        String[] parts = ciphertext.substring(PREFIX.length()).split(":", -1);
        if (parts.length != 2) throw new GeneralSecurityException("malformed ciphertext");
        byte[] iv;
        byte[] ct;
        try {
            iv = Base64.decode(parts[0], Base64.NO_WRAP);
            ct = Base64.decode(parts[1], Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("malformed ciphertext");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }
}
