package com.termux.victor.ssh;

import java.security.GeneralSecurityException;

/** Encrypts and decrypts short secrets (quick-SSH passwords). */
public interface SecretCipher {

    String encrypt(String plaintext) throws GeneralSecurityException;

    String decrypt(String ciphertext) throws GeneralSecurityException;
}
