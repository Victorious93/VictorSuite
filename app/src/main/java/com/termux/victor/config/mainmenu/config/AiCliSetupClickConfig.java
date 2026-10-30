package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_VS_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import com.termux.R;
import com.termux.victor.url.FileUrl;
import com.termux.victor.utils.SingletonCommunicationUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs AI command-line tools (pi, GitHub Copilot CLI, OpenAI Codex CLI, Hugging Face CLI).
 *
 * Copies the bundled {@code scripts/ai-cli-setup.sh} (packaged as an asset, see app/build.gradle)
 * to {@code ~/ai-cli-setup.sh} and runs it in the terminal, so the user sees its progress and can
 * answer any prompts. See docs/AI_CLIS.md for what the script installs and why.
 */
public class AiCliSetupClickConfig extends BaseMenuClickConfig {

    private static final String SCRIPT_ASSET = "ai-cli-setup.sh";

    @Override
    public int getType() {
        return CODE_VS_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.code_view);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_menu_ai_clis);
    }

    @Override
    public void onClick(View view, Context context) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File target = new File(FileUrl.INSTANCE.getMainHomeUrl(), SCRIPT_ASSET);
        new Thread(() -> {
            try {
                copyAsset(appContext, SCRIPT_ASSET, target);
                mainHandler.post(() -> SingletonCommunicationUtils.getInstance()
                    .getmSingletonCommunicationListener()
                    .sendTextToTerminal("bash ~/" + SCRIPT_ASSET + "\n"));
            } catch (IOException e) {
                mainHandler.post(() -> Toast.makeText(appContext,
                    appContext.getString(R.string.vs_ai_cli_prepare_failed, e.getMessage()),
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
