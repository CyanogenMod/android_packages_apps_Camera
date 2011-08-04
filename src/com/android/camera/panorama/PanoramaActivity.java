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

import com.android.camera.CameraDisabledException;
import com.android.camera.CameraHardwareException;
import com.android.camera.CameraHolder;
import com.android.camera.MenuHelper;
import com.android.camera.ModePicker;
import com.android.camera.R;
import com.android.camera.ShutterButton;
import com.android.camera.Storage;
import com.android.camera.Util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class PanoramaActivity extends Activity implements
        ModePicker.OnModeChangeListener, SurfaceHolder.Callback {
    public static final int DEFAULT_SWEEP_ANGLE = 60;
    public static final int DEFAULT_BLEND_MODE = Mosaic.BLENDTYPE_HORIZONTAL;
    public static final int DEFAULT_CAPTURE_PIXELS = 960 * 720;

    private static final int MSG_FINAL_MOSAIC_READY = 1;

    private static final String TAG = "PanoramaActivity";

    // Ratio of nanosecond to second
    private static final float NS2S = 1.0f / 1000000000.0f;

    private static final int PREVIEW_STOPPED = 0;
    private static final int PREVIEW_ACTIVE = 1;

    private SurfaceView mPreview;
    private ImageView mReview;
    private CaptureView mCaptureView;

    private ShutterButton mShutterButton;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private Camera mCameraDevice;
    private int mCameraState;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private ModePicker mModePicker;
    private MosaicFrameProcessor mMosaicFrameProcessor;
    private String mCurrentImagePath = null;
    private long mTimeTaken;
    private Handler mMainHandler;
    private SurfaceHolder mSurfaceHolder;

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

        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_FINAL_MOSAIC_READY:
                        Uri uri = (Uri) msg.obj;
                        showFinalMosaic(uri);
                }
            }
        };
    }

    private void setupCamera() {
        openCamera();
        Parameters parameters = mCameraDevice.getParameters();
        setupCaptureParams(parameters);
        configureCamera(parameters);
    }

    private void releaseCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            mCameraDevice = null;
            mCameraState = PREVIEW_STOPPED;
        }
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
        for (Size size : supportedSizes) {
            int h = size.height;
            int w = size.width;
            // we only want 4:3 format.
            int d = DEFAULT_CAPTURE_PIXELS - h * w;
            if (needSmaller && d < 0) { // no bigger preview than 960x720.
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

        // TODO: use camera API after it is published.
        parameters.set("recording-hint", "false");
    }

    public int getPreviewBufSize() {
        PixelFormat pixelInfo = new PixelFormat();
        PixelFormat.getPixelFormatInfo(mCameraDevice.getParameters().getPreviewFormat(), pixelInfo);
        // TODO: remove this extra 32 byte after the driver bug is fixed.
        return (mPreviewWidth * mPreviewHeight * pixelInfo.bitsPerPixel / 8) + 32;
    }

    private void configureCamera(Parameters parameters) {
        mCameraDevice.setParameters(parameters);

        int orientation = Util.getDisplayOrientation(Util.getDisplayRotation(this),
                CameraHolder.instance().getBackCameraId());
        mCameraDevice.setDisplayOrientation(orientation);

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

    private boolean switchToOtherMode(int mode) {
        if (isFinishing())
            return false;
        MenuHelper.gotoMode(mode, this);
        finish();
        return true;
    }

    public boolean onModeChanged(int mode) {
        if (mode != ModePicker.MODE_PANORAMA) {
            return switchToOtherMode(mode);
        } else {
            return true;
        }
    }

    public void setCaptureStarted() {
        // Reset values so we can do this again.
        mTimeTaken = System.currentTimeMillis();

        mMosaicFrameProcessor.setProgressListener(new MosaicFrameProcessor.ProgressListener() {
            @Override
            public void onProgress(boolean isFinished, float translationRate, int traversedAngleX,
                    int traversedAngleY, Bitmap lowResBitmapAlpha, Matrix transformaMatrix) {
                if (isFinished) {
                    onMosaicFinished();
                } else {
                    updateProgress(translationRate, traversedAngleX, traversedAngleY,
                            lowResBitmapAlpha, transformaMatrix);
                }
            }
        });

        // Preview callback used whenever new viewfinder frame is available
        mCameraDevice.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] data, Camera camera) {
                mMosaicFrameProcessor.processFrame(data, mPreviewWidth, mPreviewHeight);
                // The returned buffer needs be added back to callback buffer
                // again.
                camera.addCallbackBuffer(data);
            }
        });

        mCaptureView.setVisibility(View.VISIBLE);
    }

    private void onMosaicFinished() {
        mMosaicFrameProcessor.setProgressListener(null);
        mCameraDevice.stopPreview();
        mCameraDevice.setPreviewCallbackWithBuffer(null);
        mPreview.setVisibility(View.INVISIBLE);
        mCaptureView.setVisibility(View.INVISIBLE);
        mCaptureView.setBitmap(null);
        mCaptureView.setStatusText("");
        mCaptureView.setSweepAngle(0);
        mCaptureView.invalidate();
        // TODO: show some dialog for long computation.
        Thread t = new Thread() {
            @Override
            public void run() {
                generateAndStoreFinalMosaic(false);
            }
        };
        t.start();
    }

    private void updateProgress(float translationRate, int traversedAngleX, int traversedAngleY,
            Bitmap lowResBitmapAlpha, Matrix transformationMatrix) {
        mCaptureView.setBitmap(lowResBitmapAlpha, transformationMatrix);
        if (translationRate > 150) {
            // TODO: remove the text and draw implications according to the UI
            // spec.
            mCaptureView.setStatusText("S L O W   D O W N");
            mCaptureView.setSweepAngle(Math.max(traversedAngleX, traversedAngleY) + 1);
            mCaptureView.invalidate();
        } else {
            mCaptureView.setStatusText("");
            mCaptureView.setSweepAngle(Math.max(traversedAngleX, traversedAngleY) + 1);
            mCaptureView.invalidate();
        }
    }

    private void createContentView() {
        setContentView(R.layout.panorama);

        mPreview = (SurfaceView) findViewById(R.id.pano_preview);
        mPreview.getHolder().addCallback(this);

        mCaptureView = (CaptureView) findViewById(R.id.pano_capture_view);
        mCaptureView.setStartAngle(-DEFAULT_SWEEP_ANGLE / 2);
        mCaptureView.setVisibility(View.INVISIBLE);

        mReview = (ImageView) findViewById(R.id.pano_reviewarea);
        mReview.setVisibility(View.INVISIBLE);

        mShutterButton = (ShutterButton) findViewById(R.id.pano_shutter_button);
        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCaptureStarted();
            }
        });
        mModePicker = (ModePicker) findViewById(R.id.mode_picker);
        mModePicker.setVisibility(View.VISIBLE);
        mModePicker.setOnModeChangeListener(this);
        mModePicker.setCurrentMode(ModePicker.MODE_PANORAMA);
    }

    private void showFinalMosaic(Uri uri) {
        mReview.setImageURI(uri);
        mReview.setVisibility(View.VISIBLE);
        mCaptureView.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
        mMosaicFrameProcessor.onPause();
        mCaptureView.onPause();
        mSensorManager.unregisterListener(mListener);
        System.gc();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
         * It is not necessary to get accelerometer events at a very high rate,
         * by using a slower rate (SENSOR_DELAY_UI), we get an automatic
         * low-pass filter, which "extracts" the gravity component of the
         * acceleration. As an added benefit, we use less power and CPU
         * resources.
         */
        mSensorManager.registerListener(mListener, mSensor, SensorManager.SENSOR_DELAY_UI);

        setupCamera();
        startPreview();

        if (mMosaicFrameProcessor == null) {
            // Start the activity for the first time.
            mMosaicFrameProcessor = new MosaicFrameProcessor(DEFAULT_SWEEP_ANGLE - 5,
                    mPreviewWidth, mPreviewHeight, getPreviewBufSize());
            mMosaicFrameProcessor.onResume();
        } else {
            mMosaicFrameProcessor.onResume();
        }
        mCaptureView.onResume();
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

            if (mMosaicFrameProcessor != null) {
                mMosaicFrameProcessor.updateCompassValue(mCompassCurrX, mCompassCurrY);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public void generateAndStoreFinalMosaic(boolean highRes) {
        mMosaicFrameProcessor.createMosaic(highRes);

        mCurrentImagePath = PanoUtil.createName(
                getResources().getString(R.string.pano_file_name_format), mTimeTaken);

        if (highRes) {
            mCurrentImagePath += "_HR";
        } else {
            mCurrentImagePath += "_LR";
        }

            byte[] imageData = mMosaicFrameProcessor.getFinalMosaicNV21();
            int len = imageData.length - 8;

            int width = (imageData[len + 0] << 24) + ((imageData[len + 1] & 0xFF) << 16)
                    + ((imageData[len + 2] & 0xFF) << 8) + (imageData[len + 3] & 0xFF);
            int height = (imageData[len + 4] << 24) + ((imageData[len + 5] & 0xFF) << 16)
                    + ((imageData[len + 6] & 0xFF) << 8) + (imageData[len + 7] & 0xFF);
            Log.v(TAG, "ImLength = " + (len) + ", W = " + width + ", H = " + height);

            YuvImage yuvimage = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Exception in storing final mosaic", e);
                return;
            }
            Uri uri = Storage.addImage(
                    getContentResolver(), mCurrentImagePath, mTimeTaken, null, 0,
                    out.toByteArray());

            mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_FINAL_MOSAIC_READY, uri));
            // Now's a good time to run the GC. Since we won't do any explicit
            // allocation during the test, the GC should stay dormant and not
            // influence our results.
            System.runFinalization();
            System.gc();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        mSurfaceHolder = holder;

        if (mCameraDevice == null) return;

        // Set preview display if the surface is being created. Preview was
        // already started. Also restart the preview if display rotation has
        // changed. Sometimes this happens when the device is held in portrait
        // and camera app is opened. Rotation animation takes some time and
        // display rotation in onCreate may not be what we want.
        if (holder.isCreating()) {
            // Set preview display if the surface is being created and preview
            // was already started. That means preview display was set to null
            // and we need to set it now.
            setPreviewDisplay(holder);
        } else {
            // 1. Restart the preview if the size of surface was changed. The
            // framework may not support changing preview display on the fly.
            // 2. Start the preview now if surface was destroyed and preview
            // stopped.
            startPreview();
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            releaseCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() {
        // If we're previewing already, stop the preview first (this will blank
        // the screen).
        if (mCameraState != PREVIEW_STOPPED) stopPreview();

        setPreviewDisplay(mSurfaceHolder);

        try {
            Log.v(TAG, "startPreview");
            mCameraDevice.startPreview();
        } catch (Throwable ex) {
            releaseCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
        mCameraState = PREVIEW_ACTIVE;
    }

    private void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
        }
        mCameraState = PREVIEW_STOPPED;
    }
}
