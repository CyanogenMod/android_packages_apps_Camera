/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.hardware.Camera.Parameters;
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;

public class PhotoController extends PieController
        implements MoreSettingPopup.Listener,
        ListPrefSettingPopup.Listener {
    private static String TAG = "CAM_photocontrol";
    private static float FLOAT_PI_DIVIDED_BY_TWO = (float) Math.PI / 2;
    private final String mSettingOff;

    private PhotoModule mModule;
    private String[] mOtherKeys;
    // First level popup
    private MoreSettingPopup mPopup;
    // Second level popup
    private AbstractSettingPopup mSecondPopup;

    public PhotoController(CameraActivity activity, PhotoModule module, PieRenderer pie) {
        super(activity, pie);
        mModule = module;
        mSettingOff = activity.getString(R.string.setting_off_value);
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
        mSecondPopup = null;
        float sweep = FLOAT_PI_DIVIDED_BY_TWO / 2;
        addItem(CameraSettings.KEY_FLASH_MODE, FLOAT_PI_DIVIDED_BY_TWO - sweep, sweep);
        addItem(CameraSettings.KEY_EXPOSURE, 3 * FLOAT_PI_DIVIDED_BY_TWO - sweep, sweep);
        addItem(CameraSettings.KEY_WHITE_BALANCE, 3 * FLOAT_PI_DIVIDED_BY_TWO + sweep, sweep);
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            PieItem item = makeItem(R.drawable.ic_switch_photo_facing_holo_light);
            item.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO + sweep, sweep);
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference camPref = mPreferenceGroup
                            .findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (camPref != null) {
                        Util.mSwitchCamera = true;
                        int index = camPref.findIndexOfValue(camPref.getValue());
                        CharSequence[] values = camPref.getEntryValues();
                        index = (index + 1) % values.length;
                        int newCameraId = Integer
                                .parseInt((String) values[index]);
                        mListener.onCameraPickerClicked(newCameraId);
                    }
                }
            });
            mRenderer.addItem(item);
        }
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR) != null) {
            PieItem hdr = makeItem(R.drawable.ic_hdr);
            hdr.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO, sweep);
            hdr.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref = mPreferenceGroup
                            .findPreference(CameraSettings.KEY_CAMERA_HDR);
                    if (pref != null) {
                        // toggle hdr value
                        int index = (pref.findIndexOfValue(pref.getValue()) + 1) % 2;
                        pref.setValueIndex(index);
                        onSettingChanged(pref);
                    }
                }
            });
            mRenderer.addItem(hdr);
        }
        addItem(CameraSettings.KEY_NOHANDS_MODE, (float)(1.5 * FLOAT_PI_DIVIDED_BY_TWO) + sweep, sweep);
        mOtherKeys = new String[] {
                CameraSettings.KEY_STORAGE,
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_POWER_SHUTTER,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_FOCUS_TIME,
                CameraSettings.KEY_ISO_MODE,
                CameraSettings.KEY_JPEG,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_PERSISTENT_NOHANDS,
                CameraSettings.KEY_BURST_MODE,
                CameraSettings.KEY_SHUTTER_SPEED};
        PieItem item = makeItem(R.drawable.ic_settings_holo_light);
        item.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO * 3, sweep);
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                if (mPopup == null) {
                    initializePopup();
                }
                mModule.showPopup(mPopup);
            }
        });
        mRenderer.addItem(item);
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

    @Override
    public void reloadPreferences() {
        super.reloadPreferences();
        if (mPopup != null) {
            mPopup.reloadPreference();
        }
    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null && mSecondPopup != null) {
                mModule.dismissPopup(true);
                mPopup.reloadPreference();
        }
        onSettingChanged(pref);
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        super.overrideSettings(keyvalues);
        if (mPopup == null) initializePopup();
        mPopup.overrideSettings(keyvalues);
    }

    protected void initializePopup() {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        MoreSettingPopup popup = (MoreSettingPopup) inflater.inflate(
                R.layout.more_setting_popup, null, false);
        popup.setSettingChangedListener(this);
        popup.initialize(mPreferenceGroup, mOtherKeys);
        if (mActivity.isSecureCamera()) {
            // Prevent location preference from getting changed in secure camera mode
            popup.setPreferenceEnabled(CameraSettings.KEY_RECORD_LOCATION, false);
        }
        mPopup = popup;
    }

    public void popupDismissed(boolean topPopupOnly) {
        // if the 2nd level popup gets dismissed
        if (mSecondPopup != null) {
            mSecondPopup = null;
            if (topPopupOnly) mModule.showPopup(mPopup);
        }
        initializePopup();
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    private void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        // Reset the scene mode if HDR is set to on. Reset HDR if scene mode is
        // set to non-auto.
        if (notSame(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
            setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
        } else if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
        } else if (pref.getKey().equals(CameraSettings.KEY_NOHANDS_MODE)) {
            if (pref.getValue().equals(mActivity.getString(R.string.pref_camera_nohands_voice))) {
                Util.enableSpeechRecognition(true, mModule);
            } else {
                Util.enableSpeechRecognition(false, null);
            }
        }
        super.onSettingChanged(pref);
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        if (mSecondPopup != null) return;

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ListPrefSettingPopup basic = (ListPrefSettingPopup) inflater.inflate(
                R.layout.list_pref_setting_popup, null, false);
        basic.initialize(pref);
        basic.setSettingChangedListener(this);
        mModule.dismissPopup(true);
        mSecondPopup = basic;
        mModule.showPopup(mSecondPopup);
    }

    public void resetNoHandsShutter(boolean force) {
        ListPreference persist = mPreferenceGroup.findPreference(CameraSettings.KEY_PERSISTENT_NOHANDS);
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_NOHANDS_MODE);

        if (persist.getValue().equals(mActivity.getString(R.string.setting_on_value))) {
            Util.enableSpeechRecognition( (pref.getValue().equals(mActivity.getString(R.string.pref_camera_nohands_voice)) && 
                                           !force),
                                          null);
            return;
        }
        pref.setValue(mActivity.getString(R.string.pref_camera_nohands_default));
        Util.enableSpeechRecognition(false, null);
        super.reloadPreferences();
        super.onSettingChanged(pref);
    }

    public void restoreNoHandsShutter() {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_NOHANDS_MODE);
        Util.enableSpeechRecognition(pref.getValue().equals(mActivity.getString(R.string.pref_camera_nohands_voice)), mModule);
    }

}
