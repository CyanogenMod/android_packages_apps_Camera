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
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

/**
 *  CameraSettings
 */
public class CameraSettings extends PreferenceActivity
    implements OnSharedPreferenceChangeListener
{
    public static final String KEY_VIDEO_QUALITY = "pref_camera_videoquality_key";
    public static final boolean DEFAULT_VIDEO_QUALITY_VALUE = true;

    private ListPreference mVideoQuality;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVideoQuality();
    }

    private void initUI() {
        mVideoQuality = (ListPreference) findPreference(KEY_VIDEO_QUALITY);
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
           }

    }
}

