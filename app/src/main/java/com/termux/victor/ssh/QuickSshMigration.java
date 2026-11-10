package com.termux.victor.ssh;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.security.GeneralSecurityException;

/**
 * One-time, idempotent migration of the stored quick-SSH device list
 * ({@code SharedPreferences("xinhao")}, key {@code ssh_device_list}).
 *
 * <ul>
 *     <li>A plaintext {@code password} is encrypted into {@code passwordEnc} and removed. The
 *     ciphertext is decrypted and compared before the plaintext is dropped, so nothing is lost.</li>
 *     <li>Entries without an {@code id} get one (used to name the per-entry known_hosts file).</li>
 *     <li>Unknown fields are kept as they are.</li>
 *     <li>If anything fails, {@link MigrationException} is thrown and the caller must leave the
 *     stored value untouched.</li>
 * </ul>
 *
 * <p>Running it again on its own output returns {@code changed == false}.
 *
 * <p>Pure Java (Gson only) so it can be unit tested on the JVM.
 */
public final class QuickSshMigration {

    public static final String FIELD_ID = "id";
    public static final String FIELD_LEGACY_PASSWORD = "password";
    public static final String FIELD_PASSWORD_ENC = "passwordEnc";

    /** Supplies new entry ids. */
    public interface IdSource {
        String newId();
    }

    public static final class Result {
        public final String json;
        public final boolean changed;

        Result(String json, boolean changed) {
            this.json = json;
            this.changed = changed;
        }
    }

    public static final class MigrationException extends Exception {
        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private QuickSshMigration() {
    }

    /** True when the stored value is the "nothing saved" marker used by {@code SaveData}. */
    public static boolean isEmptyValue(String json) {
        return json == null || json.trim().isEmpty() || "def".equals(json);
    }

    public static Result migrate(String json, SecretCipher cipher, IdSource ids) throws MigrationException {
        if (isEmptyValue(json)) {
            return new Result(json, false);
        }
        JsonArray array;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                throw new MigrationException("device list is not a JSON array", null);
            }
            array = root.getAsJsonArray();
        } catch (MigrationException e) {
            throw e;
        } catch (RuntimeException e) {
            // Do not chain the parser exception: its message can quote the stored JSON.
            throw new MigrationException("device list is not valid JSON", null);
        }

        boolean changed = false;
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if (!el.isJsonObject()) {
                throw new MigrationException("device entry " + i + " is not an object", null);
            }
            JsonObject obj = el.getAsJsonObject();

            String id = stringOrNull(obj, FIELD_ID);
            if (id == null || id.isEmpty()) {
                obj.addProperty(FIELD_ID, ids.newId());
                changed = true;
            }

            boolean hasLegacyField = obj.has(FIELD_LEGACY_PASSWORD);
            String legacy = stringOrNull(obj, FIELD_LEGACY_PASSWORD);
            String enc = stringOrNull(obj, FIELD_PASSWORD_ENC);

            if (legacy != null && !legacy.isEmpty()) {
                boolean encMatches = false;
                if (enc != null && !enc.isEmpty()) {
                    try {
                        encMatches = legacy.equals(cipher.decrypt(enc));
                    } catch (GeneralSecurityException | RuntimeException e) {
                        encMatches = false;
                    }
                }
                if (!encMatches) {
                    try {
                        String newEnc = cipher.encrypt(legacy);
                        if (!legacy.equals(cipher.decrypt(newEnc))) {
                            throw new MigrationException("encryption round trip failed for entry " + i, null);
                        }
                        obj.addProperty(FIELD_PASSWORD_ENC, newEnc);
                    } catch (GeneralSecurityException | RuntimeException e) {
                        throw new MigrationException("could not encrypt password for entry " + i, e);
                    }
                }
                obj.remove(FIELD_LEGACY_PASSWORD);
                changed = true;
            } else if (hasLegacyField) {
                // Empty or null legacy value: nothing to keep.
                obj.remove(FIELD_LEGACY_PASSWORD);
                changed = true;
            }
        }
        return new Result(changed ? new Gson().toJson(array) : json, changed);
    }

    private static String stringOrNull(JsonObject obj, String field) {
        JsonElement e = obj.get(field);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
        return e.getAsString();
    }
}
