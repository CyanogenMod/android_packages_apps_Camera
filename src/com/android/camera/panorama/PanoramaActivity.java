/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.camera.panorama;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.camera.CameraDisabledException;
import com.android.camera.CameraHardwareException;
import com.android.camera.CameraHolder;
import com.android.camera.R;
import com.android.camera.ShutterButton;
import com.android.camera.Util;

import java.util.List;

public class PanoramaActivity extends Activity {
    public static final int DEFAULT_SWEEP_ANGLE = 60;
    public static final int DEFAULT_BLEND_MODE = Mosaic.BLENDTYPE_HORIZONTAL;
    public static final int DEFAULT_CAPTURE_PIXELS = 960 * 720;

    private static final String TAG = "PanoramaActivity";
    private static final float NS2S = 1.0f / 1000000000.0f;  // TODO: commit for this constant.

    private Preview mPreview;
    private ImageView mReview;
    private CaptureView mCaptureView;
    private ShutterButton mShutterButton;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private android.hardware.Camera mCameraDevice;
    private SensorManager mSensorManager;
    private Sensor mSensor;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        createContentView();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (mSensor == null) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        }
    }

    private void setupCamera() {
        openCamera();
        Parameters parameters = mCameraDevice.getParameters();
        setupCaptureParams(parameters);
        configureCamera(parameters);
    }

    private void openCamera() {
        try {
            mCameraDevice = Util.openCamera(this, CameraHolder.instance().getBackCameraId());
        } catch (CameraHardwareException e) {
            Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
            return;
        } catch (CameraDisabledException e) {
            Util.showErrorAndFinish(this, R.string.camera_disabled);
            return;
        }
    }

    private boolean findBestPreviewSize(List<Size> supportedSizes, boolean need4To3,
            boolean needSmaller) {
        int pixelsDiff = DEFAULT_CAPTURE_PIXELS;
        boolean hasFound = false;
        for (Size size: supportedSizes) {
            int h = size.height;
            int w = size.width;
            // we only want 4:3 format.
            int d = DEFAULT_CAPTURE_PIXELS - h * w;
            if (needSmaller && d < 0) {  // no bigger preview than 960x720.
                continue;
            }
            if (need4To3 && (h * 4 != w * 3)) {
                continue;
            }
            d = Math.abs(d);
            if (d < pixelsDiff) {
                mPreviewWidth = w;
                mPreviewHeight = h;
                pixelsDiff = d;
                hasFound = true;
            }
        }
        return hasFound;
    }

    private void setupCaptureParams(Parameters parameters) {
        List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
        if (!findBestPreviewSize(supportedSizes, true, true)) {
            Log.w(TAG, "No 4:3 ratio preview size supported.");
            if (!findBestPreviewSize(supportedSizes, false, true)) {
                Log.w(TAG, "Can't find a supported preview size smaller than 960x720.");
                findBestPreviewSize(supportedSizes, false, false);
            }
        }
        Log.v(TAG, "preview h = " + mPreviewHeight + " , w = " + mPreviewWidth);
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);

        List<int[]> frameRates = parameters.getSupportedPreviewFpsRange();
        int last = frameRates.size() - 1;
        int minFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MIN_INDEX];
        int maxFps = (frameRates.get(last))[Parameters.PREVIEW_FPS_MAX_INDEX];
        parameters.setPreviewFpsRange(minFps, maxFps);
        Log.v(TAG, "preview fps: " + minFps + ", " + maxFps);
    }

    public int getPreviewBufSize() {
        PixelFormat pixelInfo = new PixelFormat();
        PixelFormat.getPixelFormatInfo(mCameraDevice.getParameters().getPreviewFormat(), pixelInfo);
        // TODO: remove this extra 32 byte after the driver bug is fixed.
        return (mPreviewWidth * mPreviewHeight * pixelInfo.bitsPerPixel / 8) + 32;
    }

    private void configureCamera(Parameters parameters) {
        mCameraDevice.setParameters(parameters);

        Util.setCameraDisplayOrientation(
                Util.getDisplayRotation(this),
                CameraHolder.instance().getBackCameraId(), mCameraDevice);

        int bufSize = getPreviewBufSize();
        Log.v(TAG, "BufSize = " + bufSize);
        for (int i = 0; i < 10; i++) {
            try {
                mCameraDevice.addCallbackBuffer(new byte[bufSize]);
            } catch (OutOfMemoryError e) {
                Log.v(TAG, "Buffer allocation failed: buffer " + i);
                break;
            }
        }
    }

    private void createContentView() {
        setContentView(R.layout.panorama);

        mPreview = (Preview) findViewById(R.id.pano_preview);
        mCaptureView = (CaptureView) findViewById(R.id.pano_capture_view);
        mCaptureView.setStartAngle(-DEFAULT_SWEEP_ANGLE / 2);
        mCaptureView.setVisibility(View.INVISIBLE);

        mReview = (ImageView) findViewById(R.id.pano_reviewarea);
        mReview.setVisibility(View.INVISIBLE);

        mShutterButton = (ShutterButton) findViewById(R.id.pano_shutter_button);
        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPreview.setCaptureStarted(DEFAULT_SWEEP_ANGLE, DEFAULT_BLEND_MODE);
            }
        });
    }

    public void showResultingMosaic(String uri) {
        Uri parsed = Uri.parse(uri);
        mReview.setImageURI(parsed);
        mReview.setVisibility(View.VISIBLE);
        mPreview.setVisibility(View.INVISIBLE);
        mCaptureView.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.onPause();
        mSensorManager.unregisterListener(mListener);
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
         * It is not necessary to get accelerometer events at a very high
         * rate, by using a slower rate (SENSOR_DELAY_UI), we get an
         * automatic low-pass filter, which "extracts" the gravity component
         * of the acceleration. As an added benefit, we use less power and
         * CPU resources.
         */
        mSensorManager.registerListener(mListener, mSensor, SensorManager.SENSOR_DELAY_UI);

        setupCamera();
        mPreview.setCameraDevice(mCameraDevice);
        mCameraDevice.startPreview();
    }

    private void releaseCamera() {
        if (mCameraDevice != null){
            CameraHolder.instance().release();
            mCameraDevice = null;
        }
    }

    private final SensorEventListener mListener = new SensorEventListener() {
        private float mCompassCurrX; // degrees
        private float mCompassCurrY; // degrees
        private float mTimestamp;

        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                if (mTimestamp != 0) {
                    final float dT = (event.timestamp - mTimestamp) * NS2S;
                    mCompassCurrX += event.values[1] * dT * 180.0f / Math.PI;
                    mCompassCurrY += event.values[0] * dT * 180.0f / Math.PI;
                }
                mTimestamp = event.timestamp;

            } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                mCompassCurrX = event.values[0];
                mCompassCurrY = event.values[1];
            }

            if (mPreview != null) {
                 mPreview.updateCompassValue(mCompassCurrX, mCompassCurrY);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public int getPreviewFrameWidth() {
        return mPreviewWidth;
    }

    public int getPreviewFrameHeight() {
        return mPreviewHeight;
    }

    public CaptureView getCaptureView() {
        return mCaptureView;
    }
}
