/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 *  Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
    public static final String KEY_RECORD_LOCATION = RecordLocationPreference.KEY;
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL = "pref_video_time_lapse_frame_interval_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";

    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    public static final int CURRENT_VERSION = 4;
    public static final int CURRENT_LOCAL_VERSION = 1;

    // max video duration in seconds for mms and youtube.
    private static final int MMS_VIDEO_DURATION = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW).duration;
    private static final int YOUTUBE_VIDEO_DURATION = 15 * 60; // 15 mins
    private static final int DEFAULT_VIDEO_DURATION = 0; // no limit

    public static final String DEFAULT_VIDEO_QUALITY_VALUE = "high";

    private static final String TAG = "CameraSettings";

    private final Context mContext;
    private final Parameters mParameters;
    private final CameraInfo[] mCameraInfo;
    private final int mCameraId;

    public CameraSettings(Activity activity, Parameters parameters,
                          int cameraId, CameraInfo[] cameraInfo) {
        mContext = activity;
        mParameters = parameters;
        mCameraId = cameraId;
        mCameraInfo = cameraInfo;
    }

    public PreferenceGroup getPreferenceGroup(int preferenceRes) {
        PreferenceInflater inflater = new PreferenceInflater(mContext);
        PreferenceGroup group =
                (PreferenceGroup) inflater.inflate(preferenceRes);
        initPreference(group);
        return group;
    }

    public static void initialCameraPictureSize(
            Context context, Parameters parameters) {
        // When launching the camera app first time, we will set the picture
        // size to the first one in the list defined in "arrays.xml" and is also
        // supported by the driver.
        List<Size> supported = parameters.getSupportedPictureSizes();
        if (supported == null) return;
        for (String candidate : context.getResources().getStringArray(
                R.array.pref_camera_picturesize_entryvalues)) {
            if (setCameraPictureSize(candidate, supported, parameters)) {
                SharedPreferences.Editor editor = ComboPreferences
                        .get(context).edit();
                editor.putString(KEY_PICTURE_SIZE, candidate);
                editor.apply();
                return;
            }
        }
        Log.e(TAG, "No supported picture size found");
    }

    public static void removePreferenceFromScreen(
            PreferenceGroup group, String key) {
        removePreference(group, key);
    }

    public static boolean setCameraPictureSize(
            String candidate, List<Size> supported, Parameters parameters) {
        int index = candidate.indexOf('x');
        if (index == NOT_FOUND) return false;
        int width = Integer.parseInt(candidate.substring(0, index));
        int height = Integer.parseInt(candidate.substring(index + 1));
        for (Size size: supported) {
            if (size.width == width && size.height == height) {
                parameters.setPictureSize(width, height);
                return true;
            }
        }
        return false;
    }

    private void initPreference(PreferenceGroup group) {
        ListPreference videoQuality = group.findPreference(KEY_VIDEO_QUALITY);
        ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =  group.findPreference(KEY_WHITE_BALANCE);
        ListPreference colorEffect = group.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
        ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
        ListPreference exposure = group.findPreference(KEY_EXPOSURE);
        IconListPreference cameraIdPref =
                (IconListPreference)group.findPreference(KEY_CAMERA_ID);
        ListPreference videoFlashMode =
                group.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here
        if (videoQuality != null) {
            initVideoQuality(videoQuality);
        }

        if (pictureSize != null) {
            filterUnsupportedOptions(group, pictureSize, sizeListToStringList(
                    mParameters.getSupportedPictureSizes()));
        }
        if (whiteBalance != null) {
            filterUnsupportedOptions(group,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }
        if (colorEffect != null) {
            filterUnsupportedOptions(group,
                    colorEffect, mParameters.getSupportedColorEffects());
        }
        if (sceneMode != null) {
            filterUnsupportedOptions(group,
                    sceneMode, mParameters.getSupportedSceneModes());
        }
        if (flashMode != null) {
            filterUnsupportedOptions(group,
                    flashMode, mParameters.getSupportedFlashModes());
        }
        if (focusMode != null) {
            filterUnsupportedOptions(group,
                    focusMode, mParameters.getSupportedFocusModes());
        }
        if (videoFlashMode != null) {
            filterUnsupportedOptions(group,
                    videoFlashMode, mParameters.getSupportedFlashModes());
        }
        if (exposure != null) buildExposureCompensation(group, exposure);
        if (cameraIdPref != null) buildCameraId(group, cameraIdPref);
    }

    private static List<String> getSupportedTimeLapseProfiles(int cameraId) {
        ArrayList<String> supportedProfiles = new ArrayList<String>();
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_480P)) {
            supportedProfiles.add(Integer.toString(CamcorderProfile.QUALITY_TIME_LAPSE_480P));
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_720P)) {
            supportedProfiles.add(Integer.toString(CamcorderProfile.QUALITY_TIME_LAPSE_720P));
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_1080P)) {
            supportedProfiles.add(Integer.toString(CamcorderProfile.QUALITY_TIME_LAPSE_1080P));
        }

        return supportedProfiles;
    }

    private void buildExposureCompensation(
            PreferenceGroup group, ListPreference exposure) {
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (max == 0 && min == 0) {
            removePreference(group, exposure.getKey());
            return;
        }
        float step = mParameters.getExposureCompensationStep();

        // show only integer values for exposure compensation
        int maxValue = (int) Math.floor(max * step);
        int minValue = (int) Math.ceil(min * step);
        CharSequence entries[] = new CharSequence[maxValue - minValue + 1];
        CharSequence entryValues[] = new CharSequence[maxValue - minValue + 1];
        for (int i = minValue; i <= maxValue; ++i) {
            entryValues[maxValue - i] = Integer.toString(Math.round(i / step));
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            entries[maxValue - i] = builder.append(i).toString();
        }
        exposure.setEntries(entries);
        exposure.setEntryValues(entryValues);
    }

    private void buildCameraId(
            PreferenceGroup group, IconListPreference preference) {
        int numOfCameras = mCameraInfo.length;
        if (numOfCameras < 2) {
            removePreference(group, preference.getKey());
            return;
        }

        CharSequence[] entryValues = new CharSequence[2];
        for (int i = 0 ; i < mCameraInfo.length ; ++i) {
            int index =
                    (mCameraInfo[i].facing == CameraInfo.CAMERA_FACING_FRONT)
                    ? CameraInfo.CAMERA_FACING_FRONT
                    : CameraInfo.CAMERA_FACING_BACK;
            if (entryValues[index] == null) {
                entryValues[index] = "" + i;
                if (entryValues[((index == 1) ? 0 : 1)] != null) break;
            }
        }
        preference.setEntryValues(entryValues);
    }

    private static boolean removePreference(PreferenceGroup group, String key) {
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    private void filterUnsupportedOptions(PreferenceGroup group,
            ListPreference pref, List<String> supported) {

        // Remove the preference if the parameter is not supported or there is
        // only one options for the settings.
        if (supported == null || supported.size() <= 1) {
            removePreference(group, pref.getKey());
            return;
        }

        pref.filterUnsupported(supported);
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return;
        }

        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        if (pref.findIndexOfValue(value) == NOT_FOUND) {
            pref.setValueIndex(0);
        }
    }

    private static List<String> sizeListToStringList(List<Size> sizes) {
        ArrayList<String> list = new ArrayList<String>();
        for (Size size : sizes) {
            list.add(String.format("%dx%d", size.width, size.height));
        }
        return list;
    }

    public static void upgradeLocalPreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_LOCAL_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_LOCAL_VERSION) return;
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(KEY_LOCAL_VERSION, CURRENT_LOCAL_VERSION);
        editor.apply();
    }

    public static void upgradeGlobalPreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_VERSION) return;

        SharedPreferences.Editor editor = pref.edit();
        if (version == 0) {
            // We won't use the preference which change in version 1.
            // So, just upgrade to version 1 directly
            version = 1;
        }
        if (version == 1) {
            // Change jpeg quality {65,75,85} to {normal,fine,superfine}
            String quality = pref.getString(KEY_JPEG_QUALITY, "85");
            if (quality.equals("65")) {
                quality = "normal";
            } else if (quality.equals("75")) {
                quality = "fine";
            } else {
                quality = "superfine";
            }
            editor.putString(KEY_JPEG_QUALITY, quality);
            version = 2;
        }
        if (version == 2) {
            editor.putString(KEY_RECORD_LOCATION,
                    pref.getBoolean(KEY_RECORD_LOCATION, false)
                    ? RecordLocationPreference.VALUE_ON
                    : RecordLocationPreference.VALUE_NONE);
            version = 3;
        }
        if (version == 3) {
            // Just use video quality to replace it and
            // ignore the current settings.
            editor.remove("pref_camera_videoquality_key");
            editor.remove("pref_camera_video_duration_key");
        }
        editor.putInt(KEY_VERSION, CURRENT_VERSION);
        editor.apply();
    }

    public static boolean getVideoQuality(Context context, String quality) {
        return context.getString(R.string.pref_video_quality_youtube).equals(quality)
                || context.getString(R.string.pref_video_quality_high).equals(quality);
    }

    public static int getVideoDurationInMillis(Context context, String quality) {
        if (context.getString(R.string.pref_video_quality_mms).equals(quality)) {
            return MMS_VIDEO_DURATION * 1000;
        } else if (context.getString(R.string.pref_video_quality_youtube).equals(quality)) {
            return YOUTUBE_VIDEO_DURATION * 1000;
        }
        return DEFAULT_VIDEO_DURATION;
    }

    public static int readPreferredCameraId(SharedPreferences pref) {
        return Integer.parseInt(pref.getString(KEY_CAMERA_ID, "0"));
    }

    public static void writePreferredCameraId(SharedPreferences pref,
            int cameraId) {
        Editor editor = pref.edit();
        editor.putString(KEY_CAMERA_ID, Integer.toString(cameraId));
        editor.apply();
    }


    public static void restorePreferences(Context context,
            ComboPreferences preferences, Parameters parameters) {
        int currentCameraId = readPreferredCameraId(preferences);

        // Clear the preferences of both cameras.
        int backCameraId = CameraHolder.instance().getBackCameraId();
        if (backCameraId != -1) {
            preferences.setLocalId(context, backCameraId);
            Editor editor = preferences.edit();
            editor.clear();
            editor.apply();
        }
        int frontCameraId = CameraHolder.instance().getFrontCameraId();
        if (frontCameraId != -1) {
            preferences.setLocalId(context, frontCameraId);
            Editor editor = preferences.edit();
            editor.clear();
            editor.apply();
        }

        upgradeGlobalPreferences(preferences.getGlobal());
        upgradeLocalPreferences(preferences.getLocal());

        // Write back the current camera id because parameters are related to
        // the camera. Otherwise, we may switch to the front camera but the
        // initial picture size is that of the back camera.
        initialCameraPictureSize(context, parameters);
        writePreferredCameraId(preferences, currentCameraId);
    }

    private void initVideoQuality(ListPreference videoQuality) {
        CharSequence[] entries = videoQuality.getEntries();
        CharSequence[] values = videoQuality.getEntryValues();
        if (Util.isMmsCapable(mContext)) {
            // We need to fill in the device-dependent value (in seconds).
            for (int i = 0; i < entries.length; ++i) {
                if (mContext.getString(R.string.pref_video_quality_mms).equals(values[i])) {
                    entries[i] = entries[i].toString().replace(
                            "30", Integer.toString(MMS_VIDEO_DURATION));
                    break;
                }
            }
        } else {
            // The device does not support mms. Remove it. Using
            // filterUnsupported is not efficient but adding a new method
            // for remove is not worth it.
            ArrayList<String> supported = new ArrayList<String>();
            for (int i = 0; i < entries.length; ++i) {
                if (!mContext.getString(R.string.pref_video_quality_mms).equals(values[i])) {
                    supported.add(values[i].toString());
                }
            }
            videoQuality.filterUnsupported(supported);
        }
    }
}
