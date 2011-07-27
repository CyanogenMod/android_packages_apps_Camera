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

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Matrix;

import android.util.Log;

public class MosaicFrameProcessor {
    private static final boolean LOGV = true;
    private static final String TAG = "MosaicFrameProcessor";
    private static final int NUM_FRAMES_IN_BUFFER = 2;
    private static final int MAX_NUMBER_OF_FRAMES = 100;
    private static final int DOWN_SAMPLE_FACTOR = 4;
    private static final int FRAME_COUNT_INDEX = 9;
    private static final int X_COORD_INDEX = 2;
    private static final int Y_COORD_INDEX = 5;

    private Mosaic mMosaicer;
    private final byte[][] mFrames = new byte[NUM_FRAMES_IN_BUFFER][];  // Space for N frames
    private final long [] mFrameTimestamp = new long[NUM_FRAMES_IN_BUFFER];
    private Bitmap mLRBitmapAlpha = null;
    private Matrix mTransformationMatrix = null;
    private float mTranslationLastX;
    private float mTranslationLastY;

    private int mFillIn = 0;
    private int mTotalFrameCount = 0;
    private int[] mColors = null;
    private long mLastProcessedFrameTimestamp = 0;
    private int mLastProcessFrameIdx = -1;
    private int mCurrProcessFrameIdx = -1;

    private ProgressListener mProgressListener;

    private float mCompassValueX;
    private float mCompassValueY;
    private float mCompassValueXStart;
    private float mCompassValueYStart;
    private int mCompassThreshold;
    private int mTraversedAngleX;
    private int mTraversedAngleY;
    private float mTranslationRate;

    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mPreviewBufferSize;


    public interface ProgressListener {
        public void onProgress(boolean isFinished, float translationRate,
                int traversedAngleX, int traversedAngleY,
                Bitmap lowResBitmapAlpha, Matrix transformaMatrix);
    }

    public MosaicFrameProcessor(int sweepAngle, int previewWidth, int previewHeight, int bufSize) {
        mMosaicer = new Mosaic();
        mCompassThreshold = sweepAngle;
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        mPreviewBufferSize = bufSize;
    }

    public void setProgressListener(ProgressListener listener) {
        mProgressListener = listener;
    }

    public void onResume() {
        setupMosaicer(mPreviewWidth, mPreviewHeight, mPreviewBufferSize);
        setupAlphaBlendBitmap(mPreviewWidth, mPreviewHeight);
    }

    public void onPause() {
        releaseMosaicer();
        releaseAlphaBlendBitmap();
    }

    private void setupMosaicer(int previewWidth, int previewHeight, int bufSize) {
        Log.v(TAG, "setupMosaicer w, h=" + previewWidth + ',' + previewHeight + ',' + bufSize);
        mMosaicer.allocateMosaicMemory(previewWidth, previewHeight);

        for (int i = 0; i < NUM_FRAMES_IN_BUFFER; i++) {
            mFrames[i] = new byte[bufSize];
        }

        mFillIn = 0;
        if  (mMosaicer != null) {
            mMosaicer.reset();
        }
    }

    private void releaseMosaicer() {
        mMosaicer.freeMosaicMemory();

        for (int i = 0; i < NUM_FRAMES_IN_BUFFER; i++) {
            mFrames[i] = null;
        }
    }

    private void setupAlphaBlendBitmap(int width, int height) {
        int downSizedW = width / DOWN_SAMPLE_FACTOR;
        int downSizedH = height / DOWN_SAMPLE_FACTOR;
        mColors = new int[downSizedW * downSizedH];
        mLRBitmapAlpha = Bitmap.createBitmap(downSizedW, downSizedH, Config.ARGB_8888);
        mTransformationMatrix = new Matrix();
    }

    private void releaseAlphaBlendBitmap() {
        mColors = null;
        if (mLRBitmapAlpha != null) {
            mLRBitmapAlpha.recycle();
            mLRBitmapAlpha = null;
        }
    }

    public void createMosaic(boolean highRes) {
        mMosaicer.createMosaic(highRes);
    }

    public byte[] getFinalMosaicNV21() {
        return mMosaicer.getFinalMosaicNV21();
    }

    // Processes the last filled image frame through the mosaicer and
    // updates the UI to show progress.
    // When done, processes and displays the final mosaic.
    public void processFrame(byte[] data, int width, int height) {
        long t1 = System.currentTimeMillis();
        mFrameTimestamp[mFillIn] = t1;
        System.arraycopy(data, 0, mFrames[mFillIn], 0, data.length);
        mCurrProcessFrameIdx = mFillIn;
        mFillIn = ((mFillIn + 1) % NUM_FRAMES_IN_BUFFER);


        // Check that we are trying to process a frame different from the
        // last one processed (useful if this class was running asynchronously)
        if (mCurrProcessFrameIdx != mLastProcessFrameIdx) {
            mLastProcessFrameIdx = mCurrProcessFrameIdx;

            if (LOGV) Log.v(TAG, "Processing: [" + mCurrProcessFrameIdx + "]");

            // Access the image data and the timestamp associated with it...
            byte[] currentFrame = mFrames[mCurrProcessFrameIdx];
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
                translateFrame(currentFrame, width, height, timestamp);

                // Publish progress of the ongoing processing
                if (mProgressListener != null) {
                    mProgressListener.onProgress(
                            false, mTranslationRate, mTraversedAngleX, mTraversedAngleY,
                            mLRBitmapAlpha, mTransformationMatrix);
                }
            } else {
                if (mProgressListener != null) {
                    mProgressListener.onProgress(
                            true, mTranslationRate, mTraversedAngleX, mTraversedAngleY,
                            mLRBitmapAlpha, mTransformationMatrix);
                }
            }
        }
    }

    public void translateFrame(final byte[] data, int width, int height, long now) {
        float deltaTime = (float) (now - mLastProcessedFrameTimestamp) / 1000.0f;
        mLastProcessedFrameTimestamp = now;

        float[] frameData = mMosaicer.setSourceImage(data);
        mTotalFrameCount  = (int) frameData[FRAME_COUNT_INDEX];
        float translationCurrX = frameData[X_COORD_INDEX];
        float translationCurrY = frameData[Y_COORD_INDEX];
        mTransformationMatrix.setValues(frameData);

        int outw = width / DOWN_SAMPLE_FACTOR;
        int outh = height / DOWN_SAMPLE_FACTOR;

        PanoUtil.decodeYUV420SPQuarterRes(mColors, data, width, height);
        mLRBitmapAlpha.setPixels(mColors, 0, outw, 0, 0, outw, outh);

        mTranslationRate  = Math.max(Math.abs(translationCurrX - mTranslationLastX),
                Math.abs(translationCurrY - mTranslationLastY)) / deltaTime;
        mTranslationLastX = translationCurrX;
        mTranslationLastY = translationCurrY;
    }

    public void updateCompassValue(float valueX, float valueY) {
        mCompassValueX = valueX;
        mCompassValueY = valueY;
    }
}
