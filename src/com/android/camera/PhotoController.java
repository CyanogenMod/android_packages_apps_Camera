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

import android.view.View;
import android.view.View.OnClickListener;

import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieRenderer;

public class PhotoController extends PieController {

    private static String TAG = "CAM_photocontrol";
    private static float FLOAT_PI_DIVIDED_BY_TWO = (float) Math.PI / 2;

    private String[] mOtherKeys;

    public PhotoController(CameraActivity activity, PieRenderer pie) {
        super(activity, pie);
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        float sweep = FLOAT_PI_DIVIDED_BY_TWO / 2;
        addItem(CameraSettings.KEY_FLASH_MODE, FLOAT_PI_DIVIDED_BY_TWO - sweep, sweep);
        addItem(CameraSettings.KEY_EXPOSURE, FLOAT_PI_DIVIDED_BY_TWO + sweep, sweep);
        PieItem item = makeItem(R.drawable.ic_switch_photo_facing_holo_light);
        item.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO,  sweep);
        item.getView().setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // Find the index of next camera.
                ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
                int index = pref.findIndexOfValue(pref.getValue());
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                int newCameraId = Integer.parseInt((String) values[index]);
                mListener.onCameraPickerClicked(newCameraId);
            }
        });
        mRenderer.addItem(item);
        mOtherKeys = new String[] {
//                CameraSettings.KEY_WHITE_BALANCE,
//                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_RECORD_LOCATION,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_FOCUS_MODE};
        item = makeItem(R.drawable.ic_settings_holo_light);
        item.setFixedSlice(FLOAT_PI_DIVIDED_BY_TWO * 3, sweep);
        item.getView().setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
        mRenderer.addItem(item);
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

}
