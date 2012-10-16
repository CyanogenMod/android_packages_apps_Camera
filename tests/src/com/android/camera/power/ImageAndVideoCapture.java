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

package com.android.camera.power;

import com.android.camera.CameraActivity;

import android.app.Instrumentation;
import android.provider.MediaStore;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;
import android.content.Intent;
/**
 * Junit / Instrumentation test case for camera power measurement
 *
 * Running the test suite:
 *
 * adb shell am instrument \
 *    -e com.android.camera.power.ImageAndVideoCapture \
 *    -w com.android.camera.tests/android.test.InstrumentationTestRunner
 *
 */

public class ImageAndVideoCapture extends ActivityInstrumentationTestCase2 <CameraActivity> {
    private String TAG = "ImageAndVideoCapture";
    private static final int TOTAL_NUMBER_OF_IMAGECAPTURE = 5;
    private static final int TOTAL_NUMBER_OF_VIDEOCAPTURE = 5;
    private static final long WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN = 1500;   //1.5 sedconds
    private static final long WAIT_FOR_VIDEO_CAPTURE_TO_BE_TAKEN = 10000; //10 seconds
    private static final long WAIT_FOR_PREVIEW = 1500; //1.5 seconds
    private static final long WAIT_FOR_STABLE_STATE = 2000; //2 seconds

    public ImageAndVideoCapture() {
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
    public void testLaunchCamera() {
        // This test case capture the baseline for the image preview.
        try {
            Thread.sleep(WAIT_FOR_STABLE_STATE);
        } catch (Exception e) {
            Log.v(TAG, "Got exception", e);
            assertTrue("testImageCaptureDoNothing", false);
        }
    }

    @LargeTest
    public void testCapture5Image() {
        // This test case will use the default camera setting
        Instrumentation inst = getInstrumentation();
        try {
            for (int i = 0; i < TOTAL_NUMBER_OF_IMAGECAPTURE; i++) {
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
            }
            Thread.sleep(WAIT_FOR_STABLE_STATE);
        } catch (Exception e) {
            Log.v(TAG, "Got exception", e);
            assertTrue("testImageCapture", false);
        }
    }

    @LargeTest
    public void testCapture5Videos() {
        // This test case will use the default camera setting
        Instrumentation inst = getInstrumentation();
        try {
            // Switch to the video mode
            Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
            intent.setClass(getInstrumentation().getTargetContext(),
                    CameraActivity.class);
            getActivity().startActivity(intent);
            for (int i = 0; i < TOTAL_NUMBER_OF_VIDEOCAPTURE; i++) {
                Thread.sleep(WAIT_FOR_PREVIEW);
                // record a video
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_VIDEO_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_PREVIEW);
            }
            Thread.sleep(WAIT_FOR_STABLE_STATE);
        } catch (Exception e) {
            Log.v(TAG, "Got exception", e);
            assertTrue("testVideoCapture", false);
        }
    }
}
