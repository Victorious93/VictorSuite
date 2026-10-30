package com.termux.victor.config.mainmenu.config;

import com.termux.victor.config.VsLinks;
import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_ONLINE_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.EditText;

import com.termux.victor.util.UUtils;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.dialog.EditDialog;

public class PublicWarehouseClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_ONLINE_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.gongongcangku);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_public_warehouse);
    }

    @Override
    public void onClick(View view, Context context) {
        TermuxActivity termuxActivity = (TermuxActivity) context;
        EditDialog editDialog = new EditDialog(context);
        EditText edit_text = editDialog.getEdit_text();
        editDialog.getCancel().setText(UUtils.getString(R.string.vs_how_to_create_a_server));
        editDialog.getCancel().setVisibility(View.GONE);
        editDialog.getCancel().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
        editDialog.getOk().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = edit_text.getText().toString();
                if (s == null || s.isEmpty()) {
                    s = VsLinks.FEED_BASE;
                }
                editDialog.dismiss();
                termuxActivity.startHttp(s);
            }
        });
        editDialog.show();
    }
}
