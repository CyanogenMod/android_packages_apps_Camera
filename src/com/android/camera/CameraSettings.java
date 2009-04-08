/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 *  CameraSettings
 */
public class CameraSettings extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {
    public static final String KEY_VIDEO_QUALITY = 
            "pref_camera_videoquality_key";
    public static final String KEY_WHITE_BALANCE = 
            "pref_camera_whitebalance_key";
    public static final String KEY_EFFECT = "pref_camera_effect_key";
    public static final boolean DEFAULT_VIDEO_QUALITY_VALUE = true;

    private ListPreference mVideoQuality;
    private ListPreference mWhiteBalance;
    private ListPreference mEffect;
    private Parameters mParameters;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.camera_preferences);

        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateVideoQualitySummary();
        updateWhiteBalanceSummary();
        updateEffectSummary();
    }

    private void initUI() {
        mVideoQuality = (ListPreference) findPreference(KEY_VIDEO_QUALITY);
        mWhiteBalance = (ListPreference) findPreference(KEY_WHITE_BALANCE);
        mEffect = (ListPreference) findPreference(KEY_EFFECT);
        getPreferenceScreen().getSharedPreferences().
                registerOnSharedPreferenceChangeListener(this);

        // Get parameters.
        android.hardware.Camera device = android.hardware.Camera.open();
        mParameters = device.getParameters();
        device.release();

        // Create white balance settings.
        createSettings(mWhiteBalance, Camera.SUPPORTED_WHITE_BALANCE,
                       R.array.pref_camera_whitebalance_entries,
                       R.array.pref_camera_whitebalance_entryvalues);

        // Create effect settings.
        createSettings(mEffect, Camera.SUPPORTED_EFFECT,
                       R.array.pref_camera_effect_entries,
                       R.array.pref_camera_effect_entryvalues);
    }

    private void createSettings(
            ListPreference pref, String paramName, int prefEntriesResId,
            int prefEntryValuesResId) {
        // Get the supported parameter settings.
        String supportedParamStr = mParameters.get(paramName);
        StringTokenizer tokenizer = new StringTokenizer(supportedParamStr, ",");
        ArrayList<CharSequence> supportedParam = new ArrayList<CharSequence>();
        while (tokenizer.hasMoreElements()) {
            supportedParam.add(tokenizer.nextToken());
        }

        // Prepare setting entries and entry values.
        String[] allEntries = getResources().getStringArray(prefEntriesResId);
        String[] allEntryValues = getResources().getStringArray(
                prefEntryValuesResId);
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
        for (int i = 0, len = allEntryValues.length; i < len; i++) {
            int found = supportedParam.indexOf(allEntryValues[i]);
            if (found != -1) {
                entries.add(allEntries[i]);
                entryValues.add(allEntryValues[i]);
            }
        }

        // Set entries and entry values to list preference.
        pref.setEntries(entries.toArray(new CharSequence[entries.size()]));
        pref.setEntryValues(entryValues.toArray(
                new CharSequence[entryValues.size()]));

        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        int index = pref.findIndexOfValue(value);
        if (index == -1) {
            pref.setValueIndex(0);
        }
    }

    private void updateVideoQualitySummary() {
        mVideoQuality.setSummary(mVideoQuality.getEntry());
    }

    private void updateWhiteBalanceSummary() {
        // Set preference summary.
        mWhiteBalance.setSummary(mWhiteBalance.getEntry());
    }

    private void updateEffectSummary() {
        // Set preference summary.
        mEffect.setSummary(mEffect.getEntry());
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(KEY_VIDEO_QUALITY)) {
            updateVideoQualitySummary();
        } else if (key.equals(KEY_WHITE_BALANCE)) {
            updateWhiteBalanceSummary();
        } else if (key.equals(KEY_EFFECT)) {
            updateEffectSummary();
        }
    }
}
