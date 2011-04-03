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

import com.android.camera.CameraSettings;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

import android.content.Context;
import android.hardware.Camera.Parameters;
import android.util.Log;

public class CamcorderHeadUpDisplay extends HeadUpDisplay {

    private static final String TAG = "CamcorderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private SettingsIndicator mSettingsIndicator;
    private int mInitialOrientation;
    private BasicIndicator mVideoQualitySettings;
    private float[] mInitialZoomRatios;
    private ZoomIndicator mZoomIndicator;
    private Context mContext;

    public CamcorderHeadUpDisplay(Context context) {
        super(context);
        mContext = context;
    }

    public void initialize(Context context, PreferenceGroup group,
            float[] initialZoomRatios, int initialOrientation, Parameters params) {
        mInitialOrientation = initialOrientation;
        mInitialZoomRatios = initialZoomRatios;
        super.initialize(context, group, params);
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group, Parameters params) {
        super.initializeIndicatorBar(context, group, params);

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

        mSettingsIndicator = new SettingsIndicator(context, params);
        if (mSettingsIndicator.isAvailable()) {
            mIndicatorBar.addComponent(mSettingsIndicator);
        }

        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE);

        mVideoQualitySettings = addIndicator(context, group, CameraSettings.KEY_VIDEO_QUALITY);

        if (mInitialZoomRatios != null) {
            mZoomIndicator = new ZoomIndicator(mContext);
            mZoomIndicator.setZoomRatios(mInitialZoomRatios);
            mIndicatorBar.addComponent(mZoomIndicator);
        } else {
            mZoomIndicator = null;
        }

        addIndicator(context, group, CameraSettings.KEY_CAMERA_ID);

        mIndicatorBar.setOrientation(mInitialOrientation);
    }

    public void setZoomListener(ZoomControllerListener listener) {
        // The rendering thread won't access listener variable, so we don't
        // need to do concurrency protection here
        if (mZoomIndicator != null) {
            mZoomIndicator.setZoomListener(listener);
        }
    }

    public void setZoomIndex(int index) {
        if (mZoomIndicator != null) {
            GLRootView root = getGLRootView();
            if (root != null) {
                synchronized (root) {
                    mZoomIndicator.setZoomIndex(index);
                }
            } else {
                mZoomIndicator.setZoomIndex(index);
            }
        }
    }

    /**
     * Sets the zoom rations the camera driver provides. This methods must be
     * called before <code>setZoomListener()</code> and
     * <code>setZoomIndex()</code>
     */
    public void setZoomRatios(float[] zoomRatios) {
        GLRootView root = getGLRootView();
        if (root != null) {
            synchronized(root) {
                setZoomRatiosLocked(zoomRatios);
            }
        } else {
            setZoomRatiosLocked(zoomRatios);
        }
    }

    private void setZoomRatiosLocked(float[] zoomRatios) {
        mZoomIndicator.setZoomRatios(zoomRatios);
    }

    public void setVideoQualityControlsEnabled(boolean enabled) {
        if (mVideoQualitySettings != null) {
            mVideoQualitySettings.setEnabled(enabled);
        }
    }
}
