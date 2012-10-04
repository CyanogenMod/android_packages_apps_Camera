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

import com.android.camera.CameraActivity;

import android.app.Instrumentation;
import android.os.Environment;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedWriter;
import java.io.FileWriter;

/**
 * Junit / Instrumentation test case for camera test
 *
 */

public class CameraLatency extends ActivityInstrumentationTestCase2 <CameraActivity> {
    private String TAG = "CameraLatency";
    private static final int TOTAL_NUMBER_OF_IMAGECAPTURE = 20;
    private static final long WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN = 4000;
    private static final String CAMERA_TEST_OUTPUT_FILE =
            Environment.getExternalStorageDirectory().toString() + "/mediaStressOut.txt";

    private long mTotalAutoFocusTime;
    private long mTotalShutterLag;
    private long mTotalShutterToPictureDisplayedTime;
    private long mTotalPictureDisplayedToJpegCallbackTime;
    private long mTotalJpegCallbackFinishTime;
    private long mAvgAutoFocusTime;
    private long mAvgShutterLag = mTotalShutterLag;
    private long mAvgShutterToPictureDisplayedTime;
    private long mAvgPictureDisplayedToJpegCallbackTime;
    private long mAvgJpegCallbackFinishTime;

    public CameraLatency() {
        super(CameraActivity.class);
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
        Log.v(TAG, "start testImageCapture test");
        Instrumentation inst = getInstrumentation();
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        try {
            for (int i = 0; i < TOTAL_NUMBER_OF_IMAGECAPTURE; i++) {
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                //skip the first measurement
                if (i != 0) {
                    CameraActivity c = getActivity();

                    // if any of the latency var accessor methods return -1 then the
                    // camera is set to a different module other than PhotoModule so
                    // skip the shot and try again
                    if (c.getAutoFocusTime() != -1) {
                        mTotalAutoFocusTime += c.getAutoFocusTime();
                        mTotalShutterLag += c.getShutterLag();
                        mTotalShutterToPictureDisplayedTime +=
                                c.getShutterToPictureDisplayedTime();
                        mTotalPictureDisplayedToJpegCallbackTime +=
                                c.getPictureDisplayedToJpegCallbackTime();
                        mTotalJpegCallbackFinishTime += c.getJpegCallbackFinishTime();
                    }
                    else {
                        i--;
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            Log.v(TAG, "Got exception", e);
        }
        //ToDO: yslau
        //1) Need to get the baseline from the cupcake so that we can add the
        //failure condition of the camera latency.
        //2) Only count those number with succesful capture. Set the timer to invalid
        //before capture and ignore them if the value is invalid
        int numberofRun = TOTAL_NUMBER_OF_IMAGECAPTURE - 1;
        mAvgAutoFocusTime = mTotalAutoFocusTime / numberofRun;
        mAvgShutterLag = mTotalShutterLag / numberofRun;
        mAvgShutterToPictureDisplayedTime =
                mTotalShutterToPictureDisplayedTime / numberofRun;
        mAvgPictureDisplayedToJpegCallbackTime =
                mTotalPictureDisplayedToJpegCallbackTime / numberofRun;
        mAvgJpegCallbackFinishTime =
                mTotalJpegCallbackFinishTime / numberofRun;

        try {
            FileWriter fstream = null;
            fstream = new FileWriter(CAMERA_TEST_OUTPUT_FILE, true);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write("Camera Latency : \n");
            out.write("Number of loop: " + TOTAL_NUMBER_OF_IMAGECAPTURE + "\n");
            out.write("Avg AutoFocus = " + mAvgAutoFocusTime + "\n");
            out.write("Avg mShutterLag = " + mAvgShutterLag + "\n");
            out.write("Avg mShutterToPictureDisplayedTime = "
                    + mAvgShutterToPictureDisplayedTime + "\n");
            out.write("Avg mPictureDisplayedToJpegCallbackTime = "
                    + mAvgPictureDisplayedToJpegCallbackTime + "\n");
            out.write("Avg mJpegCallbackFinishTime = " +
                    mAvgJpegCallbackFinishTime + "\n");
            out.close();
            fstream.close();
        } catch (Exception e) {
            fail("Camera Latency write output to file");
        }
        Log.v(TAG, "The Image capture wait time = " +
            WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
        Log.v(TAG, "Avg AutoFocus = " + mAvgAutoFocusTime);
        Log.v(TAG, "Avg mShutterLag = " + mAvgShutterLag);
        Log.v(TAG, "Avg mShutterToPictureDisplayedTime = "
                + mAvgShutterToPictureDisplayedTime);
        Log.v(TAG, "Avg mPictureDisplayedToJpegCallbackTime = "
                + mAvgPictureDisplayedToJpegCallbackTime);
        Log.v(TAG, "Avg mJpegCallbackFinishTime = " + mAvgJpegCallbackFinishTime);
    }
}

