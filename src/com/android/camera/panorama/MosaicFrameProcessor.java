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

import android.util.Log;

/**
 * Class to handle the processing of each frame by Mosaicer.
 */
public class MosaicFrameProcessor {
    private static final boolean LOGV = true;
    private static final String TAG = "MosaicFrameProcessor";
    private static final int NUM_FRAMES_IN_BUFFER = 2;
    private static final int MAX_NUMBER_OF_FRAMES = 100;
    private static final int FRAME_COUNT_INDEX = 9;
    private static final int X_COORD_INDEX = 2;
    private static final int Y_COORD_INDEX = 5;

    private Mosaic mMosaicer;
    private final byte[][] mFrames = new byte[NUM_FRAMES_IN_BUFFER][];  // Space for N frames
    private final long [] mFrameTimestamp = new long[NUM_FRAMES_IN_BUFFER];
    private float mTranslationLastX;
    private float mTranslationLastY;

    private int mFillIn = 0;
    private int mTotalFrameCount = 0;
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
                int traversedAngleX, int traversedAngleY);
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

    public void initialize() {
        setupMosaicer(mPreviewWidth, mPreviewHeight, mPreviewBufferSize);
        reset();
    }

    public void clear() {
        mMosaicer.freeMosaicMemory();

        for (int i = 0; i < NUM_FRAMES_IN_BUFFER; i++) {
            mFrames[i] = null;
        }
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

    public void reset() {
        // reset() can be called even if MosaicFrameProcessor is not initialized.
        // Only counters will be changed.
        mTotalFrameCount = 0;
        mFillIn = 0;
        mLastProcessedFrameTimestamp = 0;
        mLastProcessFrameIdx = -1;
        mCurrProcessFrameIdx = -1;
        mMosaicer.reset();
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
    public void processFrame(byte[] data) {
        if (mFrames[mFillIn] == null) {
            // clear() is called and buffers are cleared, stop computation.
            // This can happen when the onPause() is called in the activity, but still some frames
            // are not processed yet and thus the callback may be invoked.
            return;
        }
        long t1 = System.currentTimeMillis();
        mFrameTimestamp[mFillIn] = t1;
        System.arraycopy(data, 0, mFrames[mFillIn], 0, data.length);
        mCurrProcessFrameIdx = mFillIn;
        mFillIn = ((mFillIn + 1) % NUM_FRAMES_IN_BUFFER);


        // Check that we are trying to process a frame different from the
        // last one processed (useful if this class was running asynchronously)
        if (mCurrProcessFrameIdx != mLastProcessFrameIdx) {
            mLastProcessFrameIdx = mCurrProcessFrameIdx;

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
                translateFrame(currentFrame, timestamp);

                // Publish progress of the ongoing processing
                if (mProgressListener != null) {
                    mProgressListener.onProgress(false, mTranslationRate,
                            mTraversedAngleX, mTraversedAngleY);
                }
            } else {
                if (mProgressListener != null) {
                    mProgressListener.onProgress(true, mTranslationRate,
                            mTraversedAngleX, mTraversedAngleY);
                }
            }
        }
    }

    public void translateFrame(final byte[] data, long now) {
        float deltaTime = (now - mLastProcessedFrameTimestamp) / 1000.0f;
        mLastProcessedFrameTimestamp = now;

        float[] frameData = mMosaicer.setSourceImage(data);
        mTotalFrameCount  = (int) frameData[FRAME_COUNT_INDEX];
        float translationCurrX = frameData[X_COORD_INDEX];
        float translationCurrY = frameData[Y_COORD_INDEX];

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
