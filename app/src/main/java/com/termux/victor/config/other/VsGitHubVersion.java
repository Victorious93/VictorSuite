package com.termux.victor.config.other;

import com.termux.victor.config.VsLinks;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.victor.util.BaseHttpUtils;
import com.termux.victor.util.HttpResponseListenerBase;
import com.termux.victor.util.LogUtils;
import com.termux.victor.util.UUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.Callback;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.HttpParams;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;
import com.lzy.okgo.request.base.Request;
import com.lzy.okgo.utils.HttpUtils;
import com.termux.BuildConfig;
import com.termux.victor.config.other.data.VersionBean;

import java.util.HashMap;

public class VsGitHubVersion {
    private static final String TAG = VsGitHubVersion.class.getSimpleName();

    public static VsGitHubVersion create() {
        return new VsGitHubVersion();
    }

    public void initZtVersionVisible(TextView textView) {
        OkGo.<String>get(VsLinks.RELEASES_API).tag(UUtils.getContext()).headers(new HttpHeaders())
            .params(new HttpParams()).execute(new StringCallback() {

                @Override
                public void onSuccess(Response<String> response) {
                    LogUtils.i(TAG, "onSuccess response: " + response.code());
                    try {
                        if (response.code() == 200) {
                            String body = response.body();
                            LogUtils.i(TAG, "onSuccess body: " + body);
                            JsonElement rootElement = JsonParser.parseString(body);
                            // releases?per_page=1 returns an array with the newest release, pre-releases included.
                            String gitTag = null;
                            if (rootElement.isJsonArray() && rootElement.getAsJsonArray().size() > 0) {
                                JsonObject rootObject = rootElement.getAsJsonArray().get(0).getAsJsonObject();
                                if (rootObject.has("tag_name") && !rootObject.get("tag_name").isJsonNull()) {
                                    gitTag = rootObject.get("tag_name").getAsString();
                                }
                            }
                            LogUtils.i(TAG, "onSuccess gitTag: " + gitTag + " ,local: " + BuildConfig.VERSION_NAME);
                            if (isNewerRelease(gitTag, BuildConfig.VERSION_NAME)) {
                                UUtils.runOnUIThread(() -> textView.setVisibility(View.VISIBLE));
                            } else {
                                UUtils.runOnUIThread(() -> textView.setVisibility(View.GONE));
                            }
                        } else {
                            UUtils.runOnUIThread(() -> textView.setVisibility(View.GONE));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        LogUtils.i(TAG, "onSuccess Exception: " + e);
                        UUtils.runOnUIThread(() -> textView.setVisibility(View.GONE));
                    }
                }

                @Override
                public void onError(Response<String> response) {
                    super.onError(response);
                    if (response == null) {
                        LogUtils.e(TAG, "onError response: null");
                    } else {
                        LogUtils.e(TAG, "onError response body: " + response.body() + " ,code: " + response.code());
                    }

                    UUtils.runOnUIThread(() -> textView.setVisibility(View.GONE));
                }
            });
    }

    /** Strips a leading "v"/"V" and any "VictorSuite" prefix from a release tag or name. */
    static String normalizeVersion(String v) {
        if (v == null) return "";
        String s = v.trim();
        if (s.regionMatches(true, 0, "VictorSuite", 0, 11)) s = s.substring(11).trim();
        if (s.startsWith("-")) s = s.substring(1);
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s.trim();
    }

    /** True when the remote tag is a strictly higher dotted version than the installed one. */
    public static boolean isNewerRelease(String remoteTag, String localVersion) {
        String r = normalizeVersion(remoteTag);
        String l = normalizeVersion(localVersion);
        if (r.isEmpty() || l.isEmpty()) return false;
        String[] rp = r.split("[.\\-]");
        String[] lp = l.split("[.\\-]");
        int n = Math.max(rp.length, lp.length);
        for (int i = 0; i < n; i++) {
            long a = i < rp.length ? parse(rp[i]) : 0;
            long b = i < lp.length ? parse(lp[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static long parse(String s) {
        StringBuilder d = new StringBuilder();
        for (char c : s.toCharArray()) { if (Character.isDigit(c)) d.append(c); else break; }
        if (d.length() == 0) return 0;
        try { return Long.parseLong(d.toString()); } catch (NumberFormatException e) { return 0; }
    }
}
