package com.termux.zerocore.config.mainmenu.config;

import com.termux.zerocore.config.VsLinks;

import static com.termux.zerocore.config.mainmenu.MainMenuConfig.CODE_ZT_FEATURES;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import com.termux.R;

/**
 * Launches the AndroidIDE fork (Android Code Studio, applicationId {@code com.tom.rv2ide}) as a
 * separate app if it's installed, or opens its GitHub page so the user can grab an APK if not.
 *
 * AndroidIDE is a full IDE (editor, LSP servers, its own Gradle-based build system) and is kept
 * as a standalone app rather than merged into this one - this is just a shortcut to it.
 */
public class AndroidIdeClickConfig extends BaseMenuClickConfig {

    private static final String ANDROID_IDE_PACKAGE_NAME = "com.tom.rv2ide";
    private static final String ANDROID_IDE_GITHUB_URL = VsLinks.ANDROID_CODE_STUDIO_REPO;

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
        return context.getString(R.string.vs_menu_android_ide);
    }

    @Override
    public void onClick(View view, Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(ANDROID_IDE_PACKAGE_NAME);
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            return;
        }

        Toast.makeText(context, R.string.vs_android_ide_not_installed, Toast.LENGTH_LONG).show();
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setData(Uri.parse(ANDROID_IDE_GITHUB_URL));
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(viewIntent);
    }
}
