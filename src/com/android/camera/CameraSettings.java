package com.android.camera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.List;

public class CameraSettings {
    private static final int FIRST_REQUEST_CODE = 100;
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_RECORD_LOCATION =
            "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY =
            "pref_camera_videoquality_key";
    public static final String KEY_VIDEO_DURATION =
            "pref_camera_video_duration_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_WHITE_BALANCE =
            "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";

    // TODO: use camera parameters API after it is finished.
    public static final String VALUE_FOCUS_INFINITY = "infinity";
    public static final String VALUE_FOCUS_AUTO = "auto";

    public static final int CURRENT_VERSION = 1;

    // max mms video duration in seconds.
    public static final int MMS_VIDEO_DURATION =
            SystemProperties.getInt("ro.media.enc.lprof.duration", 60);

    public static final boolean DEFAULT_VIDEO_QUALITY_VALUE = true;

    // MMS video length
    public static final int DEFAULT_VIDEO_DURATION_VALUE = -1;

    private static final String TAG = "CameraSettings";

    private final Context mContext;
    private final Parameters mParameters;
    private final PreferenceManager mManager;

    public CameraSettings(Activity activity, Parameters parameters) {
        mContext = activity;
        mParameters = parameters;
        mManager = new PreferenceManager(activity, FIRST_REQUEST_CODE);
    }

    public PreferenceScreen getPreferenceScreen(int preferenceRes) {
        PreferenceScreen screen = mManager.createPreferenceScreen(mContext);
        mManager.inflateFromResource(mContext, preferenceRes, screen);
        initPreference(screen);
        return screen;
    }

    private void initPreference(PreferenceScreen screen) {
        ListPreference videoDuration =
                (ListPreference) screen.findPreference(KEY_VIDEO_DURATION);
        ListPreference pictureSize =
                (ListPreference) screen.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =
                (ListPreference) screen.findPreference(KEY_WHITE_BALANCE);
        ListPreference colorEffect =
                (ListPreference) screen.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode =
                (ListPreference) screen.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode =
                (ListPreference) screen.findPreference(KEY_FLASH_MODE);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here
        if (videoDuration != null) {
            // Modify video duration settings.
            // The first entry is for MMS video duration, and we need to fill
            // in the device-dependent value (in seconds).
            CharSequence[] entries = videoDuration.getEntries();
            entries[0] = String.format(
                    entries[0].toString(), MMS_VIDEO_DURATION);
        }

        // Filter out unsupported settings / options
        if (pictureSize != null) {
            filterUnsupportedOptions(screen, pictureSize, sizeListToStringList(
                    mParameters.getSupportedPictureSizes()));
        }
        if (whiteBalance != null) {
            filterUnsupportedOptions(screen,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }
        if (colorEffect != null) {
            filterUnsupportedOptions(screen,
                    colorEffect, mParameters.getSupportedColorEffects());
        }
        if (sceneMode != null) {
            filterUnsupportedOptions(screen,
                    sceneMode, mParameters.getSupportedSceneModes());
        }
        if (flashMode != null) {
            filterUnsupportedOptions(screen,
                    flashMode, mParameters.getSupportedFlashModes());
        }
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

    private void filterUnsupportedOptions(PreferenceScreen screen,
            ListPreference pref, List<String> supported) {

        // Remove the preference if the parameter is not supported.
        if (supported == null) {
            removePreference(screen, pref);
            return;
        }

        // Prepare setting entries and entry values.
        CharSequence[] allEntries = pref.getEntries();
        CharSequence[] allEntryValues = pref.getEntryValues();
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
        for (int i = 0, len = allEntryValues.length; i < len; i++) {
            if (supported.indexOf(allEntryValues[i].toString()) != NOT_FOUND) {
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
