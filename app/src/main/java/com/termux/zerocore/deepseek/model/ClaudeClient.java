package com.termux.zerocore.deepseek.model;

import android.text.TextUtils;

import com.example.xh_lib.utils.LogUtils;
import com.example.xh_lib.utils.UUtils;
import com.termux.R;
import com.termux.zerocore.ftp.utils.UserSetManage;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import okio.BufferedSource;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Client for the Claude (Anthropic) Messages API, following the same
 * request/callback shape as {@link DeepSeekClient} so both can be driven
 * from {@link AiClient} without the UI layer needing to know which
 * provider is active.
 */
public class ClaudeClient {
    private static final String TAG = ClaudeClient.class.getSimpleName();
    private boolean isStream = false;

    public void ask(List<RequestMessageItem> messageItemList, boolean stream, DeepSeekClient.Lis lis) {
        try {
            OkHttpClient client = new OkHttpClient();
            isStream = stream;

            String apiKey = UserSetManage.Companion.get().getZTUserBean().getClaudeApiKey();
            String url = UserSetManage.Companion.get().getZTUserBean().getClaudeApiUrl();
            if (TextUtils.isEmpty(url)) {
                url = Config.CLAUDE_URL;
            }
            String model = UserSetManage.Companion.get().getZTUserBean().getClaudeModel();
            if (TextUtils.isEmpty(model)) {
                model = Config.CLAUDE_DEFAULT_MODEL;
            }

            String requestBody = buildRequestBody(model, messageItemList, stream).toString();
            RequestBody body = RequestBody.create(requestBody,
                MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey == null ? "" : apiKey)
                .addHeader("anthropic-version", Config.CLAUDE_ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    LogUtils.e(TAG, "onFailure call: " + call + " ,e: " + e);
                    e.printStackTrace();
                    lis.msg("```call: " + call + "\n\nException: " + e, true);
                    lis.end();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    LogUtils.e(TAG, "onResponse call: " + call + " ,response: " + response);
                    if (response.isSuccessful()) {
                        try {
                            BufferedSource source = response.body().source();
                            String line;
                            while ((line = source.readUtf8Line()) != null) {
                                String processChunk = processChunk(line);
                                if (processChunk != null && processChunk.length() > 0) {
                                    lis.msg(processChunk, false);
                                }
                            }
                            lis.end();
                        } catch (Exception e) {
                            LogUtils.e(TAG, "onResponse data error: " + e);
                            lis.msg("Data Error Exception: " + e, true);
                            lis.end();
                        }
                    } else {
                        lis.msg(UUtils.getString(R.string.deepseek_input_key_error_start_info) + "\n\n```" + response
                            + "```\n\n" + (response.code() == 401 ? UUtils.getString(R.string.deepseek_input_key_error_info) : ""), true);
                        lis.end();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            UUtils.getHandler().postDelayed(() -> {
                lis.msg(UUtils.getString(R.string.deepseek_input_key_error_start_info) + " \n\n```" + e + "```"
                    + "\n\n" + UUtils.getString(R.string.deepseek_input_key_error_info_1), true);
                lis.end();
            }, 100);
        }
    }

    private JSONObject buildRequestBody(String model, List<RequestMessageItem> messageItemList, boolean stream) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("max_tokens", Config.CLAUDE_MAX_TOKENS);
        root.put("stream", stream);

        JSONArray messages = new JSONArray();
        StringBuilder systemPrompt = new StringBuilder();
        for (RequestMessageItem item : messageItemList) {
            if (item == null || item.content == null) {
                continue;
            }
            // Claude takes system prompts via a top-level "system" field, not as a message role.
            if ("system".equals(item.role)) {
                if (systemPrompt.length() > 0) {
                    systemPrompt.append("\n");
                }
                systemPrompt.append(item.content);
                continue;
            }
            String role = "assistant".equals(item.role) ? "assistant" : "user";
            JSONObject message = new JSONObject();
            message.put("role", role);
            message.put("content", item.content);
            messages.put(message);
        }
        root.put("messages", messages);
        if (systemPrompt.length() > 0) {
            root.put("system", systemPrompt.toString());
        }
        return root;
    }

    // Extracts the text delta (streaming) or full text (non-streaming) from a Claude response chunk.
    public String getMsg(String msg) {
        try {
            JSONObject jsonObject = new JSONObject(msg);
            if (!isStream) {
                JSONArray content = jsonObject.optJSONArray("content");
                if (content != null && content.length() > 0) {
                    return content.getJSONObject(0).optString("text", "");
                }
                return "";
            } else {
                String type = jsonObject.optString("type");
                if ("content_block_delta".equals(type)) {
                    JSONObject delta = jsonObject.optJSONObject("delta");
                    if (delta != null) {
                        return delta.optString("text", "");
                    }
                }
                return "";
            }
        } catch (JSONException e) {
            LogUtils.e(TAG, "getMsg error: " + e);
        }
        return msg;
    }

    // Claude streams Server-Sent Events with "event:"/"data:" lines; only forward the data payloads.
    private String processChunk(String chunk) {
        if (chunk == null || !chunk.startsWith("data: ")) {
            return null;
        }
        String cleanChunk = chunk.substring("data: ".length());
        if (cleanChunk.isEmpty() || cleanChunk.charAt(0) != '{') {
            return null;
        }
        return cleanChunk;
    }
}
