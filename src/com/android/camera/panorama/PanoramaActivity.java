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
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.camera.CameraDisabledException;
import com.android.camera.CameraHardwareException;
import com.android.camera.CameraHolder;
import com.android.camera.MenuHelper;
import com.android.camera.ModePicker;
import com.android.camera.R;
import com.android.camera.ShutterButton;
import com.android.camera.Storage;
import com.android.camera.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PanoramaActivity extends Activity implements
        ModePicker.OnModeChangeListener {
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

    private Camera mCameraDevice;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private ModePicker mModePicker;

    private MosaicFrameProcessor mMosaicFrameProcessor;
    private ScannerClient mScannerClient;

    private String mCurrentImagePath = null;
    private long mTimeTaken;

    // Need handler for callbacks to the UI thread
    private final Handler mHandler = new Handler();

    /**
     * Inner class to tell the gallery app to scan the newly created mosaic images.
     * TODO: insert the image to media store.
     */
    private static final class ScannerClient implements MediaScannerConnectionClient {
        ArrayList<String> mPaths = new ArrayList<String>();
        MediaScannerConnection mScannerConnection;
        boolean mConnected;
        Object mLock = new Object();

        public ScannerClient(Context context) {
            mScannerConnection = new MediaScannerConnection(context, this);
        }

        public void scanPath(String path) {
            synchronized (mLock) {
                if (mConnected) {
                    mScannerConnection.scanFile(path, null);
                } else {
                    mPaths.add(path);
                    mScannerConnection.connect();
                }
            }
        }

        @Override
        public void onMediaScannerConnected() {
            synchronized (mLock) {
                mConnected = true;
                if (!mPaths.isEmpty()) {
                    for (String path : mPaths) {
                        mScannerConnection.scanFile(path, null);
                    }
                    mPaths.clear();
                }
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
        }
    }

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
        mScannerClient = new ScannerClient(getApplicationContext());

    }

    // Create runnable for posting
    private final Runnable mUpdateResults = new Runnable() {
        public void run() {
            showResultingMosaic("file://" + mCurrentImagePath);
            mScannerClient.scanPath(mCurrentImagePath);
        }
    };

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
        for (Size size : supportedSizes) {
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

    private boolean switchToOtherMode(int mode) {
        if (isFinishing()) return false;
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

    public void setCaptureStarted(int sweepAngle, int blendType) {
        // Reset values so we can do this again.
        mTimeTaken = System.currentTimeMillis();
        mMosaicFrameProcessor = new MosaicFrameProcessor(sweepAngle - 5, mPreviewWidth,
                mPreviewHeight, getPreviewBufSize());

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
                // The returned buffer needs be added back to callback buffer again.
                camera.addCallbackBuffer(data);
            }
        });

        mCaptureView.setVisibility(View.VISIBLE);
    }

    private void onMosaicFinished() {
        mMosaicFrameProcessor.setProgressListener(null);
        mPreview.setVisibility(View.INVISIBLE);
        mCaptureView.setVisibility(View.INVISIBLE);
        mCaptureView.setBitmap(null);
        mCaptureView.setStatusText("");
        mCaptureView.setSweepAngle(0);
        mCaptureView.invalidate();
        // Background-process the final blending of the mosaic so
        // that the UI is not blocked.
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
            // TODO: remove the text and draw implications according to the UI spec.
            mCaptureView.setStatusText("S L O W   D O W N");
            mCaptureView.setSweepAngle(
                    Math.max(traversedAngleX, traversedAngleY) + 1);
            mCaptureView.invalidate();
        } else {
            mCaptureView.setStatusText("");
            mCaptureView.setSweepAngle(
                    Math.max(traversedAngleX, traversedAngleY) + 1);
            mCaptureView.invalidate();
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
                setCaptureStarted(DEFAULT_SWEEP_ANGLE, DEFAULT_BLEND_MODE);
            }
        });
        mModePicker = (ModePicker) findViewById(R.id.mode_picker);
        mModePicker.setVisibility(View.VISIBLE);
        mModePicker.setOnModeChangeListener(this);
        mModePicker.setCurrentMode(ModePicker.MODE_PANORAMA);
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

        mCurrentImagePath = Storage.DIRECTORY + "/" + PanoUtil.createName(
                getResources().getString(R.string.pano_file_name_format), mTimeTaken);

        if (highRes) {
            mCurrentImagePath += "_HR.jpg";
        } else {
            mCurrentImagePath += "_LR.jpg";
        }

        try {
            File mosDirectory = new File(Storage.DIRECTORY);
            // have the object build the directory structure, if needed.
            mosDirectory.mkdirs();

            byte[] imageData = mMosaicFrameProcessor.getFinalMosaicNV21();
            int len = imageData.length - 8;

            int width = (imageData[len + 0] << 24) + ((imageData[len + 1] & 0xFF) << 16)
                    + ((imageData[len + 2] & 0xFF) << 8) + (imageData[len + 3] & 0xFF);
            int height = (imageData[len + 4] << 24) + ((imageData[len + 5] & 0xFF) << 16)
                    + ((imageData[len + 6] & 0xFF) << 8) + (imageData[len + 7] & 0xFF);
            Log.v(TAG, "ImLength = " + (len) + ", W = " + width + ", H = " + height);

            YuvImage yuvimage = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
            FileOutputStream out = new FileOutputStream(mCurrentImagePath);
            yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            out.close();

            // Now's a good time to run the GC.  Since we won't do any explicit
            // allocation during the test, the GC should stay dormant and not
            // influence our results.
            System.runFinalization();
            System.gc();

            mHandler.post(mUpdateResults);
        } catch (Exception e) {
            Log.e(TAG, "exception in storing final mosaic", e);
        }
    }

}
