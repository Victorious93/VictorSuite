package com.termux.victor.util;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

/**
 * Activity base that wraps the subclass layout under a 44dp title bar
 * (back/close button, centred title, optional right text).
 *
 * <p>Theme: {@code VictorDayTheme} unless the "settings" prefs hold day_night_mode=NIGHT,
 * then {@code VictorNightTheme}. The light-status-bar flag follows the theme.
 * If the {@code login_tk} pref (OtherPrefs) is set, it becomes {@link BaseHttpUtils#token}.
 */
public abstract class BaseThemeActivity extends AppCompatActivity {

    private static final String THEME_PREFS = "settings";
    private static final String THEME_KEY = "day_night_mode";

    private RelativeLayout mTitleBar;
    private TextView mTitle;
    private TextView mRightTv;
    private ImageView mCloseImg;
    private ImageView mBackImg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarCompat.changeToLightStatusBar(this);
        initData();
        boolean night = "NIGHT".equals(getSharedPreferences(THEME_PREFS, MODE_PRIVATE).getString(THEME_KEY, "DAY"));
        if (night) {
            setTheme(R.style.VictorNightTheme);
            StatusBarCompat.cancelLightStatusBar(this);
        } else {
            setTheme(R.style.VictorDayTheme);
            StatusBarCompat.changeToLightStatusBar(this);
        }
        String token = OtherPrefs.getStringOther("login_tk");
        if (token != null && !token.isEmpty() && !"def".equals(token)) {
            BaseHttpUtils.token = token;
        }
    }

    /** Hook run in onCreate before the theme is applied. */
    public void initData() {}

    @Override
    public void setContentView(int layoutResID) {
        View child = UUtils.getViewLay(layoutResID);
        View root = UUtils.getViewLay(R.layout.victor_base_title_activity);
        mTitleBar = root.findViewById(R.id.victor_title_bar);
        mTitle = root.findViewById(R.id.victor_title);
        mRightTv = root.findViewById(R.id.victor_right_tv);
        mCloseImg = root.findViewById(R.id.victor_close_img);
        mBackImg = root.findViewById(R.id.victor_back_img);
        mCloseImg.setOnClickListener(v -> finish());
        mBackImg.setOnClickListener(v -> finish());
        ((FrameLayout) root.findViewById(R.id.victor_content)).addView(child);
        super.setContentView(root);
    }

    /** The whole title bar. */
    public final RelativeLayout getBaseTitle() { return mTitleBar; }

    /** Right-hand text, hidden by default. */
    public final TextView getRightTv() { return mRightTv; }

    /** Shows the title text and sets it. */
    public final void setBaseTitleString(String title) {
        mTitle.setVisibility(View.VISIBLE);
        mTitle.setText(title);
    }

    /** Swaps the default left button for the small back arrow. */
    public final void setImgLiftAs() {
        mBackImg.setVisibility(View.VISIBLE);
        mCloseImg.setVisibility(View.GONE);
    }

    /** Kept for subclasses; never called by this base (the theme toggle was unused). */
    public abstract void refreshUI(TypedValue background, TypedValue textColor);
}
