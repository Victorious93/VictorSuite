package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_COMMON_FUNCTIONS;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.termux.victor.util.LogUtils;
import com.termux.victor.util.UUtils;
import com.termux.R;
import com.termux.victor.dialog.SwitchDialog;
import com.termux.victor.popuwindow.MenuLeftPopuListWindow;

import java.util.ArrayList;

public abstract class BaseMenuClickConfig implements MainMenuClickConfig {
    private static final String TAG = BaseMenuClickConfig.class.getSimpleName();
    protected Context mContext;
    private String mXmlName;
    @Override
    public boolean onLongClick(View view, Context context) {
        return false;
    }

    @Override
    public void initViewStatus(Context context) {

    }

    @Override
    public String getXmlString(Context context) {
        return mXmlName;
    }

    public void setXmlName(String xmlName) {
        this.mXmlName = xmlName;
    }

    @Override
    public boolean isShowDisableIco() {
        return false;
    }

    @Override
    public void initContext(Context context) {
        mContext = context;
    }

    @Override
    public void release() {
        LogUtils.e(TAG, "release.");
        mContext = null;
    }

    @Override
    public int getType() {
        return CODE_COMMON_FUNCTIONS;
    }

    @Override
    public void setImageView(ImageView imageView) {

    }

    @Override
    public void setTextView(TextView textView) {

    }

    public SwitchDialog switchDialogShow(String title, String msg, Context context) {
        SwitchDialog switchDialog = new SwitchDialog(context);
        switchDialog.getTitle().setText(title);
        switchDialog.getMsg().setText(msg);
        switchDialog.getOther().setVisibility(View.GONE);
        switchDialog.getOk().setText(UUtils.getString(R.string.vs_determine));
        switchDialog.getCancel().setText(UUtils.getString(R.string.vs_cancel));
        switchDialog.show();
        return switchDialog;
    }

}
