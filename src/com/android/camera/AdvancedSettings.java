/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (C) 2010 The CyanogenMod Project
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

package com.android.camera;

import com.android.camera.R;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

public class AdvancedSettings extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    private static final String KEY_VOL_UP_SHUTTER = "vol_up_shutter_enabled";
    private static final String KEY_VOL_DOWN_SHUTTER = "vol_down_shutter_enabled";
    private static final String KEY_SEARCH_SHUTTER = "search_shutter_enabled";
    private static final String KEY_VOL_ZOOM = "vol_zoom_enabled";
    private static final String KEY_LONG_FOCUS = "long_focus_enabled";
    private static final String KEY_PRE_FOCUS = "pre_focus_enabled";

    CheckBoxPreference volUpShutter = null;
    CheckBoxPreference volDownShutter = null;
    CheckBoxPreference searchShutter = null;
    CheckBoxPreference volZoom = null;
    CheckBoxPreference longFocus = null;
    CheckBoxPreference preFocus = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.camera_advanced_settings);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        
        volUpShutter = (CheckBoxPreference) preferenceScreen.findPreference(KEY_VOL_UP_SHUTTER);
        volDownShutter = (CheckBoxPreference) preferenceScreen.findPreference(KEY_VOL_DOWN_SHUTTER);
        searchShutter = (CheckBoxPreference) preferenceScreen.findPreference(KEY_SEARCH_SHUTTER);
        volZoom = (CheckBoxPreference) preferenceScreen.findPreference(KEY_VOL_ZOOM);
        longFocus = (CheckBoxPreference) preferenceScreen.findPreference(KEY_LONG_FOCUS);
        preFocus = (CheckBoxPreference) preferenceScreen.findPreference(KEY_PRE_FOCUS);

        checkBoxes();

        setListeners(true);
    }

    public void setListeners(boolean enable) {
        if (enable) {
            volUpShutter.setOnPreferenceChangeListener(this);
            volDownShutter.setOnPreferenceChangeListener(this);
            searchShutter.setOnPreferenceChangeListener(this);
            volZoom.setOnPreferenceChangeListener(this);
            longFocus.setOnPreferenceChangeListener(this);
            preFocus.setOnPreferenceChangeListener(this);
        } else {
            volUpShutter.setOnPreferenceChangeListener(null);
            volDownShutter.setOnPreferenceChangeListener(null);
            searchShutter.setOnPreferenceChangeListener(null);
            volZoom.setOnPreferenceChangeListener(null);
            longFocus.setOnPreferenceChangeListener(null);
            preFocus.setOnPreferenceChangeListener(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBoxes();
        setListeners(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setListeners(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setListeners(false);
    }

    public void checkBoxes() {
        if (volUpShutter.isChecked() || volDownShutter.isChecked()) {
            volZoom.setEnabled(false);
        } else if (!volUpShutter.isChecked() && !volDownShutter.isChecked()) {
            volZoom.setEnabled(true);
        }
        if (volZoom.isChecked()) {
            volUpShutter.setEnabled(false);
            volDownShutter.setEnabled(false);
            longFocus.setEnabled(false);
        } else {
            volUpShutter.setEnabled(true);
            volDownShutter.setEnabled(true);
            longFocus.setEnabled(true);
        }
        if (longFocus.isChecked()) {
            preFocus.setEnabled(false);
        } else {
            preFocus.setEnabled(true);
        }

        if (preFocus.isChecked()) {
            longFocus.setEnabled(false);
        } else {
            longFocus.setEnabled(true);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        CheckBoxPreference checkBox = (CheckBoxPreference)preference;
        boolean checked = (Boolean)value;
        System.out.println("value = " + value + ", pref.isChecked() - " + 
            checkBox.isChecked());
        String key = preference.getKey();
        if (key.contains("vol")) {
            if (key.contains("shutter")) {
                if (checked) {
                    volZoom.setEnabled(false);
                } else if (!checked && 
                        (volUpShutter.isChecked() && !volDownShutter.isChecked()) || 
                        (volDownShutter.isChecked() && !volUpShutter.isChecked())) {
                    volZoom.setEnabled(true);
                }
            } else {
                if (checked) {
                    volUpShutter.setEnabled(false);
                    volDownShutter.setEnabled(false);
                    longFocus.setEnabled(false);
                } else {
                    volUpShutter.setEnabled(true);
                    volDownShutter.setEnabled(true);                    
                    longFocus.setEnabled(true);
                }
            }
        } else if (key.equalsIgnoreCase(KEY_LONG_FOCUS)) {
            if (checked) {
                preFocus.setEnabled(false);
            } else if (!checked) {
                preFocus.setEnabled(true);
            }
        } else if (key.equalsIgnoreCase(KEY_PRE_FOCUS)) {
            if (checked) {
                longFocus.setEnabled(false);
            } else if (!checked) {
                longFocus.setEnabled(true);
            }
        }
        
        return true;
    }
}
