package com.android.camera;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.SystemProperties;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

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

    public static final int CURRENT_VERSION = 2;

    // max mms video duration in seconds.
    public static final int MMS_VIDEO_DURATION =
            SystemProperties.getInt("ro.media.enc.lprof.duration", 60);

    public static final boolean DEFAULT_VIDEO_QUALITY_VALUE = true;

    // MMS video length
    public static final int DEFAULT_VIDEO_DURATION_VALUE = -1;

    @SuppressWarnings("unused")
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
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(context).edit();
                editor.putString(KEY_PICTURE_SIZE, candidate);
                editor.commit();
                return;
            }
        }
    }

    public static void removePreferenceFromScreen(
            PreferenceScreen screen, String key) {
        Preference pref = screen.findPreference(key);
        if (pref == null) {
            Log.i(TAG, "No preference found based the key : " + key);
            throw new IllegalArgumentException();
        } else {
            removePreference(screen, pref);
        }
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
        ListPreference focusMode =
                (ListPreference) screen.findPreference(KEY_FOCUS_MODE);

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
        if (focusMode != null) {
            filterUnsupportedOptions(screen,
                    focusMode, mParameters.getSupportedFocusModes());
        }
    }

    private static boolean removePreference(PreferenceGroup group,
            Preference remove) {
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

        CharSequence[] allEntries = pref.getEntries();

        // Remove the preference if the parameter is not supported or there is
        // only one options for the settings.
        if (supported == null || supported.size() <= 1) {
            removePreference(screen, pref);
            return;
        }

        CharSequence[] allEntryValues = pref.getEntryValues();
        Drawable[] allIcons = (pref instanceof IconListPreference)
                ? ((IconListPreference) pref).getIcons()
                : null;
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
        ArrayList<Drawable> icons =
                allIcons == null ? null : new ArrayList<Drawable>();
        for (int i = 0, len = allEntryValues.length; i < len; i++) {
            if (supported.indexOf(allEntryValues[i].toString()) != NOT_FOUND) {
                entries.add(allEntries[i]);
                entryValues.add(allEntryValues[i]);
                if (allIcons != null) icons.add(allIcons[i]);
            }
        }

        // Set entries and entry values to list preference.
        int size = entries.size();
        pref.setEntries(entries.toArray(new CharSequence[size]));
        pref.setEntryValues(entryValues.toArray(new CharSequence[size]));
        if (allIcons != null) {
            ((IconListPreference) pref)
                    .setIcons(icons.toArray(new Drawable[size]));
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
            // For old version, change 1 to 10 for video duration preference.
            if (pref.getString(KEY_VIDEO_DURATION, "1").equals("1")) {
                editor.putString(KEY_VIDEO_DURATION, "10");
            }
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
        editor.putInt(KEY_VERSION, CURRENT_VERSION);
        editor.commit();
    }
}
