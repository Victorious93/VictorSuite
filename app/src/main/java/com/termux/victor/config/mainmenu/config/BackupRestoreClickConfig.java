package com.termux.victor.config.mainmenu.config;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.back.BackRestoreDialog;
import com.termux.victor.back.listener.CreateConversationListener;
import com.termux.victor.config.mainmenu.MainMenuConfig;

// 备份/ 恢复
public class BackupRestoreClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return MainMenuConfig.CODE_COMMON_FUNCTIONS;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.beifen_ico);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_backup_restore);
    }

    @Override
    public void onClick(View view, Context context) {
//  startActivity(new Intent(this, BackNewActivity.class));
        BackRestoreDialog backRestoreDialog = new BackRestoreDialog(context);
        backRestoreDialog.setCreateConversationListener(new CreateConversationListener() {
            @Override
            public void create() {
                if (context instanceof TermuxActivity) {
                    ((TermuxActivity) context).mTermuxTerminalSessionActivityClient.addNewSession(false, "VictorSuite session");
                }
            }
        });
        backRestoreDialog.initData();
        backRestoreDialog.show();
        backRestoreDialog.setCancelable(true);
        backRestoreDialog.createStoragePath();
    }
}
