package com.termux.victor.config.mainmenu.config;

import static com.termux.victor.config.mainmenu.MainMenuConfig.CODE_BEAUTIFICATION_FUNCTION;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.victor.bean.VsUserBean;
import com.termux.victor.ftp.utils.UserSetManage;

public class ParticleClickConfig extends BaseMenuClickConfig {
    @Override
    public int getType() {
        return CODE_BEAUTIFICATION_FUNCTION;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getDrawable(R.mipmap.particle);
    }

    @Override
    public String getString(Context context) {
        return context.getString(R.string.vs_particle_animation);
    }

    @Override
    public void onClick(View view, Context context) {
        TermuxActivity termuxActivity = (TermuxActivity) context;
        VsUserBean vsRainUserBean = UserSetManage.Companion.get().getVsUserBean();
        vsRainUserBean.setSnowflakeShow(false);
        termuxActivity.xue_fragment.removeAllViews();
        if (!vsRainUserBean.isRainShow()) {
            termuxActivity.firework_view.setVisibility(View.VISIBLE);
            vsRainUserBean.setRainShow(true);
            UserSetManage.Companion.get().setVsUserBean(vsRainUserBean);
        } else {
            termuxActivity.firework_view.setVisibility(View.GONE);
            vsRainUserBean.setRainShow(false);
            UserSetManage.Companion.get().setVsUserBean(vsRainUserBean);
        }
    }

    @Override
    public void initViewStatus(Context context) {
        TermuxActivity termuxActivity = (TermuxActivity) context;
        VsUserBean vsRainUserBean = UserSetManage.Companion.get().getVsUserBean();
        termuxActivity.xue_fragment.removeAllViews();
        if (vsRainUserBean.isRainShow()) {
            termuxActivity.firework_view.setVisibility(View.VISIBLE);
        } else {
            termuxActivity.firework_view.setVisibility(View.GONE);
        }
    }
}
