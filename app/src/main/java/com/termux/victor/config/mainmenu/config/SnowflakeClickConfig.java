package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_BEAUTIFICATION_FUNCTION;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import com.termux.victor.util.UUtils;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.bean.VsUserBean;
import com.termux.victor.ftp.utils.UserSetManage;
import com.termux.victor.view.xuehua.SnowView;

public class SnowflakeClickConfig extends BaseMenuClickConfig {
    private TextView mTextView;
    @Override
    public int getType() {
        return CODE_BEAUTIFICATION_FUNCTION;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.xuehua_ico);
    }

    @Override
    public String getString(Context context) {
        if (UserSetManage.Companion.get().getVsUserBean().isSnowflakeShow()) {
            return context.getString(R.string.vs_snowflakes_open);
        } else {
            return context.getString(R.string.vs_snowflake_off);
        }
    }

    @Override
    public void onClick(View view, Context context) {
        TermuxActivity termuxActivity = (TermuxActivity) context;
        VsUserBean vsUserBean = UserSetManage.Companion.get().getVsUserBean();
        vsUserBean.setRainShow(false);
        termuxActivity.firework_view.setVisibility(View.GONE);
        if (!vsUserBean.isSnowflakeShow()) {
            mTextView.setText(UUtils.getString(R.string.vs_snowflakes_open));
            SnowView snowView = new SnowView(termuxActivity);
            termuxActivity.xue_fragment.removeAllViews();
            termuxActivity.xue_fragment.addView(snowView);
            vsUserBean.setSnowflakeShow(true);
            UserSetManage.Companion.get().setVsUserBean(vsUserBean);
        } else {
            mTextView.setText(UUtils.getString(R.string.vs_snowflake_off));
            termuxActivity.xue_fragment.removeAllViews();
            vsUserBean.setSnowflakeShow(false);
            UserSetManage.Companion.get().setVsUserBean(vsUserBean);
        }
    }

    @Override
    public void setTextView(TextView textView) {
        super.setTextView(textView);
        mTextView = textView;
    }

    @Override
    public void release() {
        super.release();
        mTextView = null;
    }

    @Override
    public void initViewStatus(Context context) {
        TermuxActivity termuxActivity = (TermuxActivity) context;
        boolean snowflakeShow = UserSetManage.Companion.get().getVsUserBean().isSnowflakeShow();
        if (!snowflakeShow) {
            //xue_hua_start.setText(UUtils.getString(R.string.vs_snowflake_off));
            termuxActivity.xue_fragment.removeAllViews();
        } else {
            //xue_hua_start.setText(UUtils.getString(R.string.vs_snowflakes_open));
            SnowView snowView = new SnowView(termuxActivity);
            termuxActivity.xue_fragment.removeAllViews();
            termuxActivity.xue_fragment.addView(snowView);
        }
    }
}
