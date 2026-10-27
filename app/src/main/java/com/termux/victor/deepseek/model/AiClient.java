package com.termux.zerocore.deepseek.model;

import com.termux.zerocore.ftp.utils.UserSetManage;

import java.util.List;

/**
 * Routes chat requests to the AI provider selected in settings (DeepSeek or
 * Claude), so UI code (e.g. ChatFragment) doesn't need to know which
 * provider is active. Mirrors the {@link DeepSeekClient} call shape so it
 * is a drop-in replacement at existing call sites.
 */
public class AiClient {
    private final DeepSeekClient deepSeekClient = new DeepSeekClient();
    private final ClaudeClient claudeClient = new ClaudeClient();
    private volatile boolean lastRequestWasClaude = false;

    public void ask(List<RequestMessageItem> messageItemList, boolean stream, DeepSeekClient.Lis lis) {
        lastRequestWasClaude = isClaudeSelected();
        if (lastRequestWasClaude) {
            claudeClient.ask(messageItemList, stream, lis);
        } else {
            deepSeekClient.ask(messageItemList, stream, lis);
        }
    }

    public String getMsg(String msg) {
        if (lastRequestWasClaude) {
            return claudeClient.getMsg(msg);
        }
        return deepSeekClient.getMsg(msg);
    }

    private boolean isClaudeSelected() {
        return "claude".equalsIgnoreCase(UserSetManage.Companion.get().getZTUserBean().getAiProvider());
    }
}
