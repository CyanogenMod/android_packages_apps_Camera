/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
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

import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import java.util.List;

/**
 * Base class for camera implementations.
 */
public abstract class BaseCamera extends NoSearchActivity implements View.OnClickListener, SensorEventListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback, Switcher.OnSwitchListener {

    private static final String TAG = "BaseCamera";

    protected android.hardware.Camera mCameraDevice;
    protected MediaRecorder mMediaRecorder;

    protected SharedPreferences mPreferences;
    protected HeadUpDisplay mHeadUpDisplay;
    protected Parameters mParameters;
    protected Menu mOptionsMenu;
    protected FocusRectangle mFocusRectangle;
    protected String mFocusMode;

    protected static final int ZOOM_STOPPED = 0;
    protected static final int ZOOM_START = 1;
    protected static final int ZOOM_STOPPING = 2;

    protected int mZoomState = ZOOM_STOPPED;
    protected int mTargetZoomValue;
    
    protected boolean mSmoothZoomSupported;
    protected int mZoomValue;  // The current zoom value.
    protected int mZoomMax;
    protected GestureDetector mZoomGestureDetector;
    protected GestureDetector mFocusGestureDetector;
    protected final ZoomListener mZoomListener = new ZoomListener();

    protected boolean mPausing = false;
    protected boolean mPreviewing = false; // True if preview is started.
    protected boolean mFocusing = false;

    private long lastSensorUpdate = -1;

    private float[] lastSensorValues = new float[3];

    protected boolean deviceStable = false;

    protected abstract void onZoomValueChanged(int index);

    protected abstract int getCameraMode();

    private StabilityListener mStabilityListener;

    private StabilityChangeListener mStabilityChangeListener;

    private boolean mStable = false;

    public interface StabilityListener {
        public void onStable();
    }

    public interface StabilityChangeListener {
        public void onStabilityChanged(boolean stable);
    }

    protected void setStabilityListener(StabilityListener listener) {
        mStabilityListener = listener;
    }

