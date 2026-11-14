package com.termux.filepicker;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Covers the current file-share contract, replacing the removed text-URL receiver's no-op test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TermuxFileReceiverActivityTest {
    private Uri resolve(Intent intent) throws Exception {
        TermuxFileReceiverActivity activity = Robolectric.buildActivity(TermuxFileReceiverActivity.class).get();
        activity.setIntent(intent);
        // Exercise the resolver without running file copies, bootstrap setup, or installer UI.
        Method method = TermuxFileReceiverActivity.class.getDeclaredMethod("resolveIncomingUri");
        method.setAccessible(true);
        return (Uri) method.invoke(activity);
    }

    @Test
    public void sharePrefersStreamOverData() throws Exception {
        Uri stream = Uri.parse("content://fixture/selected.tar.gz");
        Intent intent = new Intent(Intent.ACTION_SEND)
            .setData(Uri.parse("content://fixture/other.tar.gz"))
            .putExtra(Intent.EXTRA_STREAM, stream);
        assertEquals(stream, resolve(intent));
    }

    @Test
    public void shareFallsBackToData() throws Exception {
        Uri data = Uri.parse("content://fixture/shared.tar.gz");
        assertEquals(data, resolve(new Intent(Intent.ACTION_SEND).setData(data)));
    }

    @Test
    public void viewUsesDataInsteadOfShareExtra() throws Exception {
        Uri data = Uri.parse("content://fixture/viewed.tar.gz");
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(data)
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://fixture/unused.tar.gz"));
        assertEquals(data, resolve(intent));
    }

    @Test
    public void missingUriReturnsNull() throws Exception {
        assertNull(resolve(new Intent(Intent.ACTION_SEND)));
    }
}
