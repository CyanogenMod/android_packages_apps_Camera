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
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.TimeIntervalPopup;

public class VideoController extends PieController
        implements MoreSettingPopup.Listener,
        ListPrefSettingPopup.Listener,
        TimeIntervalPopup.Listener {


    private static String TAG = "CAM_videocontrol";
    private static float FLOAT_PI_DIVIDED_BY_TWO = (float) Math.PI / 2;

    private VideoModule mModule;
    private String[] mOtherKeys;
    private AbstractSettingPopup mPopup;

    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private int mPopupStatus;

    public VideoController(CameraActivity activity, VideoModule module, PieRenderer pie) {
        super(activity, pie);
        mModule = module;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
        mPopupStatus = POPUP_NONE;
        float sweep = FLOAT_PI_DIVIDED_BY_TWO / 2;

        addItem(CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE, FLOAT_PI_DIVIDED_BY_TWO - sweep, sweep);
        addItem(CameraSettings.KEY_EXPOSURE, 3 * FLOAT_PI_DIVIDED_BY_TWO - sweep, sweep);
        addItem(CameraSettings.KEY_WHITE_BALANCE, 3 * FLOAT_PI_DIVIDED_BY_TWO + sweep, sweep);
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            PieItem item = makeItem(R.drawable.ic_switch_video_facing_holo_light);
            item.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO + sweep,  sweep);
            item.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (pref != null) {
                        Util.mSwitchCamera = true;
                        int index = pref.findIndexOfValue(pref.getValue());
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        int newCameraId = Integer.parseInt((String) values[index]);
                        mListener.onCameraPickerClicked(newCameraId);
                    }
                }
            });
            mRenderer.addItem(item);
        }
        if (group.findPreference(CameraSettings.KEY_VIDEO_HDR) != null) {
            PieItem hdr = makeItem(R.drawable.ic_hdr);
            hdr.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO, sweep);
            hdr.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref = mPreferenceGroup
                            .findPreference(CameraSettings.KEY_VIDEO_HDR);
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
        mOtherKeys = new String[] {
                CameraSettings.KEY_STORAGE,
                CameraSettings.KEY_VIDEO_EFFECT,
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                CameraSettings.KEY_VIDEO_QUALITY,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_POWER_SHUTTER,
                CameraSettings.KEY_VIDEOCAMERA_COLOR_EFFECT,
                CameraSettings.KEY_VIDEOCAMERA_JPEG};

        PieItem item = makeItem(R.drawable.ic_settings_holo_light);
        item.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO * 3, sweep);
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                if (mPopup == null || mPopupStatus != POPUP_FIRST_LEVEL) {
                    initializePopup();
                    mPopupStatus = POPUP_FIRST_LEVEL;
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
    public void overrideSettings(final String ... keyvalues) {
        super.overrideSettings(keyvalues);
        if (mPopup == null || mPopupStatus != POPUP_FIRST_LEVEL) {
            mPopupStatus = POPUP_FIRST_LEVEL;
            initializePopup();
        }
        ((MoreSettingPopup) mPopup).overrideSettings(keyvalues);
    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null) {
            if (mPopupStatus == POPUP_SECOND_LEVEL) {
                mModule.dismissPopup(true);
            }
        }
        super.onSettingChanged(pref);
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
        initializePopup();
        // if the 2nd level popup gets dismissed
        if (mPopupStatus == POPUP_SECOND_LEVEL) {
            mPopupStatus = POPUP_FIRST_LEVEL;
            if (topPopupOnly) mModule.showPopup(mPopup);
        }
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
        if (pref.getKey().equals(CameraSettings.KEY_VIDEO_HDR)) {
            if (!pref.getValue().equals(mActivity.getString(R.string.setting_on_value))) {
                setPreference(CameraSettings.KEY_VIDEO_HDR, mActivity.getString(R.string.setting_off_value));
            } else {
                setPreference(CameraSettings.KEY_VIDEO_HDR, mActivity.getString(R.string.setting_on_value));
            }
        }
        super.onSettingChanged(pref);
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        if (mPopupStatus != POPUP_FIRST_LEVEL) return;

        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        if (CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL.equals(pref.getKey())) {
            TimeIntervalPopup timeInterval = (TimeIntervalPopup) inflater.inflate(
                    R.layout.time_interval_popup, null, false);
            timeInterval.initialize((IconListPreference) pref);
            timeInterval.setSettingChangedListener(this);
            mModule.dismissPopup(true);
            mPopup = timeInterval;
        } else {
            ListPrefSettingPopup basic = (ListPrefSettingPopup) inflater.inflate(
                    R.layout.list_pref_setting_popup, null, false);
            basic.initialize(pref);
            basic.setSettingChangedListener(this);
            mModule.dismissPopup(true);
            mPopup = basic;
        }
        mModule.showPopup(mPopup);
        mPopupStatus = POPUP_SECOND_LEVEL;
    }

}