    protected void setStabilityChangeListener(StabilityChangeListener listener) {
        mStabilityChangeListener = listener;
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void onSensorChanged(SensorEvent event) {

        long curTime = System.currentTimeMillis();
        if (lastSensorUpdate == -1 || (curTime - lastSensorUpdate) > 500) {
            lastSensorUpdate = curTime;
            if (lastSensorUpdate == -1) {
                lastSensorValues[0] = 0.0f;
                lastSensorValues[1] = 0.0f;
                lastSensorValues[2] = 0.0f;
            } else {

                if (Math.abs(event.values[0] - lastSensorValues[0]) < 0.25f
                        && Math.abs(event.values[1] - lastSensorValues[1]) < 0.25f
                        && Math.abs(event.values[2] - lastSensorValues[2]) < 0.25f) {
                    if (mStabilityListener != null) {
                        mStabilityListener.onStable();
                    }
                    if (!mStable) {
                        mStable = true;
                        Log.d(TAG, "Stability changed: " + mStable);
                        if (mStabilityChangeListener != null) {
                            mStabilityChangeListener.onStabilityChanged(mStable);
                        }
                    }
                } else {
                    if (mStable) {
                        mStable = false;
                        Log.d(TAG, "Stability changed: " + mStable);
                        if (mStabilityChangeListener != null) {
                            mStabilityChangeListener.onStabilityChanged(mStable);
                        }
                    }
                }


                lastSensorValues[0] = event.values[0];
                lastSensorValues[1] = event.values[1];
                lastSensorValues[2] = event.values[2];
            }
        }
    }

    protected void onResume() {
        super.onResume();

        SensorManager mgr = (SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    }

    protected void onPause() {
        super.onPause();

        SensorManager mgr = (SensorManager)getSystemService(SENSOR_SERVICE);
        mgr.unregisterListener(this);
    }
    protected void setCommonParameters() {

        // Set color effect parameter.
        String colorEffect = mPreferences.getString(
                CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        // Set sharpness parameter.
        if (mParameters.getMaxSharpness() > 0) {
            String sharpness = mPreferences.getString(
                    CameraSettings.KEY_SHARPNESS,
                    String.valueOf(mParameters.getDefaultSharpness()));
            mParameters.setSharpness(Integer.valueOf(sharpness));
        }

        // Set contrast parameter.
        if (mParameters.getMaxContrast() > 0) {
            String contrast = mPreferences.getString(
                    CameraSettings.KEY_CONTRAST,
                    String.valueOf(mParameters.getDefaultContrast()));
            mParameters.setContrast(Integer.valueOf(contrast));
        }

        // Set saturation parameter.
        if (mParameters.getMaxSaturation() > 0) {
            String saturation = mPreferences.getString(
                    CameraSettings.KEY_SATURATION,
                    String.valueOf(mParameters.getDefaultSaturation()));
            mParameters.setSaturation(Integer.valueOf(saturation));
        }

        // Set exposure compensation
        String exposure = mPreferences.getString(
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        try {
            float value = Float.parseFloat(exposure);
            int max = mParameters.getMaxExposureCompensation();
            int min = mParameters.getMinExposureCompensation();
            if (value >= min && value <= max) {
                mParameters.set("exposure-compensation", exposure);
            } else {
                Log.w(TAG, "invalid exposure range: " + exposure);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "invalid exposure: " + exposure);
        }
    }

    protected void setWhiteBalance() {
        String whiteBalance = mPreferences.getString(
                CameraSettings.KEY_WHITE_BALANCE,
                getString(R.string.pref_camera_whitebalance_default));
        if (isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }
    }

    protected static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    protected void initializeZoom() {
        if (!mParameters.isZoomSupported()) return;

        // Maximum zoom value may change after preview size is set. Get the
        // latest parameters here.
        mZoomMax = mParameters.getMaxZoom();
        mSmoothZoomSupported = mParameters.isSmoothZoomSupported();
        mZoomGestureDetector = new GestureDetector(this, new ZoomGestureListener());

        mCameraDevice.setZoomChangeListener(mZoomListener);
    }

    protected void initializeTouchFocus() {
        if (!CameraSettings.hasTouchFocusSupport(mParameters)) return;
        
        Log.d(TAG, "initializeTouchFocus");
        enableTouchAEC(false); 
        mFocusGestureDetector = new GestureDetector(this, new FocusGestureListener());
    }

    protected float[] getZoomRatios() {
        List<Integer> zoomRatios = mParameters.getZoomRatios();
        if (mParameters.get("taking-picture-zoom") != null) {
            // HTC camera zoom
            float result[] = new float[mZoomMax + 1];
            for (int i = 0, n = result.length; i < n; ++i) {
                result[i] = 1 + i * 0.2f;
            }
            return result;
        } else if (zoomRatios != null) {
            float result[] = new float[zoomRatios.size()];
            for (int i = 0, n = result.length; i < n; ++i) {
                result[i] = (float) zoomRatios.get(i) / 100f;
            }
            return result;

        }

        float[] result = new float[1];
        result[0] = 0.0f;
        return result;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        boolean ret = true;
        if (!super.dispatchTouchEvent(m)) {
            if (mZoomGestureDetector != null) {
                ret = mZoomGestureDetector.onTouchEvent(m);
            }
            if (mFocusGestureDetector != null) {
                ret = mFocusGestureDetector.onTouchEvent(m);
            }
        }
        return ret;
    }

    protected void resetFocusIndicator() {
        if (mFocusRectangle == null)
            return;

        mFocusRectangle.setVisibility(View.VISIBLE);
        PreviewFrameLayout frameLayout = (PreviewFrameLayout) findViewById(R.id.frame_layout);
        int x = frameLayout.getActualWidth() / 2;
        int y = frameLayout.getActualHeight() / 2;
        mFocusRectangle.setPosition(x, y);
        updateTouchFocus(x, y);
    }

    private class FocusGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mPausing || !mPreviewing || mHeadUpDisplay == null || mFocusing
                    || (getCameraMode() == CameraSettings.CAMERA_MODE && !"touch".equals(mFocusMode))) {
                return false;
            }

            mFocusing = true;
            int x = (int) e.getX();
            int y = (int) e.getY();

            enableTouchAEC(true);
            updateTouchFocus(x, y);
            mFocusRectangle.showStart();

            if (mMediaRecorder == null) {
                mCameraDevice.autoFocus(getAutoFocusCallback());

            } else {
                
                // FIXME: No autofocus callback via MediaRecorder yet.
                mMediaRecorder.autoFocusCamera();
                mFocusRectangle.showSuccess();
                mFocusing = false;
            }
          
            return true;
        }
    }

    private void updateTouchFocus(int x, int y) {
        Log.d(TAG, "updateTouchFocus x=" + x + " y=" + y);
        mFocusRectangle.setVisibility(View.VISIBLE);
        mFocusRectangle.setPosition(x, y);
        mParameters.set("touch-focus", x + "," + y);
        setCameraParameters();
    }

    private void enableTouchAEC(boolean enable) {
        Log.d(TAG, "enableTouchAEC: " + enable);
        mParameters.set("touch-aec", enable ? "on" : "off");
        setCameraParameters();
    }
    
    private void setCameraParameters() {
        if (mMediaRecorder == null) {
            mCameraDevice.setParameters(mParameters);
        } else {
            mMediaRecorder.setCameraParameters(mParameters.flatten());
        }
    }

    protected android.hardware.Camera.AutoFocusCallback getAutoFocusCallback() {
        return new android.hardware.Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (mFocusing) {
                    mFocusRectangle.showSuccess();
                    mFocusing = false;
                }
            }
        };
    }
    
    private class ZoomGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Perform zoom only when preview is started and snapshot is not in
            // progress.
            if (mFocusing || mPausing || !mPreviewing || mHeadUpDisplay == null) {
                return false;
            }

            if (mZoomValue < mZoomMax) {
                // Zoom in to the maximum.
                mZoomValue = mZoomMax;
            } else {
                mZoomValue = 0;
            }
            mHeadUpDisplay.setZoomIndex(mZoomValue);
            return true;
        }
    }
    
    private final class ZoomListener implements android.hardware.Camera.OnZoomChangeListener {
        public void onZoomChange(int value, boolean stopped, android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped=" + stopped);
            mZoomValue = value;
            // Keep mParameters up to date. We do not getParameter again in
            // takePicture. If we do not do this, wrong zoom value will be set.
            mParameters.setZoom(value);
            // We only care if the zoom is stopped. mZooming is set to true when
            // we start smooth zoom.
            if (stopped && mZoomState != ZOOM_STOPPED) {
                if (value != mTargetZoomValue) {
                    mCameraDevice.startSmoothZoom(mTargetZoomValue);
                    mZoomState = ZOOM_START;
                } else {
                    mZoomState = ZOOM_STOPPED;
                }
            }
        }
    }

}

