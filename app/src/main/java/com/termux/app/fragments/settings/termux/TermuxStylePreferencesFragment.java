package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

@Keep
public class TermuxStylePreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_style_preferences, rootKey);
    }

}

class TermuxStylePreferencesDataStore extends PreferenceDataStore {

    private final TermuxAppSharedPreferences mPreferences;

    private static TermuxStylePreferencesDataStore mInstance;

    private TermuxStylePreferencesDataStore(Context context) {
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxStylePreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxStylePreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null || key == null)
            return;
        if ("monet_background_enabled".equals(key)) {
            mPreferences.setMonetBackgroundEnabled(value);
        } else if ("extrakeys_blur_enabled".equals(key)) {
            mPreferences.setExtraKeysBlurEnabled(value);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null || key == null)
            return defValue;
        if ("monet_background_enabled".equals(key)) {
            return mPreferences.isMonetBackgroundEnabled();
        } else if ("extrakeys_blur_enabled".equals(key)) {
            return mPreferences.isExtraKeysBlurEnabled();
        }
        return defValue;
    }
}
