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
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.CameraSwitch;
import android.media.CamcorderProfile;
import android.media.EncoderCapabilities;
import android.media.EncoderCapabilities.VideoEncoderCap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 *  Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_RECORD_LOCATION = RecordLocationPreference.KEY;
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_QUICK_CAPTURE = "pref_camera_quickcapture_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_ISO = "pref_camera_iso_key";
    public static final String KEY_LENSSHADING = "pref_camera_lensshading_key";
    public static final String KEY_AUTOEXPOSURE = "pref_camera_autoexposure_key";
    public static final String KEY_ANTIBANDING = "pref_camera_antibanding_key";
    public static final String KEY_SHARPNESS = "pref_camera_sharpness_key";
    public static final String KEY_CONTRAST = "pref_camera_contrast_key";
    public static final String KEY_SATURATION = "pref_camera_saturation_key";
    public static final String KEY_BRIGHTNESS = "pref_camera_brightness_key";
    
    public static final String QUICK_CAPTURE_ON = "on";
    public static final String QUICK_CAPTURE_OFF = "off";

    private static final String VIDEO_QUALITY_HIGH = "high";
    private static final String VIDEO_QUALITY_MMS = "mms";
    private static final String VIDEO_QUALITY_YOUTUBE = "youtube";
    
    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    public static final int CURRENT_VERSION = 4;

    // max video duration in seconds for mms and youtube.
    private static final int MMS_VIDEO_DURATION = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW).duration;
    private static final int YOUTUBE_VIDEO_DURATION = 15 * 60; // 15 mins - now you can upload 15 min to youtube!
    private static final int DEFAULT_VIDEO_DURATION = 30 * 60; // 10 mins

    public static final String DEFAULT_VIDEO_QUALITY_VALUE = "high";
    public static final String KEY_VIDEO_SIZE = "pref_camera_videosize_key";
    public static final String KEY_VIDEO_ENCODER = "pref_camera_videoencoder_key";
    public static final String KEY_AUDIO_ENCODER = "pref_camera_audioencoder_key";
    public static final String KEY_VIDEO_DURATION = "pref_camera_video_duration_key";

    // MMS video length
    public static final int DEFAULT_VIDEO_DURATION_VALUE = 30;

    private static final String TAG = "CameraSettings";

    private final Context mContext;
    private final Parameters mParameters;

    public static final int CAMERA_MODE = 0;
    public static final int VIDEO_MODE = 1;
    public static final String HTC_CAM_MODE = "cam-mode";
    
    public CameraSettings(Activity activity, Parameters parameters) {
        mContext = activity;
        mParameters = parameters;
    }
    
    public static String getDefaultVideoQualityValue() {
        return VIDEO_QUALITY_HIGH;
    }
    
    public synchronized PreferenceGroup getPreferenceGroup(int preferenceRes) {
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
                SharedPreferences.Editor editor = context.getSharedPreferences(
                        CameraHolder.instance().getCameraNode(), Context.MODE_PRIVATE).edit();
                editor.putString(KEY_PICTURE_SIZE, candidate);
                editor.commit();
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
        if(supported == null)
            return false;

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

    public static boolean hasTouchFocusSupport(Parameters parameters) {
        // Not the best way to check, but works for HTC cameras
        return parameters.get("taking-picture-zoom") != null && isMainCamera();
    }

    private void initPreference(PreferenceGroup group) {
        ListPreference videoQuality = group.findPreference(KEY_VIDEO_QUALITY);
        ListPreference videoSize = group.findPreference(KEY_VIDEO_SIZE);
        ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =  group.findPreference(KEY_WHITE_BALANCE);
        ListPreference colorEffect = group.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
        ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
        ListPreference exposure = group.findPreference(KEY_EXPOSURE);
        ListPreference videoFlashMode =
                group.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);
        ListPreference mIso = group.findPreference(KEY_ISO);
        ListPreference lensShade = group.findPreference(KEY_LENSSHADING);
        ListPreference antiBanding = group.findPreference(KEY_ANTIBANDING);
        ListPreference autoExposure = group.findPreference(KEY_AUTOEXPOSURE);
        ListPreference sharpness = group.findPreference(KEY_SHARPNESS);
        ListPreference contrast = group.findPreference(KEY_CONTRAST);
        ListPreference saturation = group.findPreference(KEY_SATURATION);
        ListPreference brightness = group.findPreference(KEY_BRIGHTNESS);
        ListPreference videoEncoder = group.findPreference(KEY_VIDEO_ENCODER);
        ListPreference audioEncoder = group.findPreference(KEY_AUDIO_ENCODER);
        
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
        }

        // Filter out unsupported settings / options    
        if (audioEncoder != null) {
            filterUnsupportedOptions(group, audioEncoder, new ArrayList<String>(VideoCamera.AUDIO_ENCODER_TABLE.keySet()));
        }
        
        if (videoSize != null && videoEncoder != null) {
            filterUnsupportedOptions(group, videoEncoder, new ArrayList<String>(VideoCamera.VIDEO_ENCODER_TABLE.keySet()));
            
            final int selectedEncoder = VideoCamera.VIDEO_ENCODER_TABLE.get(videoEncoder.getValue());
            VideoEncoderCap cap = null;
            for (VideoEncoderCap vc : EncoderCapabilities.getVideoEncoders()) {
                if (vc.mCodec == selectedEncoder) {
                    cap = vc;
                    break;
                }
            }
            if (cap == null) {
                Log.wtf(TAG, "Unknown encoder! " + selectedEncoder);
            }
            
            final List<Size> validSizesForEncoder = new ArrayList<Size>();
            for (Size size : mParameters.getSupportedPreviewSizes()) {
                if (!CameraSwitch.SWITCH_CAMERA_MAIN.equals(CameraHolder.instance().getCameraNode())) {
                    // Terrible hack, this should be done another way.
                    if (size.width > 640 || size.height > 480) {
                        continue;
                    }
                }
                if (size.width <= cap.mMaxFrameWidth && size.height <= cap.mMaxFrameHeight) {
                    validSizesForEncoder.add(size);
                }
            }
            filterUnsupportedOptions(group, videoSize, sizeListToStringList(validSizesForEncoder));
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
            if (isMainCamera()) {
                List<String> focusModes = mParameters.getSupportedFocusModes();
                if (hasTouchFocusSupport(mParameters)) {
                    focusModes.add("touch");
                }
                filterUnsupportedOptions(group, focusMode, focusModes);
            } else {
                // Front camera cannot focus
                removePreference(group, focusMode.getKey());
            }
        }
        if (videoFlashMode != null) {
            filterUnsupportedOptions(group,
                    videoFlashMode, mParameters.getSupportedFlashModes());
        }
        if (mIso != null) {
            filterUnsupportedOptions(group,
                    mIso, mParameters.getSupportedIsoValues());
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
        if (exposure != null) {
            buildExposureCompensation(group, exposure);
        }
        if (brightness != null && mParameters.getMaxBrightness() == 0.0f) {
            removePreference(group, brightness.getKey());
        }
        if (sharpness != null && mParameters.getMaxSharpness() == 0.0f) {
            removePreference(group, sharpness.getKey());
        }
        if (contrast != null && mParameters.getMaxContrast() == 0.0f) {
            removePreference(group, contrast.getKey());
        }
        if (saturation != null && mParameters.getMaxSaturation() == 0.0f) {
            removePreference(group, saturation.getKey());
        }
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

    public static void upgradePreferences(SharedPreferences pref) {
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
        editor.commit();
    }

    public static boolean getVideoQuality(String quality) {
        return VIDEO_QUALITY_YOUTUBE.equals(quality) || VIDEO_QUALITY_HIGH.equals(quality);
    }

    public static CamcorderProfile getCamcorderProfile(boolean highQuality) {
        int profile = CamcorderProfile.QUALITY_LOW;
        if (highQuality) {
            profile = isMainCamera() ? CamcorderProfile.QUALITY_HIGH : CamcorderProfile.QUALITY_FRONT;
        }
        return CamcorderProfile.get(profile);
    }
    
    public static boolean isMainCamera() {
        return CameraSwitch.SWITCH_CAMERA_MAIN.equals(CameraHolder.instance().getCameraNode());
    }
    
    public static int getVidoeDurationInMillis(String quality) {
        if (VIDEO_QUALITY_MMS.equals(quality)) {
            return MMS_VIDEO_DURATION * 1000;
        } else if (VIDEO_QUALITY_YOUTUBE.equals(quality)) {
            return YOUTUBE_VIDEO_DURATION * 1000;
        }
        return DEFAULT_VIDEO_DURATION * 1000;
    }
    
    public static void setCamMode(Parameters params, int mode) {
        if (params.get(HTC_CAM_MODE) != null) {
            Log.d(TAG, "Set cam-mode: " + mode);
            params.set(HTC_CAM_MODE, mode);
        }
    }

    public static boolean isVideoZoomSupported(Parameters params) {
        boolean ret = params.isZoomSupported();
        if (ret) {
            // No zoom at 720P currently. Driver limitation?
            Size size = params.getPreviewSize();
            ret = !(size.width == 1280 && size.height == 720);
        }
        return ret;
    }

    public static void dumpParameters(Parameters params) {
        final String[] paramList = params.flatten().split(";");
        final TreeSet<String> sortedParams = new TreeSet<String>();
        sortedParams.addAll(Arrays.asList(paramList));
        Log.d(TAG, sortedParams.toString());
    }
}
