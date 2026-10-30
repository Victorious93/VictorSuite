package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_VS_FEATURES;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import com.termux.victor.util.LogUtils;
import com.termux.victor.util.UUtils;
import com.mallotec.reb.localeplugin.utils.LocaleHelper;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.popuwindow.MenuLeftPopuListWindow;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;

public class LanguageClickConfig extends BaseMenuClickConfig implements MenuLeftPopuListWindow.ItemClickPopuListener {
    private static final String TAG = LanguageClickConfig.class.getSimpleName();
    @Override
    public int getType() {
        return CODE_VS_FEATURES;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.yuyan_ico);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_language_switch);
    }

    @Override
    public void onClick(View view, Context context) {
        LogUtils.e(TAG, "onClick Language.");
        ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> yuyan_list = new ArrayList<>();
        MenuLeftPopuListWindow.MenuLeftPopuListData msg_zh = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.zhongwen, UUtils.getString(R.string.vs_chinese), 30);
        yuyan_list.add(msg_zh);
        MenuLeftPopuListWindow.MenuLeftPopuListData msg_en = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.yingwen_ico, UUtils.getString(R.string.English), 31);
        yuyan_list.add(msg_en);
        showMenuDialog(yuyan_list, view);
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
            // 中文
            case 30:
                //  Intent intent = new Intent(this, TermuxActivity.class);
                LocaleHelper.Companion.getInstance()
                    .language(getLocale("2")).apply(mContext);
                // startActivity(intent);
                break;
            // 英文
            case 31:
                LocaleHelper.Companion.getInstance()
                    .language(getLocale("1")).apply(mContext);
                break;
        }

        if (mContext instanceof TermuxActivity) {
            ((TermuxActivity) mContext).restartActivity();
        }
    }

    private Locale getLocale(String which) {
        switch (which) {
            case "0":
                return Locale.ROOT;
            case "1":
                return Locale.ENGLISH;
            case "2":
                return Locale.SIMPLIFIED_CHINESE;
            default:
                return Locale.ENGLISH;
        }
    }
}
