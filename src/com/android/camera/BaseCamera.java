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

import android.graphics.Point;
import android.graphics.Rect;
import android.content.SharedPreferences;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.List;

public abstract class BaseCamera extends NoSearchActivity
        implements PreviewFrameLayout.OnSizeChangedListener {

    private static final String LOG_TAG = "BaseCamera";

    protected ComboPreferences mPreferences;

    protected android.hardware.Camera mCameraDevice;

    protected Parameters mParameters;

    protected FocusRectangle mFocusRectangle;
    protected String mFocusMode;
    protected String mCaptureMode;
    protected GestureDetector mFocusGestureDetector;

    private PreviewFrameLayout mPreviewFrameLayout;
    private Rect mPreviewRect;

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

        mPreviewFrameLayout = (PreviewFrameLayout) findViewById(R.id.frame_layout);
        mPreviewFrameLayout.setOnSizeChangedListener(this);
        mPreviewRect = null;
    }

    protected void setCommonParameters() {
        // Set capture mode.
        mCaptureMode = mPreferences.getString(
                CameraSettings.KEY_CAPTURE_MODE,
                getString(R.string.pref_camera_capturemode_entry_default));

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

        int x = mPreviewFrameLayout.getActualWidth() / 2;
        int y = mPreviewFrameLayout.getActualHeight() / 2;
        mFocusRectangle.setPosition(x, y);

        if (mFocusMode.equals(CameraSettings.FOCUS_MODE_TOUCH)) {
            Size previewSize = mParameters.getPreviewSize();
            updateTouchFocus(previewSize.width / 2, previewSize.height / 2);
            mFocusRectangle.setVisibility(View.VISIBLE);
        }
    }

    protected boolean powerShutter(SharedPreferences prefs) {
        if (prefs.getBoolean("power_shutter_enabled", false)){
            getWindow().addFlags(WindowManager.LayoutParams.PREVENT_POWER_KEY);
            return true;
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.PREVENT_POWER_KEY);
            return false;
        }
    }

    private class FocusGestureListener extends GestureDetector.SimpleOnGestureListener {
        private void transformToPreviewCoords(Point point) {
            Size previewSize = mParameters.getPreviewSize();
            float x = point.x;
            float y = point.y;
 
            point.x = (int) ((previewSize.width * x) / mPreviewRect.width());
            point.y = (int) ((previewSize.height * y) / mPreviewRect.height());
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            if (mPausing || !mPreviewing || mHeadUpDisplay == null || mPreviewRect == null
                    || mFocusState != FOCUS_NOT_STARTED || mHeadUpDisplay.isActive()
                    || !CameraSettings.FOCUS_MODE_TOUCH.equals(mFocusMode)) {
                return false;
            }

            Point touch = new Point((int) e.getX(), (int) e.getY());
            if (!mPreviewRect.contains(touch.x, touch.y)) {
                return true;
            }

            /*
             * Move point so the coordinate system origin is at the upper
             * left corner of the preview layout
             */
            touch.offset(-mPreviewRect.left, -mPreviewRect.top);

            mFocusRectangle.setPosition(touch.x, touch.y);
            mFocusRectangle.setVisibility(View.VISIBLE);
            mFocusRectangle.showStart();

            /*
             * Scale coordinate system so the point is given in preview coordinates
             */
            transformToPreviewCoords(touch);

            Log.d(LOG_TAG, "Got preview touch event at " + e.getX() + "," + e.getY() +
                    ", transformed to " + touch);

            mFocusState = FOCUSING;
            enableTouchAEC(true);
            updateTouchFocus(touch.x, touch.y);

            mCameraDevice.autoFocus(new android.hardware.Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, android.hardware.Camera camera) {
                    if (mFocusState == FOCUSING) {
                        if (success) mFocusRectangle.showSuccess();
                        else mFocusRectangle.showFail();
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
        } else if (mParameters.get("touch-af-aec-values") != null) {
            mParameters.set("touch-af-aec", "touch-off");
        }

        if (CameraSettings.getTouchFocusParameterName() != null) {
            mParameters.set(CameraSettings.getTouchFocusParameterName(), "");
        }
    }

    /* x and y are in preview coordinates */
    private void updateTouchFocus(int x, int y) {
        Log.d(LOG_TAG, "updateTouchFocus x=" + x + " y=" + y);

        int width = mFocusRectangle.getWidth();
        Size previewSize = mParameters.getPreviewSize();

        if (mPreviewRect != null) {
            /* scale rect width according to preview scale */
            float widthf = width;
            width = (int) ((widthf * previewSize.width) / mPreviewRect.width());
        }

        Rect focusRect = new Rect(x - width / 2, y - width / 2, x + width / 2, y + width / 2);

        /* ensure the rect is fully within the preview */
        int offsetX = 0, offsetY = 0;
        if (focusRect.left < 0) {
            offsetX = -focusRect.left;
        } else if (focusRect.right > previewSize.width) {
            offsetX = previewSize.width - focusRect.right;
        }
        if (focusRect.top < 0) {
            offsetY = -focusRect.top;
        } else if (focusRect.bottom > previewSize.height) {
            offsetY = previewSize.height - focusRect.bottom;
        }
        focusRect.offset(offsetX, offsetY);

        Log.d(LOG_TAG, "determined focus rect as " + focusRect);

	final String paramName = CameraSettings.getTouchFocusParameterName();

        if (CameraSettings.getTouchFocusNeedsRect()) {
            /*
	     * Arguments to configure a region are:
             *      regionId,x-for-ul-corner,y-for-ul-corner,width,height
             */
            mParameters.set(paramName, "1," +
                    focusRect.left + "," + focusRect.top + "," +
                    focusRect.width() + "," + focusRect.height());
        } else if (mParameters.get("touch-af-aec-values") != null) {
	    /* special case: Qualcomm uses separate items for focus
             * and exposure, and both need to be set... */
            mParameters.set("touch-index-af", focusRect.centerX() + "," + focusRect.centerY());
            mParameters.set("touch-index-aec", focusRect.centerX() + "," + focusRect.centerY());
        } else {
	    /* use center point */
            mParameters.set(paramName, focusRect.centerX() + "," + focusRect.centerY());
        }

        mCameraDevice.setParameters(mParameters);
    }

    private void enableTouchAEC(boolean enable) {
        Log.d(LOG_TAG, "enableTouchAEC: " + enable);
        if (mParameters.get("touch-af-aec-values") != null) {
            mParameters.set("touch-af-aec", enable ? "touch-on" : "touch-off");
        } else {
            mParameters.set("touch-aec", enable ? "on" : "off");
        }
        mCameraDevice.setParameters(mParameters);
    }

    public void onSizeChanged(Rect newRect) {
        mPreviewRect = new Rect(newRect);
    }

    protected static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }
}


