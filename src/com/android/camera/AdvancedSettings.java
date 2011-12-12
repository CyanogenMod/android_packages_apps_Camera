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
import android.os.Environment;

public class AdvancedSettings extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    private static final String KEY_VOL_UP_SHUTTER = "vol_up_shutter_enabled";
    private static final String KEY_VOL_DOWN_SHUTTER = "vol_down_shutter_enabled";
    private static final String KEY_SEARCH_SHUTTER = "search_shutter_enabled";
    private static final String KEY_VOL_ZOOM = "vol_zoom_enabled";
    private static final String KEY_LONG_FOCUS = "long_focus_enabled";
    private static final String KEY_PRE_FOCUS = "pre_focus_enabled";
    private static final String KEY_STORE_EXTSD = "store_on_external_sd";

    CheckBoxPreference volUpShutter = null;
    CheckBoxPreference volDownShutter = null;
    CheckBoxPreference searchShutter = null;
    CheckBoxPreference volZoom = null;
    CheckBoxPreference longFocus = null;
    CheckBoxPreference preFocus = null;
    CheckBoxPreference storeExtSd = null;

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
        storeExtSd = (CheckBoxPreference) preferenceScreen.findPreference(KEY_STORE_EXTSD);
        // Hide this preference if there is no removable external storage at all
        // for this device.
        if (!Environment.isExternalStorageRemovable()) {
            preferenceScreen.removePreference(storeExtSd);
            storeExtSd = null;
        }

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
            if (storeExtSd != null)
                storeExtSd.setOnPreferenceChangeListener(this);
        } else {
            volUpShutter.setOnPreferenceChangeListener(null);
            volDownShutter.setOnPreferenceChangeListener(null);
            searchShutter.setOnPreferenceChangeListener(null);
            volZoom.setOnPreferenceChangeListener(null);
            longFocus.setOnPreferenceChangeListener(null);
            preFocus.setOnPreferenceChangeListener(null);
            if (storeExtSd != null)
                storeExtSd.setOnPreferenceChangeListener(null);
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
            volZoom.setChecked(false);
        } else {
            volZoom.setEnabled(true);
        }
        if (volZoom.isChecked()) {
            volUpShutter.setEnabled(false);
            volDownShutter.setEnabled(false);
            /* no need to update checked, both must be unchecked anyway */
        } else {
            volUpShutter.setEnabled(true);
            volDownShutter.setEnabled(true);
        }

        if (longFocus.isChecked()) {
            preFocus.setEnabled(false);
            preFocus.setChecked(false);
        } else {
            preFocus.setEnabled(true);
        }

        if (preFocus.isChecked()) {
            longFocus.setEnabled(false);
            longFocus.setChecked(false);
        } else {
            longFocus.setEnabled(true);
        }

        // Check if we have both removable SD card and internal phone memory.
        // If so, enable storeExtSd
        if (storeExtSd != null)
            storeExtSd.setEnabled(ImageManager.enableExtSdOption());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        CheckBoxPreference checkBox = (CheckBoxPreference)preference;
        boolean checked = (Boolean)value;

        if (checkBox == volUpShutter || checkBox == volDownShutter) {
            boolean up = checkBox == volUpShutter;

            if (checked) {
                volZoom.setEnabled(false);
            } else {
                boolean upChecked = up ? checked : volUpShutter.isChecked();
                boolean downChecked = !up ? checked : volDownShutter.isChecked();
                if (!upChecked && !downChecked) {
                    volZoom.setEnabled(true);
                }
            }
        } else if (checkBox == volZoom) {
            volUpShutter.setEnabled(!checked);
            volDownShutter.setEnabled(!checked);
        } else if (checkBox == longFocus) {
            preFocus.setEnabled(!checked);
        } else if (checkBox == preFocus) {
            longFocus.setEnabled(!checked);
        } else if (checkBox == storeExtSd)
            ImageManager.setUseRemovableMemPref(checked);
        return true;
    }
}
