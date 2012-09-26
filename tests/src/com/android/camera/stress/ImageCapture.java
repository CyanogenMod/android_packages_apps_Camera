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
import com.android.camera.stress.CameraStressTestRunner;

import android.app.Instrumentation;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;
import android.app.Activity;

/**
 * Junit / Instrumentation test case for camera test
 *
 * Running the test suite:
 *
 * adb shell am instrument \
 *    -e class com.android.camera.stress.ImageCapture \
 *    -w com.google.android.camera.tests/android.test.InstrumentationTestRunner
 *
 */

public class ImageCapture extends ActivityInstrumentationTestCase2 <CameraActivity> {
    private String TAG = "ImageCapture";
    private static final long WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN = 1500;   //1.5 sedconds
    private static final long WAIT_FOR_SWITCH_CAMERA = 3000; //3 seconds

    private TestUtil testUtil = new TestUtil();

    // Private intent extras.
    private final static String EXTRAS_CAMERA_FACING =
        "android.intent.extras.CAMERA_FACING";

    public ImageCapture() {
        super(CameraActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        testUtil.prepareOutputFile();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        testUtil.closeOutputFile();
        super.tearDown();
    }

    public void captureImages(String reportTag, Instrumentation inst) {
        int total_num_of_images = CameraStressTestRunner.mImageIterations;
        Log.v(TAG, "no of images = " + total_num_of_images);

        //TODO(yslau): Need to integrate the outoput with the central dashboard,
        //write to a txt file as a temp solution
        boolean memoryResult = false;
        KeyEvent focusEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS);

        try {
            testUtil.writeReportHeader(reportTag, total_num_of_images);
            for (int i = 0; i < total_num_of_images; i++) {
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                inst.sendKeySync(focusEvent);
                inst.sendCharacterSync(KeyEvent.KEYCODE_CAMERA);
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                testUtil.writeResult(i);
            }
        } catch (Exception e) {
            Log.v(TAG, "Got exception: " + e.toString());
            assertTrue("testImageCapture", false);
        }
    }

    @LargeTest
    public void testBackImageCapture() throws Exception {
        Instrumentation inst = getInstrumentation();
        Intent intent = new Intent();

        intent.setClass(getInstrumentation().getTargetContext(), CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRAS_CAMERA_FACING,
                android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK);
        Activity act = inst.startActivitySync(intent);
        Thread.sleep(WAIT_FOR_SWITCH_CAMERA);
        captureImages("Back Camera Image Capture\n", inst);
        act.finish();
    }

    @LargeTest
    public void testFrontImageCapture() throws Exception {
        Instrumentation inst = getInstrumentation();
        Intent intent = new Intent();

        intent.setClass(getInstrumentation().getTargetContext(), CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRAS_CAMERA_FACING,
                android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT);
        Activity act = inst.startActivitySync(intent);
        Thread.sleep(WAIT_FOR_SWITCH_CAMERA);
        captureImages("Front Camera Image Capture\n", inst);
        act.finish();
    }
}
