/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.stress;

import com.android.camera.Camera;

import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Junit / Instrumentation test case for camera test
 *
 */

public class CameraLatency extends ActivityInstrumentationTestCase2 <Camera> {
    private String TAG = "CameraLatency";
    private static final int TOTAL_NUMBER_OF_IMAGECAPTURE = 20;
    private static final long WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN = 3000;

    private long mTotalAutoFocusTime;
    private long mTotalShutterLag;
    private long mTotalShutterAndRawPictureCallbackTime;
    private long mTotalJpegPictureCallbackTimeLag;
    private long mTotalRawPictureAndJpegPictureCallbackTime;
    private long mAvgAutoFocusTime;
    private long mAvgShutterLag = mTotalShutterLag;
    private long mAvgShutterAndRawPictureCallbackTime;
    private long mAveJpegPictureCallbackTimeLag;
    private long mAvgRawPictureAndJpegPictureCallbackTime;

    public CameraLatency() {
        super("com.android.camera", Camera.class);
    }

    @Override
    protected void setUp() throws Exception {
        getActivity();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @LargeTest
    public void testImageCapture() {
        Instrumentation inst = getInstrumentation();
        try {
            for (int i = 0; i < TOTAL_NUMBER_OF_IMAGECAPTURE; i++) {
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                //skip the first measurement
                if (i != 0) {
                    mTotalAutoFocusTime += Camera.mAutoFocusTime;
                    mTotalShutterLag += Camera.mShutterLag;
                    mTotalShutterAndRawPictureCallbackTime +=
                            Camera.mShutterAndRawPictureCallbackTime;
                    mTotalJpegPictureCallbackTimeLag += Camera.mJpegPictureCallbackTimeLag;
                    mTotalRawPictureAndJpegPictureCallbackTime +=
                            Camera.mRawPictureAndJpegPictureCallbackTime;
                }
            }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }
        //ToDO: yslau
        //1) Need to get the baseline from the cupcake so that we can add the
        //failure condition of the camera latency.
        //2) Only count those number with succesful capture. Set the timer to invalid
        //before capture and ignore them if the value is invalid
        int numberofRun = TOTAL_NUMBER_OF_IMAGECAPTURE - 1;
        mAvgAutoFocusTime = mTotalAutoFocusTime / numberofRun;
        mAvgShutterLag = mTotalShutterLag / numberofRun;
        mAvgShutterAndRawPictureCallbackTime =
            mTotalShutterAndRawPictureCallbackTime / numberofRun;
        mAveJpegPictureCallbackTimeLag =
            mTotalJpegPictureCallbackTimeLag / numberofRun;
        mAvgRawPictureAndJpegPictureCallbackTime =
                mTotalRawPictureAndJpegPictureCallbackTime / numberofRun;

        Log.v(TAG, "Avg AutoFocus = " + mAvgAutoFocusTime);
        Log.v(TAG, "Avg mShutterLag = " + mAvgShutterLag);
        Log.v(TAG, "Avg mShutterAndRawPictureCallbackTime = "
                + mAvgShutterAndRawPictureCallbackTime);
        Log.v(TAG, "Avg mJpegPictureCallbackTimeLag = "
                + mAveJpegPictureCallbackTimeLag);
        Log.v(TAG, "Avg mRawPictureAndJpegPictureCallbackTime = "
                + mAvgRawPictureAndJpegPictureCallbackTime);
        assertTrue("testImageCapture", true);
    }
}

