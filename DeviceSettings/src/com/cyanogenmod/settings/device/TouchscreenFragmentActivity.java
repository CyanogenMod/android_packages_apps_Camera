/*
 * Copyright (C) 2012-2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.settings.device;

import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.TwoStatePreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.cyanogenmod.settings.device.R;

public class TouchscreenFragmentActivity extends PreferenceFragment {

    private static final String TAG = "DeviceSettings_Touchscreen";
    public static final String KEY_LOGO2MENU_SWITCH = "logo2menu_switch";
    public static final String KEY_LONGTAPLOGOSLEEP_SWITCH = "longtaplogosleep_switch";
    public static final String KEY_WAKE_METHOD = "wake_method";

    private static boolean sLogo2Menu;
    private static boolean sWake;
    private TwoStatePreference mLogo2MenuSwitch;
    private TwoStatePreference mLongTapLogoSleepSwitch;
    private ListPreference mWakeMethod;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Resources res = getResources();
        sLogo2Menu = res.getBoolean(R.bool.has_logo2menu);
        sWake = res.getBoolean(R.bool.has_wake);

        addPreferencesFromResource(R.xml.touchscreen_preferences);

        if (sLogo2Menu) {
            mLogo2MenuSwitch = (TwoStatePreference) findPreference(KEY_LOGO2MENU_SWITCH);
            mLogo2MenuSwitch.setEnabled(Logo2MenuSwitch.isSupported());
            mLogo2MenuSwitch.setOnPreferenceChangeListener(new Logo2MenuSwitch());
            mLongTapLogoSleepSwitch = (TwoStatePreference) findPreference(KEY_LONGTAPLOGOSLEEP_SWITCH);
            mLongTapLogoSleepSwitch.setEnabled(LongTapLogoSleepSwitch.isSupported());
            mLongTapLogoSleepSwitch.setOnPreferenceChangeListener(new LongTapLogoSleepSwitch());
        }
        if (sWake) {
            mWakeMethod = (ListPreference) findPreference(KEY_WAKE_METHOD);
            mWakeMethod.setEnabled(WakeMethod.isSupported());
            mWakeMethod.setOnPreferenceChangeListener(new WakeMethod());
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String boxValue;
        String key = preference.getKey();
        Log.w(TAG, "key: " + key);
        return true;
    }

    public static void restore(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
}
