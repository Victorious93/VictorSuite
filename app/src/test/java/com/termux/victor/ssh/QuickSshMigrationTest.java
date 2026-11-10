package com.termux.victor.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;

public class QuickSshMigrationTest {

    /** Reversible fake: "enc:" + reversed text. */
    static final class FakeCipher implements SecretCipher {
        int encrypts;

        @Override
        public String encrypt(String plaintext) {
            encrypts++;
            return "enc:" + new StringBuilder(plaintext).reverse();
        }

        @Override
        public String decrypt(String ciphertext) throws GeneralSecurityException {
            if (!ciphertext.startsWith("enc:")) throw new GeneralSecurityException("bad");
            return new StringBuilder(ciphertext.substring(4)).reverse().toString();
        }
    }

    static final class BrokenCipher implements SecretCipher {
        @Override
        public String encrypt(String plaintext) throws GeneralSecurityException {
            throw new GeneralSecurityException("keystore down");
        }

        @Override
        public String decrypt(String ciphertext) throws GeneralSecurityException {
            throw new GeneralSecurityException("keystore down");
        }
    }

    private static QuickSshMigration.IdSource ids() {
        AtomicInteger n = new AtomicInteger();
        return () -> "id-" + n.incrementAndGet();
    }

    private static final String LEGACY = "[" +
        "{\"alias\":\"pc\",\"host\":\"10.0.0.2\",\"port\":22,\"username\":\"root\",\"password\":\"it's\",\"useKey\":false}," +
        "{\"alias\":\"nas\",\"host\":\"nas.lan\",\"port\":2222,\"username\":\"don\",\"useKey\":true,\"keyPath\":\"/k\"}," +
        "{\"alias\":\"empty\",\"host\":\"h\",\"port\":22,\"username\":\"u\",\"password\":\"\",\"useKey\":false}" +
        "]";

    @Test
    public void encryptsPlaintextAndRemovesIt() throws Exception {
        FakeCipher c = new FakeCipher();
        QuickSshMigration.Result r = QuickSshMigration.migrate(LEGACY, c, ids());
        assertTrue(r.changed);
        assertFalse(r.json.contains("it's"));
        assertFalse(r.json.contains("\"password\""));

        JsonArray a = JsonParser.parseString(r.json).getAsJsonArray();
        assertEquals(3, a.size());
        JsonObject pc = a.get(0).getAsJsonObject();
        assertEquals("it's", c.decrypt(pc.get("passwordEnc").getAsString())); // lossless
        assertEquals("id-1", pc.get("id").getAsString());
        // Other fields preserved.
        assertEquals("10.0.0.2", pc.get("host").getAsString());
        JsonObject nas = a.get(1).getAsJsonObject();
        assertEquals("/k", nas.get("keyPath").getAsString());
        assertEquals(2222, nas.get("port").getAsInt());
        assertTrue(nas.get("useKey").getAsBoolean());
        assertFalse(nas.has("passwordEnc"));
        assertFalse(a.get(2).getAsJsonObject().has("passwordEnc"));
    }

    @Test
    public void isIdempotent() throws Exception {
        FakeCipher c = new FakeCipher();
        QuickSshMigration.Result first = QuickSshMigration.migrate(LEGACY, c, ids());
        int encrypts = c.encrypts;
        QuickSshMigration.Result second = QuickSshMigration.migrate(first.json, c, ids());
        assertFalse(second.changed);
        assertSame(first.json, second.json);
        assertEquals(encrypts, c.encrypts);
    }

    @Test
    public void keepsExistingIdsAndCiphertext() throws Exception {
        String json = "[{\"id\":\"keep\",\"alias\":\"a\",\"host\":\"h\",\"passwordEnc\":\"enc:wp\"}]";
        QuickSshMigration.Result r = QuickSshMigration.migrate(json, new FakeCipher(), ids());
        assertFalse(r.changed);
    }

    @Test
    public void legacyAlongsideMatchingCiphertextJustDropsPlaintext() throws Exception {
        FakeCipher c = new FakeCipher();
        String json = "[{\"id\":\"x\",\"password\":\"pw\",\"passwordEnc\":\"enc:wp\"}]";
        QuickSshMigration.Result r = QuickSshMigration.migrate(json, c, ids());
        assertTrue(r.changed);
        assertEquals(0, c.encrypts);
        JsonObject o = JsonParser.parseString(r.json).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("enc:wp", o.get("passwordEnc").getAsString());
        assertFalse(o.has("password"));
    }

    @Test
    public void legacyAlongsideStaleCiphertextReencryptsLegacy() throws Exception {
        FakeCipher c = new FakeCipher();
        String json = "[{\"id\":\"x\",\"password\":\"new\",\"passwordEnc\":\"garbage\"}]";
        QuickSshMigration.Result r = QuickSshMigration.migrate(json, c, ids());
        JsonObject o = JsonParser.parseString(r.json).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("new", c.decrypt(o.get("passwordEnc").getAsString()));
    }

    @Test
    public void failsClosedWhenCipherBroken() {
        try {
            QuickSshMigration.migrate(LEGACY, new BrokenCipher(), ids());
            fail("expected MigrationException");
        } catch (QuickSshMigration.MigrationException e) {
            assertFalse(String.valueOf(e.getMessage()).contains("it's"));
        }
    }

    @Test
    public void invalidJsonFailsWithoutLeakingContent() {
        try {
            QuickSshMigration.migrate("[{\"password\":\"hunter2\"", new FakeCipher(), ids());
            fail("expected MigrationException");
        } catch (QuickSshMigration.MigrationException e) {
            assertFalse(e.getMessage().contains("hunter2"));
        }
    }

    @Test
    public void emptyValuesAreUntouched() throws Exception {
        assertFalse(QuickSshMigration.migrate("def", new FakeCipher(), ids()).changed);
        assertFalse(QuickSshMigration.migrate("", new FakeCipher(), ids()).changed);
        assertFalse(QuickSshMigration.migrate(null, new FakeCipher(), ids()).changed);
        assertFalse(QuickSshMigration.migrate("[]", new FakeCipher(), ids()).changed);
    }
}
