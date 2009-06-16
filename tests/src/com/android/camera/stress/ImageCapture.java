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
import java.io.FileWriter;
import java.io.BufferedWriter;

/**
 * Junit / Instrumentation test case for camera test
 *
 * Running the test suite:
 *
 * adb shell am instrument \
 *    -e class com.android.camera.stress.ImageCapture \
 *    -w com.android.camera.tests/com.android.camera.CameraStressTestRunner
 *
 */

public class ImageCapture extends ActivityInstrumentationTestCase2 <Camera> {
    private String TAG = "ImageCapture";
    private static final int TOTAL_NUMBER_OF_IMAGECAPTURE = 100;
    private static final int TOTAL_NUMBER_OF_VIDEOCAPTURE = 100;
    private static final long WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN = 1000;
    private static final long WAIT_FOR_VIDEO_CAPTURE_TO_BE_TAKEN = 50000; //50seconds
    private static final long WAIT_FOR_PREVIEW = 1000; //1 seconds

    private static final String CAMERA_TEST_OUTPUT_FILE = "/sdcard/mediaStressOut.txt";
    private BufferedWriter mOut;
    private FileWriter mfstream;

    public ImageCapture() {
        super("com.android.camera", Camera.class);
    }

    @Override
    protected void setUp() throws Exception {
        getActivity();
        prepareOutputFile();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        closeOutputFile();
        super.tearDown();
    }

    private void prepareOutputFile(){
        try{
            mfstream = new FileWriter(CAMERA_TEST_OUTPUT_FILE, true);
            mOut = new BufferedWriter(mfstream);
        } catch (Exception e){
            assertTrue("ImageCapture open output",false);
        }
    }

    private void closeOutputFile() {
        try {
            mOut.write("\n");
            mOut.close();
            mfstream.close();
        } catch (Exception e) {
            assertTrue("ImageCapture close output", false);
        }
    }

    @LargeTest
    public void testImageCapture() {
        //TODO(yslau): Need to integrate the outoput with the central dashboard,
        //write to a txt file as a temp solution
        Instrumentation inst = getInstrumentation();

        try {
            mOut.write("Video Camera Capture\n");
            mOut.write("No of loops :" + TOTAL_NUMBER_OF_VIDEOCAPTURE + "\n");
            mOut.write("loop: ");

            for (int i = 0; i < TOTAL_NUMBER_OF_IMAGECAPTURE; i++) {
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN);
                mOut.write(" ," + i);
            }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
            assertTrue("testImageCapture", false);
        }
        assertTrue("testImageCapture", true);
    }

    @LargeTest
    public void testVideoCapture() {
        //TODO(yslau): Need to integrate the output with the central dashboard,
        //write to a txt file as a temp solution
        Instrumentation inst = getInstrumentation();

        try {
            mOut.write("Video Camera Capture\n");
            mOut.write("No of loops :" + TOTAL_NUMBER_OF_VIDEOCAPTURE + "\n");
            mOut.write("loop: ");
            // Switch to the video mode
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);

            for (int i = 0; i < TOTAL_NUMBER_OF_VIDEOCAPTURE; i++) {
                Thread.sleep(WAIT_FOR_PREVIEW);
                // record a video
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_VIDEO_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_PREVIEW);
                mOut.write(" ," + i);
            }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
            fail("Fails to capture video");
        }
    }

}

