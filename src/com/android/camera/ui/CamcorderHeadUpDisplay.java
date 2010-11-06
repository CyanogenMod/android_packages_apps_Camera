/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;

import com.android.camera.CameraSettings;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

public class CamcorderHeadUpDisplay extends HeadUpDisplay {

    private static final String TAG = "CamcorderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private int mInitialOrientation;

    public CamcorderHeadUpDisplay(Context context) {
        super(context);
    }

    public void initialize(Context context, PreferenceGroup group,
            int initialOrientation) {
        mInitialOrientation = initialOrientation;
        super.initialize(context, group);
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        ListPreference[] prefs = getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_COLOR_EFFECT);

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);
        addIndicator(context, group, CameraSettings.KEY_VIDEO_QUALITY);
        addIndicator(context, group, CameraSettings.KEY_CAMERA_ID);

        mIndicatorBar.setOrientation(mInitialOrientation);
    }
}
