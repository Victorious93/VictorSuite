package com.termux.victor.util;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import java.io.InputStream;

import cn.bingoogolapple.qrcode.core.QRCodeView;
import cn.bingoogolapple.qrcode.zxing.ZXingView;

/**
 * Full-screen QR scanner. The result is reported through a static listener:
 * {@code success(intent with "result")} on a decode, and {@code fail()} whenever the
 * activity is destroyed (also after a success, as before).
 */
public class ScanActivity extends AppCompatActivity implements QRCodeView.Delegate, View.OnClickListener {

    public interface ScanActivityResultListener {
        void success(Intent intent);
        void fail();
    }

    private static final int REQUEST_PICK_IMAGE = 666;
    private static ScanActivityResultListener sListener;

    private ZXingView mZxingView;

    public static void setScanActivityResultListener(ScanActivityResultListener listener) {
        sListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarCompat.changeToLightStatusBar(this);
        setContentView(R.layout.victor_activity_scan);
        mZxingView = findViewById(R.id.victor_zxing_view);
        findViewById(R.id.victor_scan_back).setOnClickListener(this);
        findViewById(R.id.victor_scan_album).setOnClickListener(this);
        mZxingView.setDelegate(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mZxingView.startCamera();
        mZxingView.startSpotAndShowRect();
    }

    @Override
    protected void onStop() {
        mZxingView.stopCamera();
        super.onStop();
    }

    @Override
    public void onScanQRCodeSuccess(String result) {
        vibrate();
        Intent intent = new Intent();
        intent.putExtra("result", result);
        UUtils.showLog("onActivityResultxxxxxx：result:" + result);
        if (sListener != null) sListener.success(intent);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onCameraAmbientBrightnessChanged(boolean isDark) {
        String tip = mZxingView.getScanBoxView().getTipText();
        String darkTip = "\n" + getString(R.string.victor_scan_too_dark);
        if (tip == null) return;
        if (isDark) {
            if (!tip.contains(darkTip)) mZxingView.getScanBoxView().setTipText(tip + darkTip);
        } else if (tip.contains(darkTip)) {
            mZxingView.getScanBoxView().setTipText(tip.substring(0, tip.indexOf(darkTip)));
        }
    }

    @Override
    public void onScanQRCodeOpenCameraError() {}

    @SuppressWarnings("deprecation")
    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap != null) mZxingView.decodeQRCode(bitmap);
        } catch (Exception e) {
            UUtils.showLog("scan: cannot read picked image " + e);
        }
    }

    @Override
    protected void onDestroy() {
        mZxingView.onDestroy();
        if (sListener != null) sListener.fail();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.victor_scan_back) {
            finish();
        } else if (id == R.id.victor_scan_album) {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            startActivityForResult(pick, REQUEST_PICK_IMAGE);
        }
    }
}
