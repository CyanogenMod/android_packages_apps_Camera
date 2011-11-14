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

import com.android.camera.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *  Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
    public static final String KEY_RECORD_LOCATION = RecordLocationPreference.KEY;
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_CAPTURE_MODE = "pref_camera_capturemode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";
    public static final String KEY_ISO = "pref_camera_iso_key";
    public static final String KEY_LENSSHADING = "pref_camera_lensshading_key";
    public static final String KEY_AUTOEXPOSURE = "pref_camera_autoexposure_key";
    public static final String KEY_ANTIBANDING = "pref_camera_antibanding_key";
    public static final String KEY_SHARPNESS = "pref_camera_sharpness_key";
    public static final String KEY_CONTRAST = "pref_camera_contrast_key";
    public static final String KEY_SATURATION = "pref_camera_saturation_key";

    private static final String VIDEO_QUALITY_HD = "hd";
    private static final String VIDEO_QUALITY_WIDE = "wide";
    private static final String VIDEO_QUALITY_HIGH = "high";
    private static final String VIDEO_QUALITY_MMS = "mms";
    private static final String VIDEO_QUALITY_YOUTUBE_HD = "youtubehd";
    private static final String VIDEO_QUALITY_YOUTUBE = "youtube";

    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    public static final int CURRENT_VERSION = 4;
    public static final int CURRENT_LOCAL_VERSION = 1;

    // max video duration in seconds for mms and youtube.
    private static final int MMS_VIDEO_DURATION = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW).duration;
    private static final int YOUTUBE_VIDEO_DURATION = 15 * 60; // 15 mins
    private static final int DEFAULT_VIDEO_DURATION = 30 * 60; // 30 mins

    // MMS video length
    public static final int DEFAULT_VIDEO_DURATION_VALUE = 30;

    private static final String TAG = "CameraSettings";

    private final Context mContext;
    private final Parameters mParameters;
    private final CameraInfo[] mCameraInfo;
    private final int mCameraId;

    private static String sTouchFocusParameter;
    private static boolean sTouchFocusNeedsRect = false;

    // Nvidia 1080p high framerate
    private static boolean mSupportsNvHFR;

    // Samsung camera unadvertised modes
    private static boolean mSamsungCamMode; // camcorder mode
    private static boolean mSamsungContinuousAf;
    private static boolean mSamsungSpecialSettings; // slow_ae and video_recording_gamma
    private static boolean mIsOMAP4Camera;

    public static final String FOCUS_MODE_TOUCH = "touch";

    public CameraSettings(Activity activity, Parameters parameters,
                          CameraInfo[] cameraInfo, int cameraId) {
        mContext = activity;
        mParameters = parameters;
        mCameraInfo = cameraInfo;
        mCameraId = cameraId;
        mIsOMAP4Camera = mContext.getResources().getBoolean(R.bool.isOMAP4Camera);
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
        IconListPreference cameraId =
                (IconListPreference)group.findPreference(KEY_CAMERA_ID);
        ListPreference videoFlashMode =
                group.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);
        ListPreference iso = group.findPreference(KEY_ISO);
        ListPreference lensShade = group.findPreference(KEY_LENSSHADING);
        ListPreference antiBanding = group.findPreference(KEY_ANTIBANDING);
        ListPreference autoExposure = group.findPreference(KEY_AUTOEXPOSURE);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here
        if (videoQuality != null) {
            // Modify video duration settings.
            // The first entry is for MMS video duration, and we need to fill
            // in the device-dependent value (in seconds).
            CharSequence[] entries = videoQuality.getEntries();
            CharSequence[] values = videoQuality.getEntryValues();
            for (int i = 0; i < entries.length; ++i) {
                if (VIDEO_QUALITY_MMS.equals(values[i])) {
                    entries[i] = entries[i].toString().replace(
                            "30", Integer.toString(MMS_VIDEO_DURATION));
                    break;
                }
            }
            if (!isHDCapable(mCameraId)) {
                List<String> supported = new ArrayList<String>();
                for (CharSequence value : values) {
                    if (!VIDEO_QUALITY_HD.equals(value) &&
                            !VIDEO_QUALITY_YOUTUBE_HD.equals(value)) {
                        supported.add(value.toString());
                    }
                }
                filterUnsupportedOptions(group, videoQuality, supported);
            }
            if (!mContext.getResources().getBoolean(R.bool.supportsWideProfile)) {
                List<String> supported = new ArrayList<String>();
                for (CharSequence value : values) {
                    if (!VIDEO_QUALITY_WIDE.equals(value)) {
                        supported.add(value.toString());
                    }
                }
                filterUnsupportedOptions(group, videoQuality, supported);
            }
        }

        // Filter out unsupported settings / options
        if (pictureSize != null) {
            final List<String> pictureSizes = sizeListToStringList(mParameters.getSupportedPictureSizes());
            final String filteredSizes = mContext.getResources().getString(R.string.filtered_pictureSizes);
            if (filteredSizes != null && filteredSizes.length() > 0) {
                pictureSizes.removeAll(Arrays.asList(filteredSizes.split(",")));
            }
            filterUnsupportedOptions(group, pictureSize, pictureSizes);
        }
        if (whiteBalance != null) {
            filterUnsupportedOptions(group,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }
        if (colorEffect != null) {
            if (isFrontFacingCamera()) {
                String supportedEffects = mContext.getResources().getString(R.string.ffc_supportedEffects);
                if (supportedEffects != null && supportedEffects.length() > 0) {
                    filterUnsupportedOptions(group, colorEffect,
                            Arrays.asList(supportedEffects.split(",")));
                }
            } else {
                filterUnsupportedOptions(group,
                        colorEffect, mParameters.getSupportedColorEffects());
            }
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
            if (isFrontFacingCamera() && !mContext.getResources().getBoolean(R.bool.ffc_canFocus)) {
                filterUnsupportedOptions(group, focusMode, new ArrayList<String>());
            } else {
                List<String> focusModes = mParameters.getSupportedFocusModes();
                if (checkTouchFocus()) {
                    focusModes.add(FOCUS_MODE_TOUCH);
                }
                filterUnsupportedOptions(group, focusMode, focusModes);
            }
        }

        if (videoFlashMode != null) {
            filterUnsupportedOptions(group,
                    videoFlashMode, mParameters.getSupportedFlashModes());
        }
        if (exposure != null) buildExposureCompensation(group, exposure);
        if (cameraId != null) buildCameraId(group, cameraId);
        if (iso != null) {
            filterUnsupportedOptions(group,
                    iso, mParameters.getSupportedIsoValues());
        }
        if (lensShade!= null) {
            filterUnsupportedOptions(group,
                    lensShade, mParameters.getSupportedLensShadeModes());
        }
         if (antiBanding != null) {
             filterUnsupportedOptions(group,
                     antiBanding, mParameters.getSupportedAntibanding());
         }
         if (autoExposure != null) {
             filterUnsupportedOptions(group,
                     autoExposure, mParameters.getSupportedAutoexposure());
         }
    }

    private boolean checkTouchFocus() {
        sTouchFocusParameter = mContext.getResources().getString(R.string.touchFocusParameter);
        sTouchFocusNeedsRect = mContext.getResources().getBoolean(R.bool.touchFocusNeedsRect);

        if (sTouchFocusParameter != null) {
            return true;
        } else {
            return false;
        }
    }

    public static String getTouchFocusParameterName() {
        return sTouchFocusParameter;
    }

    public static boolean getTouchFocusNeedsRect() {
        return sTouchFocusNeedsRect;
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
            PreferenceGroup group, IconListPreference cameraId) {
        int numOfCameras = mCameraInfo.length;
        if (numOfCameras < 2) {
            removePreference(group, cameraId.getKey());
            return;
        }

        CharSequence entries[] = new CharSequence[numOfCameras];
        CharSequence entryValues[] = new CharSequence[numOfCameras];
        int[] iconIds = new int[numOfCameras];
        int[] largeIconIds = new int[numOfCameras];
        for (int i = 0; i < numOfCameras; i++) {
            entryValues[i] = Integer.toString(i);
            if (mCameraInfo[i].facing == CameraInfo.CAMERA_FACING_FRONT) {
                entries[i] = mContext.getString(
                        R.string.pref_camera_id_entry_front);
                iconIds[i] = R.drawable.ic_menuselect_camera_facing_front;
                largeIconIds[i] = R.drawable.ic_viewfinder_camera_facing_front;
            } else if (mIsOMAP4Camera && i>=2) {
                entries[i] = mContext.getString(
                        R.string.pref_camera_id_entry_dual);
                iconIds[i] = R.drawable.ic_menuselect_camera_facing_back;
                largeIconIds[i] = R.drawable.ic_viewfinder_camera_facing_back;
            } else {
                entries[i] = mContext.getString(
                        R.string.pref_camera_id_entry_back);
                iconIds[i] = R.drawable.ic_menuselect_camera_facing_back;
                largeIconIds[i] = R.drawable.ic_viewfinder_camera_facing_back;
            }
        }
        cameraId.setEntries(entries);
        cameraId.setEntryValues(entryValues);
        cameraId.setIconIds(iconIds);
        cameraId.setLargeIconIds(largeIconIds);

        mSupportsNvHFR = mContext.getResources().getBoolean(R.bool.supportsNvHighBitrateFullHD);
        mSamsungCamMode = mContext.getResources().getBoolean(R.bool.needsSamsungCamMode);
        mSamsungContinuousAf = mContext.getResources().getBoolean(R.bool.needsSamsungContinuousAf);
        mSamsungSpecialSettings = mContext.getResources().getBoolean(R.bool.needsSamsungSpecialSettings);
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

    public static void upgradeAllPreferences(ComboPreferences pref) {
        upgradeGlobalPreferences(pref.getGlobal());
        upgradeLocalPreferences(pref.getLocal());
    }

    public static final String getDefaultVideoQuality(int cameraId) {
        return isHDCapable(cameraId) ? VIDEO_QUALITY_HD : VIDEO_QUALITY_HIGH;
    }

    public static final boolean isHDCapable(int cameraId) {
        boolean ret = false;
        try {
            ret = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HD) != null;
        } catch (Exception e) {
            // Native code throws exception if not found
        }
        return ret;
    }

    public static int getVideoQuality(String quality) {
        final int q;
        if (VIDEO_QUALITY_YOUTUBE_HD.equals(quality) || VIDEO_QUALITY_HD.equals(quality)) {
            q = CamcorderProfile.QUALITY_HD;
        } else if (VIDEO_QUALITY_WIDE.equals(quality)) {
            q = CamcorderProfile.QUALITY_WIDE;
        } else if (VIDEO_QUALITY_YOUTUBE.equals(quality) || VIDEO_QUALITY_HIGH.equals(quality)) {
            q = CamcorderProfile.QUALITY_HIGH;
        } else {
            q = CamcorderProfile.QUALITY_LOW;
        }
        return q;
    }

    public static int getVidoeDurationInMillis(String quality) {
        if (VIDEO_QUALITY_MMS.equals(quality)) {
            return MMS_VIDEO_DURATION * 1000;
        } else if (VIDEO_QUALITY_YOUTUBE.equals(quality) ||
                VIDEO_QUALITY_YOUTUBE_HD.equals(quality)) {
            return YOUTUBE_VIDEO_DURATION * 1000;
        }
        return DEFAULT_VIDEO_DURATION * 1000;
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

    public static boolean isZoomSupported(Context context, int cameraId) {
        return CameraHolder.instance().getCameraInfo()[cameraId].facing != CameraInfo.CAMERA_FACING_FRONT
                || context.getResources().getBoolean(R.bool.ffc_canZoom);
    }

    public static void dumpParameters(Parameters params) {
        Set<String> sortedParams = new TreeSet<String>();
        sortedParams.addAll(Arrays.asList(params.flatten().split(";")));
        Log.d(TAG, "Parameters: " + sortedParams.toString());
    }

    private boolean isFrontFacingCamera() {
        return mCameraInfo[mCameraId].facing == CameraInfo.CAMERA_FACING_FRONT;
    }

    public static boolean isVideoZoomSupported(Context context, int cameraId, Parameters params) {
        boolean ret = isZoomSupported(context, cameraId);
        if (ret) {
            // No zoom at 720P currently. Driver limitation?
            Size size = params.getPreviewSize();
            ret = !(size.width == 1280 && size.height == 720);
        }
        return ret;
    }

    /**
     * Tell camera driver whether video mode is enabled or not,
     * if supported/requested by driver.
     *
     * @param params
     * @param on
     */
    public static void setVideoMode(Parameters params, boolean on) {
        if (params.get("cam-mode") != null) {
            params.set("cam-mode", on ? "1" : "0");
        } else if (params.get("nv-mode-hint") != null) {
            params.set("nv-mode-hint", on ? "video" : "still");
        } else if (mSamsungCamMode) {
            params.set("cam_mode", on ? "1" : "0");
        } else if (mIsOMAP4Camera) {
            params.set("mode", on ? "video-mode" : "high-quality");
        }

        if (on && params.get("focus-mode-values").indexOf("continuous-video") != -1) {
            // Galaxy S2
            params.set("focus-mode", "continuous-video");
        }

        if (on && params.get("focus-mode-values").indexOf("caf") != -1) {
            // OMAP4
            params.set("focus-mode", "caf");
        }

        if (mSamsungSpecialSettings) {
            params.set("video_recording_gamma", on ? "on" : "off");
            params.set("slow_ae", on ? "on" : "off");
            params.set("iso", on ? "movie" : "auto");
            params.set("metering", on ? "matrix" : "center");

            if (on) {
                params.set("antibanding", "50hz");
            }
        }
    }

    /**
     * Sets continuous-autofocus video mode on HTC cameras that support it.
     *
     * @param params
     * @param on
     */
    public static void setContinuousAf(Parameters params, boolean on) {
        if (params.get("enable-caf") != null) {
            params.set("enable-caf", on ? "on" : "off");
        } else if (mSamsungContinuousAf) {
            params.set("continuous_af", on ? 1 : 0);
        }
    }

    /**
     * Changes nv-sensor-mode to enable higher framerate video recording 
     * for some tegra 2 devices
     *
     * @param params
     */
    public static void enableHighFrameRateFHD(Parameters params) {
        if (!mSupportsNvHFR || params.get("nv-sensor-mode") == null)
            return;
        Log.v(TAG,"Enabling 1080p@30fps on nvcamera");
        // Not listed as a supported parameter, force it
        params.set("nv-sensor-mode", "3264x1224x30"); 
        // Default is 1600x1200, which causes nv-sensor-mode to be 
        // reset to 3264x2448x15 when attempting Full HD recording.
        params.setPreviewSize(1280, 720); 
        params.set("preview-frame-rate", "30"); 
    }

}
