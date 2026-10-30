package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_VS_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.victor.util.UUtils;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.code.CodeString;
import com.termux.victor.dialog.SwitchDialog;
import com.termux.victor.popuwindow.MenuLeftPopuListWindow;
import com.termux.victor.url.FileUrl;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;

public class PhoneSmsClickConfig extends BaseMenuClickConfig implements MenuLeftPopuListWindow.ItemClickPopuListener {
    @Override
    public int getType() {
        return CODE_VS_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.duanxin_ico);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_sms_call);
    }

    @Override
    public void onClick(View view, Context context) {
        ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> menuphoneMsg = new ArrayList<>();
        MenuLeftPopuListWindow.MenuLeftPopuListData msg_phone = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.install_msg_phone, UUtils.getString(R.string.vs_sms_address_book_tool), 6);
        menuphoneMsg.add(msg_phone);
        showMenuDialog(menuphoneMsg, view);
    }

    private void showMenuDialog(ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> arrayList, View showView) {
        MenuLeftPopuListWindow menuLeftPopuListWindow = new MenuLeftPopuListWindow(mContext);
        menuLeftPopuListWindow.setItemClickPopuListener(this);
        menuLeftPopuListWindow.setListData(arrayList);
        menuLeftPopuListWindow.showAsDropDown(showView, 250, -200);
    }

    @Override
    public void itemClick(int id, int index, @Nullable MenuLeftPopuListWindow mMenuLeftPopuListWindow) {
        switch (id) {
            case 6:
                SwitchDialog msg = switchDialogShow(UUtils.getString(R.string.vs_warning),
                    UUtils.getString(R.string.vs_this_operation_is_risky_your_mobile_phon), mContext);
                msg.getCancel().setOnClickListener(v -> msg.dismiss());
                msg.getOk().setOnClickListener(v -> {
                    msg.dismiss();
                    File file = new File(FileUrl.INSTANCE.getSmsUrl());
                    if (file.exists()) {
                        UUtils.showMsg(UUtils.getString(R.string.vs_you_have_already_installed_the_tool_so_t));
                    } else {
                        UUtils.writerFile("runcommand/smsread", new File(FileUrl.INSTANCE.getSmsUrl()));
                        UUtils.writerFile("runcommand/readcontacts", new File(FileUrl.INSTANCE.getPhoneUrl()));
                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunsmsChomdSh());
                        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(CodeString.INSTANCE.getRunPhoneChomdSh());
                        UUtils.showMsg(UUtils.getString(R.string.vs_the_installation_is_complete_please_ente));
                    }
                });
                break;
        }
    }
}
