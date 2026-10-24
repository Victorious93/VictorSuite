package com.termux.zerocore.config.mainmenu.config;

import static com.termux.zerocore.config.mainmenu.MainMenuConfig.CODE_ZT_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import com.termux.R;
import com.termux.zerocore.url.FileUrl;
import com.termux.zerocore.utils.SingletonCommunicationUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the AI Coding Agents pack (Claude Code, Codex, Copilot CLI, Ollama, vsphone, vstoken).
 *
 * Copies the bundled {@code scripts/ai-cli-setup.sh} (packaged as an asset, see app/build.gradle)
 * to {@code ~/ai-cli-setup.sh} and runs it in the terminal, so the user sees its progress and can
 * answer any prompts. See docs/AI_CODING_AGENTS.md for what the script installs and why.
 */
public class AiAgentsPackClickConfig extends BaseMenuClickConfig {

    private static final String SCRIPT_ASSET = "ai-agents-pack.sh";
    /** Everything the pack script needs next to it in ~/.victorsuite-ai-agents/. */
    private static final String[] ASSETS = {
        "ai-agents-pack.sh", "ai-cli-setup.sh", "vsphone", "vstoken", "vstoken.example.json"};
    private static final String DIR = ".victorsuite-ai-agents";

    @Override
    public int getType() {
        return CODE_ZT_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.code_view);
    }

    @Override
    public String getString(Context context) {
        return "AI Coding Agents";
    }

    @Override
    public void onClick(View view, Context context) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File dir = new File(FileUrl.INSTANCE.getMainHomeUrl(), DIR);
        new Thread(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("could not create " + dir);
                }
                for (String asset : ASSETS) {
                    copyAsset(appContext, asset, new File(dir, asset));
                }
                mainHandler.post(() -> SingletonCommunicationUtils.getInstance()
                    .getmSingletonCommunicationListener()
                    .sendTextToTerminal("bash ~/" + DIR + "/" + SCRIPT_ASSET + "\n"));
            } catch (IOException e) {
                mainHandler.post(() -> Toast.makeText(appContext,
                    "Could not prepare the AI agents pack: " + e.getMessage(),
                    Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static void copyAsset(Context context, String assetName, File target) throws IOException {
        try (InputStream in = context.getAssets().open(assetName);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        if (!target.setExecutable(true, true)) {
            throw new IOException("could not mark " + target + " executable");
        }
    }
}
