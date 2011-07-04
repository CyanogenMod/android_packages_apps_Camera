/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2010 The CyanogenMod Project
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

import com.android.camera.ui.HeadUpDisplay;

import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

public abstract class BaseCamera extends NoSearchActivity {

    private static final String LOG_TAG = "BaseCamera";

    protected ComboPreferences mPreferences;

    protected android.hardware.Camera mCameraDevice;

    protected Parameters mParameters;

    protected FocusRectangle mFocusRectangle;
    protected String mFocusMode;

    protected GestureDetector mFocusGestureDetector;

    protected boolean mPreviewing;
    protected boolean mPausing;

    protected static final int FOCUS_NOT_STARTED = 0;
    protected static final int FOCUSING = 1;
    protected static final int FOCUSING_SNAP_ON_FINISH = 2;
    protected static final int FOCUS_SUCCESS = 3;
    protected static final int FOCUS_FAIL = 4;
    protected int mFocusState = FOCUS_NOT_STARTED;

    protected HeadUpDisplay mHeadUpDisplay;

    protected void initializeTouchFocus() {
        Log.d(LOG_TAG, "initializeTouchFocus");
        enableTouchAEC(false);
        mFocusGestureDetector = new GestureDetector(this, new FocusGestureListener());
    }

    protected void setCommonParameters() {
        // Set color effect parameter.
        String colorEffect = mPreferences.getString(CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        // Set sharpness parameter.
        if (mParameters.getMaxSharpness() > 0) {
            String sharpness = mPreferences.getString(CameraSettings.KEY_SHARPNESS,
                    String.valueOf(mParameters.getDefaultSharpness()));
            mParameters.setSharpness(Integer.valueOf(sharpness));
        }

        // Set contrast parameter.
        if (mParameters.getMaxContrast() > 0) {
            String contrast = mPreferences.getString(CameraSettings.KEY_CONTRAST,
                    String.valueOf(mParameters.getDefaultContrast()));
            mParameters.setContrast(Integer.valueOf(contrast));
        }

        // Set saturation parameter.
        if (mParameters.getMaxSaturation() > 0) {
            String saturation = mPreferences.getString(CameraSettings.KEY_SATURATION,
                    String.valueOf(mParameters.getDefaultSaturation()));
            mParameters.setSaturation(Integer.valueOf(saturation));
        }
    }

    protected void setWhiteBalance() {
        String whiteBalance = mPreferences.getString(CameraSettings.KEY_WHITE_BALANCE,
                getString(R.string.pref_camera_whitebalance_default));
        if (isSupported(whiteBalance, mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }
    }

    protected void resetFocusIndicator() {
        if (mFocusRectangle == null)
            return;

        mFocusRectangle.setVisibility(View.VISIBLE);
        PreviewFrameLayout frameLayout = (PreviewFrameLayout) findViewById(R.id.frame_layout);
        int x = frameLayout.getActualWidth() / 2;
        int y = frameLayout.getActualHeight() / 2;
        updateTouchFocus(x, y);
    }

    private class FocusGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            if (mPausing || !mPreviewing || mHeadUpDisplay == null || mFocusState != FOCUS_NOT_STARTED
                    || !CameraSettings.FOCUS_MODE_TOUCH.equals(mFocusMode) || mHeadUpDisplay.isActive()) {
                return false;
            }

            mFocusState = FOCUSING;
            int x = (int) e.getX();
            int y = (int) e.getY();

            enableTouchAEC(true);
            updateTouchFocus(x, y);
            mFocusRectangle.showStart();

            mCameraDevice.autoFocus(new android.hardware.Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, android.hardware.Camera camera) {
                    if (mFocusState == FOCUSING) {
                        mFocusRectangle.showSuccess();
                        mFocusState = FOCUS_NOT_STARTED;
                    }
                }
            });
            return true;
        }
    }

    protected void clearTouchFocusAEC() {
        if (mParameters.get("touch-aec") != null) {
            mParameters.set("touch-aec", "off");
            mParameters.set("touch-focus", "");
        }
    }

    private void updateTouchFocus(int x, int y) {
        Log.d(LOG_TAG, "updateTouchFocus x=" + x + " y=" + y);
        mFocusRectangle.setVisibility(View.VISIBLE);
        mFocusRectangle.setPosition(x, y);
        mParameters.set("touch-focus", x + "," + y);
        mCameraDevice.setParameters(mParameters);
    }

    private void enableTouchAEC(boolean enable) {
        Log.d(LOG_TAG, "enableTouchAEC: " + enable);
        mParameters.set("touch-aec", enable ? "on" : "off");
        mCameraDevice.setParameters(mParameters);
    }

    protected static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }
}


