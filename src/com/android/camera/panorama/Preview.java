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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.android.camera.R;
import com.android.camera.Storage;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

class Preview extends SurfaceView implements SurfaceHolder.Callback,
        Camera.PreviewCallback {
    private static final String TAG = "Preview";
    private static final boolean LOGV = true;
    private static final int NUM_FRAMES_IN_BUFFER = 2;
    private static final int MAX_NUMBER_OF_FRAMES = 100;
    private static final int DOWN_SAMPLE_SIZE = 4;

    private final Object mLockFillIn = new Object();  // Object used for synchronization of mFillIn
    private final byte[][] mFrames = new byte[NUM_FRAMES_IN_BUFFER][];  // Space for N frames
    private final long [] mFrameTimestamp = new long[NUM_FRAMES_IN_BUFFER];

    private PanoramaActivity mActivity;
    private Mosaic mMosaicer;
    private LowResFrameProcessor mLowResProcessor = null;
    private SurfaceHolder mHolder;

    private android.hardware.Camera mCameraDevice;

    private Bitmap mLRBitmapAlpha = null;
    private Matrix mTransformationMatrix = null;

    private int mFillIn = 0;
    private long mLastProcessedFrameTimestamp = 0;
    private int mTotalFrameCount = 0;
    private int[] mColors = null;

    private int mPreviewWidth;
    private int mPreviewHeight;

    private float mTranslationLastX;
    private float mTranslationLastY;
    private float mTranslationRate;

    private ScannerClient mScannerClient;

    private String mCurrentImagePath = null;
    private long mTimeTaken;

    // Need handler for callbacks to the UI thread
    private final Handler mHandler = new Handler();

    // Create runnable for posting
    private final Runnable mUpdateResults = new Runnable() {
        public void run() {
            mActivity.showResultingMosaic("file://" + mCurrentImagePath);
            mScannerClient.scanPath(mCurrentImagePath);
        }
    };

    public Preview(Context context, AttributeSet attrs) {
        super(context, attrs);

        mActivity = (PanoramaActivity) getContext();

        mMosaicer = new Mosaic();
        mScannerClient = new ScannerClient(getContext());

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private class LowResFrameProcessor {
        private CaptureView mCaptureView;
        int mLastProcessFrameIdx = -1;
        int mCurrProcessFrameIdx = -1;
        boolean mRun = true;

        private float mCompassValueX;
        private float mCompassValueY;
        private float mCompassValueXStart;
        private float mCompassValueYStart;
        private int mCompassThreshold;
        private int mTraversedAngleX;
        private int mTraversedAngleY;


        public LowResFrameProcessor(int sweepAngle, CaptureView overlayer) {
            mCompassThreshold = sweepAngle;
            mCaptureView = overlayer;
        }

        // Processes the last filled image frame through the mosaicer and
        // updates the UI to show progress.
        // When done, processes and displays the final mosaic.
        public void runEachFrame() {
            mCurrProcessFrameIdx = getLastFilledIn();

            // Check that we are trying to process a frame different from the
            // last one processed (useful if this class was running asynchronously)
            if (mCurrProcessFrameIdx != mLastProcessFrameIdx) {
                mLastProcessFrameIdx = mCurrProcessFrameIdx;

                if (LOGV) Log.v(TAG, "Processing: [" + mCurrProcessFrameIdx + "]");

                // Access the image data and the timestamp associated with it...
                byte[] data = mFrames[mCurrProcessFrameIdx];
                long timestamp = mFrameTimestamp[mCurrProcessFrameIdx];

                // Keep track of what compass bearing we started at...
                if (mTotalFrameCount == 0) { // First frame
                    mCompassValueXStart = mCompassValueX;
                    mCompassValueYStart = mCompassValueY;
                }

                // By what angle has the camera moved since start of capture?
                mTraversedAngleX = (int) PanoUtil.calculateDifferenceBetweenAngles(
                        mCompassValueX, mCompassValueXStart);
                mTraversedAngleY = (int) PanoUtil.calculateDifferenceBetweenAngles(
                        mCompassValueY, mCompassValueYStart);

                if (mTotalFrameCount <= MAX_NUMBER_OF_FRAMES
                        && mTraversedAngleX < mCompassThreshold
                        && mTraversedAngleY < mCompassThreshold) {
                    // If we are still collecting new frames for the current mosaic,
                    // process the new frame.
                    processFrame(data, timestamp);

                    // Publish progress of the ongoing processing
                    publishProgress(0);
                } else {
                    // Publish progress that we are done with capture
                    publishProgress(1);

                    // Background-process the final blending of the mosaic so
                    // that the UI is not blocked.
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            generateAndStoreFinalMosaic(false);
                        }
                    };
                    t.start();

                    mRun = false;
                }
            }
        }

        // Sets the screen layout before starting each fresh capture.
        protected void onPreExecute() {
            if (mTotalFrameCount == 0) {
                mCaptureView.setVisibility(View.VISIBLE);
            }
        }

        // Updates the GUI with ongoing updates if values[0]==0 and
        // with the constructed mosaic for values[0]==1.
        public void publishProgress(Integer... values) {
            long t1 = System.currentTimeMillis();

            if (values[0] == 0) {  // Ongoing
                // This updates the real-time mosaic display with the current image frame and the
                // transformation matrix to warp it by.
                mCaptureView.setBitmap(mLRBitmapAlpha, mTransformationMatrix);

                // Update the sweep-angle sector display and show "SLOW DOWN" message if the user
                // is moving the camera too fast
                if (mTranslationRate > 150) {
                    // TODO: remove the text and draw implications according to the UI spec.
                    mCaptureView.setStatusText("S L O W   D O W N");
                    mCaptureView.setSweepAngle(
                            Math.max(mTraversedAngleX, mTraversedAngleY) + 1);
                    mCaptureView.invalidate();
                } else {
                    mCaptureView.setStatusText("");
                    mCaptureView.setSweepAngle(
                            Math.max(mTraversedAngleX, mTraversedAngleY) + 1);
                    mCaptureView.invalidate();
                }
            } else {  // Done
                setVisibility(View.INVISIBLE);
                mCaptureView.setVisibility(View.INVISIBLE);
                mCaptureView.setBitmap(null);
                mCaptureView.setStatusText("");
                mCaptureView.setSweepAngle(0);
                mCaptureView.invalidate();
            }

            long t2 = System.currentTimeMillis();
        }

        public void updateCompassValue(float valueX, float valueY) {
            mCompassValueX = valueX;
            mCompassValueY = valueY;
        }
    }

    public void updateCompassValue(float valueX, float valueY) {
        if (mLowResProcessor != null) {
            mLowResProcessor.updateCompassValue(valueX, valueY);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private void setupMosaicer() {
        mPreviewWidth = mActivity.getPreviewFrameWidth();
        mPreviewHeight = mActivity.getPreviewFrameHeight();

        mMosaicer.setSourceImageDimensions(mPreviewWidth, mPreviewHeight);

        mColors = new int[(mActivity.getPreviewFrameWidth() / DOWN_SAMPLE_SIZE)
                * (mActivity.getPreviewFrameHeight() / DOWN_SAMPLE_SIZE)];
        mLRBitmapAlpha = Bitmap.createBitmap((mPreviewWidth / DOWN_SAMPLE_SIZE),
                (mPreviewHeight / DOWN_SAMPLE_SIZE), Config.ARGB_8888);
        mTransformationMatrix = new Matrix();

        int bufSize = mActivity.getPreviewBufSize();
        for (int i = 0; i < NUM_FRAMES_IN_BUFFER; i++) {
            mFrames[i] = new byte[bufSize];
        }
    }

    public void setCameraDevice(android.hardware.Camera camera) {
        setupMosaicer();

        mCameraDevice = camera;
        // Preview callback used whenever new viewfinder frame is available
        mCameraDevice.setPreviewCallbackWithBuffer(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    public void onPreviewFrame(final byte[] data, Camera camera) {
        long t1 = System.currentTimeMillis();
        synchronized (mLockFillIn) {
            mFrameTimestamp[mFillIn] = t1;
            System.arraycopy(data, 0, mFrames[mFillIn], 0, data.length);
        }
        incrementFillIn();

        if (mLowResProcessor != null && mLowResProcessor.mRun) {
            mLowResProcessor.runEachFrame();
        }

        // The returned buffer needs be added back to callback buffer again.
        if (mCameraDevice != null) {
            mCameraDevice.addCallbackBuffer(data);
        }
    }

    public void processFrame(final byte[] data, long now) {
        float deltaTime = (float) (now - mLastProcessedFrameTimestamp) / 1000.0f;
        mLastProcessedFrameTimestamp = now;

        long t1 = System.currentTimeMillis();

        float[] frameData = mMosaicer.setSourceImage(data);

        mTotalFrameCount  = (int) frameData[9];
        float translationCurrX = frameData[2];
        float translationCurrY = frameData[5];

        long t2 = System.currentTimeMillis();

        Log.v(TAG, "[ " + deltaTime + " ] AddFrame: " + (t2 - t1));

        t1 = System.currentTimeMillis();
        mTransformationMatrix.setValues(frameData);

        int outw = mPreviewWidth / DOWN_SAMPLE_SIZE;
        int outh = mPreviewHeight / DOWN_SAMPLE_SIZE;

        PanoUtil.decodeYUV420SPQuarterRes(mColors, data, mPreviewWidth, mPreviewHeight);

        mLRBitmapAlpha.setPixels(mColors, 0, outw, 0, 0, outw, outh);

        t2 = System.currentTimeMillis();
        Log.v(TAG, "GenerateLowResBitmap: " + (t2 - t1));

        mTranslationRate  = Math.max(Math.abs(translationCurrX - mTranslationLastX),
                Math.abs(translationCurrY - mTranslationLastY)) / deltaTime;
        mTranslationLastX = translationCurrX;
        mTranslationLastY = translationCurrY;
    }

    public void generateAndStoreFinalMosaic(boolean highRes) {
        long t1 = System.currentTimeMillis();

        mMosaicer.createMosaic(highRes);

        mCurrentImagePath = Storage.DIRECTORY + "/" + PanoUtil.createName(
                mActivity.getResources().getString(R.string.pano_file_name_format), mTimeTaken);

        if (highRes) {
            mCurrentImagePath += "_HR.jpg";
        } else {
            mCurrentImagePath += "_LR.jpg";
        }

        long t2 = System.currentTimeMillis();
        long dur = (t2 - t1) / 1000;

        try {
            File mosDirectory = new File(Storage.DIRECTORY);
            // have the object build the directory structure, if needed.
            mosDirectory.mkdirs();

            byte[] imageData = mMosaicer.getFinalMosaicNV21();
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

    public void setCaptureStarted(int sweepAngle, int blendType) {
        // Reset values so we can do this again.
        mMosaicer.reset();
        mTotalFrameCount = 0;
        mLastProcessedFrameTimestamp = 0;

        mTimeTaken = System.currentTimeMillis();
        CaptureView captureView = mActivity.getCaptureView();
        captureView.setVisibility(View.VISIBLE);
        mLowResProcessor = new LowResFrameProcessor(sweepAngle - 5, captureView);

        mLowResProcessor.onPreExecute();
    }

    /**
     * This must be called when the activity pauses (in Activity.onPause).
     */
    public void onPause() {
        mMosaicer.reset();
    }

    private int getLastFilledIn() {
        synchronized (mLockFillIn) {
            if (mFillIn > 0) {
                return mFillIn - 1;
            } else {
                return NUM_FRAMES_IN_BUFFER - 1;
            }
        }
    }

    private void incrementFillIn() {
        synchronized (mLockFillIn) {
            mFillIn = ((mFillIn + 1) >= NUM_FRAMES_IN_BUFFER) ? 0 : (mFillIn + 1);
        }
    }

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
}
