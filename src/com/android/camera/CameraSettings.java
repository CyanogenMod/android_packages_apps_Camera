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
import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;

import java.util.ArrayList;
import java.util.List;

/**
 *  CameraSettings
 */
public class CameraSettings extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {
    public static final String KEY_VIDEO_QUALITY =
            "pref_camera_videoquality_key";
    public static final String KEY_VIDEO_DURATION =
            "pref_camera_video_duration_key";
    public static final String KEY_VERSION = "pref_version_key";
    public static final int CURRENT_VERSION = 1;
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_WHITE_BALANCE =
            "pref_camera_whitebalance_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final boolean DEFAULT_VIDEO_QUALITY_VALUE = true;

    // MMS video length
    public static final int DEFAULT_VIDEO_DURATION_VALUE = -1;

    // max mms video duration in seconds.
    public static final int MMS_VIDEO_DURATION =
            SystemProperties.getInt("ro.media.enc.lprof.duration", 60);

    private ListPreference mVideoQuality;
    private ListPreference mVideoDuration;
    private ListPreference mPictureSize;
    private ListPreference mJpegQuality;
    private ListPreference mFocusMode;
    private ListPreference mWhiteBalance;
    private ListPreference mColorEffect;
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
        updateVideoDurationSummary();
        updatePictureSizeSummary();
        updateJpegQualitySummary();
        updateFocusModeSummary();
        updateWhiteBalanceSummary();
        updateEffectSummary();
    }

    private ArrayList<String> sizeToStr(List<Size> sizes) {
        if (sizes == null) return null;

        ArrayList<String> sizesInString = new ArrayList<String>();
        for (Size size : sizes) {
            sizesInString.add("" + size.width + "x" + size.height);
        }
        return sizesInString;
    }

    private void initUI() {
        mVideoQuality = (ListPreference) findPreference(KEY_VIDEO_QUALITY);
        mVideoDuration = (ListPreference) findPreference(KEY_VIDEO_DURATION);
        mPictureSize = (ListPreference) findPreference(KEY_PICTURE_SIZE);
        mJpegQuality = (ListPreference) findPreference(KEY_JPEG_QUALITY);
        mFocusMode = (ListPreference) findPreference(KEY_FOCUS_MODE);
        mWhiteBalance = (ListPreference) findPreference(KEY_WHITE_BALANCE);
        mColorEffect = (ListPreference) findPreference(KEY_COLOR_EFFECT);

        SharedPreferences pref = getPreferenceScreen().getSharedPreferences();
        upgradePreferences(pref);
        pref.registerOnSharedPreferenceChangeListener(this);

        // Get parameters.
        android.hardware.Camera device;
        try {
            device = CameraHolder.instance().open();
        } catch (CameraHardwareException e) {
            Resources ress = getResources();
            Util.showFatalErrorAndFinish(this,
                    ress.getString(R.string.camera_error_title),
                    ress.getString(R.string.cannot_connect_camera));
            return;
        }
        mParameters = device.getParameters();
        CameraHolder.instance().release();

        // Create picture size settings.
        List<Size> pictureSizes = mParameters.getSupportedPictureSizes();
        ArrayList<String> pictureSizesInString = sizeToStr(pictureSizes);
        createSettings(mPictureSize, pictureSizesInString);

        // Create white balance settings.
        createSettings(mWhiteBalance, mParameters.getSupportedWhiteBalance());

        // Create color effect settings.
        createSettings(mColorEffect, mParameters.getSupportedColorEffects());

        // Modify video duration settings.
        // The first entry is for MMS video duration, and we need to fill in the
        // device-dependent value (in seconds).
        CharSequence[] entries = mVideoDuration.getEntries();
        entries[0] = String.format(entries[0].toString(), MMS_VIDEO_DURATION);

        // Set default JPEG quality value if it is empty.
        if (mJpegQuality.getValue() == null) {
            mJpegQuality.setValue(getString(
                R.string.pref_camera_jpegquality_default));
        }

        // Set default focus mode value if it is empty.
        if (mFocusMode.getValue() == null) {
            mFocusMode.setValue(getString(
                R.string.pref_camera_focusmode_default));
        }
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    private boolean removePreference(PreferenceGroup group, Preference remove) {
        if (group.removePreference(remove)) return true;

        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference child = group.getPreference(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, remove)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createSettings(
            ListPreference pref, List<String> supportedParam) {
        // Remove the preference if the parameter is not supported.
        if (supportedParam == null) {
            removePreference(getPreferenceScreen(), pref);
            return;
        }

        // Prepare setting entries and entry values.
        CharSequence[] allEntries = pref.getEntries();
        CharSequence[] allEntryValues = pref.getEntryValues();
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

    private void updateVideoDurationSummary() {
        mVideoDuration.setSummary(mVideoDuration.getEntry());
    }

    private void updatePictureSizeSummary() {
        mPictureSize.setSummary(mPictureSize.getEntry());
    }

    private void updateJpegQualitySummary() {
        mJpegQuality.setSummary(mJpegQuality.getEntry());
    }

    private void updateWhiteBalanceSummary() {
        mWhiteBalance.setSummary(mWhiteBalance.getEntry());
    }

    private void updateFocusModeSummary() {
        mFocusMode.setSummary(mFocusMode.getEntry());
    }

    private void updateEffectSummary() {
        mColorEffect.setSummary(mColorEffect.getEntry());
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(KEY_VIDEO_QUALITY)) {
            updateVideoQualitySummary();
        } else if (key.equals(KEY_VIDEO_DURATION)) {
            updateVideoDurationSummary();
        } else if (key.equals(KEY_PICTURE_SIZE)) {
            updatePictureSizeSummary();
        } else if (key.equals(KEY_JPEG_QUALITY)) {
            updateJpegQualitySummary();
        } else if (key.equals(KEY_FOCUS_MODE)) {
            updateFocusModeSummary();
        } else if (key.equals(KEY_WHITE_BALANCE)) {
            updateWhiteBalanceSummary();
        } else if (key.equals(KEY_COLOR_EFFECT)) {
            updateEffectSummary();
        }
    }

    public static void upgradePreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }

        if (version == 0) {
            SharedPreferences.Editor editor = pref.edit();
            // For old version, change 1 to -1 for video duration preference.
            if (pref.getString(KEY_VIDEO_DURATION, "1").equals("1")) {
                editor.putString(KEY_VIDEO_DURATION, "-1");
            }
            editor.putInt(KEY_VERSION, CURRENT_VERSION);
            editor.commit();
        }
    }
}
