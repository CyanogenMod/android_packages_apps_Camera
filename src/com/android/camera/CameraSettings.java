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
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 *  CameraSettings
 */
public class CameraSettings extends PreferenceActivity
    implements OnSharedPreferenceChangeListener
{
    public static final String KEY_VIDEO_QUALITY = "pref_camera_videoquality_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final boolean DEFAULT_VIDEO_QUALITY_VALUE = true;

    private ListPreference mVideoQuality;
    private ListPreference mWhiteBalance;
    private Parameters mParameters;

    public CameraSettings()
    {
    }

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.camera_preferences);

        initUI();

        // Get parameters.
        android.hardware.Camera device = android.hardware.Camera.open();
        mParameters = device.getParameters();
        device.release();

        createWhiteBalanceSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateVideoQuality();
        updateWhiteBalance();
    }

    private void initUI() {
        mVideoQuality = (ListPreference) findPreference(KEY_VIDEO_QUALITY);
        mWhiteBalance = (ListPreference) findPreference(KEY_WHITE_BALANCE);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    private void updateVideoQuality() {
        boolean vidQualityValue = getBooleanPreference(mVideoQuality, DEFAULT_VIDEO_QUALITY_VALUE);
        int vidQualityIndex = vidQualityValue ? 1 : 0;
        String[] vidQualities =
            getResources().getStringArray(R.array.pref_camera_videoquality_entries);
        String vidQuality = vidQualities[vidQualityIndex];
        mVideoQuality.setSummary(vidQuality);
    }

    private void createWhiteBalanceSettings() {
        // Get the supported white balance settings.
        String supportedWbStr = mParameters.get("whitebalance-values");
        StringTokenizer tokenizer = new StringTokenizer(supportedWbStr, ",");
        ArrayList<CharSequence> supportedWb = new ArrayList<CharSequence>();
        while (tokenizer.hasMoreElements()) {
            supportedWb.add(tokenizer.nextToken());
        }

        // Prepare white balance entries and entry values.
        String[] allWbEntries = getResources().getStringArray(
                R.array.pref_camera_whitebalance_entries);
        String[] allWbEntryValues = getResources().getStringArray(
                R.array.pref_camera_whitebalance_entryvalues);
        ArrayList<CharSequence> wbEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> wbEntryValues = new ArrayList<CharSequence>();
        for (int i = 0, len = allWbEntryValues.length; i < len; i++) {
            int found = supportedWb.indexOf(allWbEntryValues[i]);
            if (found != -1) {
                wbEntries.add(allWbEntries[i]);
                wbEntryValues.add(allWbEntryValues[i]);
            }
        }

        // Set white balance entries and entry values to list preference.
        mWhiteBalance.setEntries(wbEntries.toArray(
                new CharSequence[wbEntries.size()]));
        mWhiteBalance.setEntryValues(wbEntryValues.toArray(
                new CharSequence[wbEntryValues.size()]));

        String value = mWhiteBalance.getValue();
        int index = mWhiteBalance.findIndexOfValue(value);
        if (index == -1) {
            mWhiteBalance.setValueIndex(0);
        }
    }

    private void updateWhiteBalance() {
        // Set preference summary.
        mWhiteBalance.setSummary(mWhiteBalance.getEntry());
    }

    private static int getIntPreference(ListPreference preference, int defaultValue) {
        String s = preference.getValue();
        int result = defaultValue;
        try {
            result = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            // Ignore, result is already the default value.
        }
        return result;
    }

    private boolean getBooleanPreference(ListPreference preference, boolean defaultValue) {
        return getIntPreference(preference, defaultValue ? 1 : 0) != 0;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(KEY_VIDEO_QUALITY)) {
            updateVideoQuality();
        } else if (key.equals(KEY_WHITE_BALANCE)) {
            updateWhiteBalance();
        }
    }
}

