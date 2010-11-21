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
import android.hardware.Camera.Parameters;

import com.android.camera.CameraSettings;
import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

public class CameraHeadUpDisplay extends HeadUpDisplay {

    protected static final String TAG = "CameraHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private GpsIndicator mGpsIndicator;

    public CameraHeadUpDisplay(Context context, Parameters params) {
        super(context, params, params.isZoomSupported(), false); // false does nothing here
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        ListPreference prefs[] = getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_COLOR_EFFECT,
                CameraSettings.KEY_ISO,
                CameraSettings.KEY_LENSSHADING,
                CameraSettings.KEY_AUTOEXPOSURE,
                CameraSettings.KEY_ANTIBANDING,
                CameraSettings.KEY_STABLESHOT,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY);

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        mSettingsIndicator = new SettingsIndicator(context, mParameters, mSharedPrefs);
        if (mSettingsIndicator.isAvailable()) {
            mIndicatorBar.addComponent(mSettingsIndicator);
        }

        mGpsIndicator = new GpsIndicator(
                context, group, (IconListPreference)
                group.findPreference(CameraSettings.KEY_RECORD_LOCATION));
        mIndicatorBar.addComponent(mGpsIndicator);

        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_FLASH_MODE);
        
        if (mZoomSupported) {
            mZoomIndicator = new ZoomIndicator(context);
            mIndicatorBar.addComponent(mZoomIndicator);
        }

    }

    public void setGpsHasSignal(final boolean hasSignal) {
        GLRootView root = getGLRootView();
        if (root != null) {
            root.queueEvent(new Runnable() {
                public void run() {
                    mGpsIndicator.setHasSignal(hasSignal);
                }
            });
        } else {
            mGpsIndicator.setHasSignal(hasSignal);
        }
    }

}
