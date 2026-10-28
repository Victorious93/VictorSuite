package com.termux.victor.util;

import android.app.Application;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() {
        UUtils.initUUtils(RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()));
    }

    @Test
    public void fileStringRoundTripAppendsNewlinePerLine() throws Exception {
        File f = new File(tmp.getRoot(), "a.txt");
        assertTrue(UUtils.setFileString(f, "one\ntwo"));
        assertEquals("one\ntwo\n", UUtils.getFileString(f));
    }

    @Test
    public void getFileStringCreatesMissingFile() {
        File f = new File(tmp.getRoot(), "new.txt");
        assertEquals("", UUtils.getFileString(f));
        assertTrue(f.exists());
    }

    @Test
    public void getFileStringNotNewFileDoesNotCreate() {
        File f = new File(tmp.getRoot(), "missing.txt");
        assertEquals("", UUtils.getFileStringNotNewFile(f));
        assertFalse(f.exists());
    }

    @Test
    public void setFileStringFailsForDirectoryTarget() {
        assertFalse(UUtils.setFileString(tmp.getRoot(), "x"));
    }

    @Test
    public void writerFileRawInputCopiesAndReportsSuccess() throws Exception {
        File f = new File(tmp.getRoot(), "copy.bin");
        byte[] data = new byte[5000];
        Arrays.fill(data, (byte) 7);
        AtomicBoolean ok = new AtomicBoolean(false);
        UUtils.writerFileRawInput(f, new ByteArrayInputStream(data), (msg, success) -> ok.set(success));
        assertTrue(ok.get());
        assertEquals(5000, f.length());
    }

    @Test
    public void writerFileRawInputReportsFailure() {
        AtomicBoolean called = new AtomicBoolean(false);
        AtomicBoolean ok = new AtomicBoolean(true);
        UUtils.writerFileRawInput(new File(tmp.getRoot(), "no/such/dir/f"),
            new ByteArrayInputStream(new byte[1]), (msg, success) -> { called.set(true); ok.set(success); });
        assertTrue(called.get());
        assertFalse(ok.get());
    }

    @Test
    public void writerFileInputCountsWholeBuffers() throws Exception {
        File f = new File(tmp.getRoot(), "p.bin");
        AtomicLong last = new AtomicLong();
        AtomicBoolean done = new AtomicBoolean(false);
        UUtils.writerFileInput(f, new ByteArrayInputStream(new byte[100]), (n, d) -> { last.set(n); done.set(d); });
        assertTrue(done.get());
        assertEquals(51200, last.get());
        assertEquals(100, f.length());
    }

    @Test
    public void arrayListToStringShellKeepsTrailingSpace() {
        assertEquals("a b ", UUtils.arrayListToStringShell(new ArrayList<>(Arrays.asList("a", "b"))));
        assertEquals("", UUtils.arrayListToStringShell(new ArrayList<>()));
    }

    @Test
    public void ftpDateIsUtc() {
        assertEquals("1970-01-01 00:00:01", UUtils.getFtpDate(1000L));
    }

    @Test
    public void calculateAlphaValueKeepsOriginalArgumentOrder() {
        int c = 0xFF102030;
        assertEquals(Color.argb(0x10, 0x20, 0x30, 0x80), UUtils.calculateAlphaValue(c, 0x80));
    }

    @Test
    public void installApkRejectsEmptyAndMissingPaths() {
        assertFalse(UUtils.installApk(RuntimeEnvironment.getApplication(), ""));
        assertFalse(UUtils.installApk(RuntimeEnvironment.getApplication(), "/does/not/exist.apk"));
    }

    @Test
    public void getFileAbsolutePathNullSafe() {
        assertNull(UUtils.getFileAbsolutePath(null, null));
    }

    @Test
    public void otherPrefsDefaultAndRoundTrip() {
        assertEquals("def", OtherPrefs.getStringOther("vs_test_key"));
        OtherPrefs.saveStringOther("vs_test_key", "v");
        assertEquals("v", OtherPrefs.getStringOther("vs_test_key"));
        assertEquals("v", RuntimeEnvironment.getApplication()
            .getSharedPreferences("start_flash", 0).getString("vs_test_key", null));
    }
}
