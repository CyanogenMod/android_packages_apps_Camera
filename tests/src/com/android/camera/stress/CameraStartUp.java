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

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Environment;
import android.provider.MediaStore;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.io.FileWriter;
import java.io.BufferedWriter;

/**
 * Test cases to measure the camera and video recorder startup time.
 */
public class CameraStartUp extends InstrumentationTestCase {

    private static final int TOTAL_NUMBER_OF_STARTUP = 20;

    private String TAG = "CameraStartUp";
    private static final String CAMERA_TEST_OUTPUT_FILE =
            Environment.getExternalStorageDirectory().toString() + "/mediaStressOut.txt";
    private static int WAIT_TIME_FOR_PREVIEW = 1500; //1.5 second

    private long launchCamera() {
        long startupTime = 0;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(getInstrumentation().getTargetContext(), CameraActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            long beforeStart = System.currentTimeMillis();
            Instrumentation inst = getInstrumentation();
            Activity cameraActivity = inst.startActivitySync(intent);
            long cameraStarted = System.currentTimeMillis();
            Thread.sleep(WAIT_TIME_FOR_PREVIEW);
            cameraActivity.finish();
            startupTime = cameraStarted - beforeStart;
            Thread.sleep(1000);
            Log.v(TAG, "camera startup time: " + startupTime);
        } catch (Exception e) {
            Log.v(TAG, "Got exception", e);
            fail("Fails to get the output file");
        }
        return startupTime;
    }

    private long launchVideo() {
        long startupTime = 0;

        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
            intent.setClass(getInstrumentation().getTargetContext(), CameraActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            long beforeStart = System.currentTimeMillis();
            Instrumentation inst = getInstrumentation();
            Activity recorderActivity = inst.startActivitySync(intent);
            long cameraStarted = System.currentTimeMillis();
            recorderActivity.finish();
            startupTime = cameraStarted - beforeStart;
            Log.v(TAG, "Video Startup Time = " + startupTime);
            // wait for 1s to make sure it reach a clean stage
            Thread.sleep(WAIT_TIME_FOR_PREVIEW);
            Log.v(TAG, "video startup time: " + startupTime);
        } catch (Exception e) {
            Log.v(TAG, "Got exception", e);
            fail("Fails to launch video output file");
        }
        return startupTime;
    }

    private void writeToOutputFile(long totalStartupTime,
            String individualStartupTime, boolean firstStartUp, String Type) throws Exception {
        // TODO (yslau) : Need to integrate the output data with central
        // dashboard
        try {
            FileWriter fstream = null;
            fstream = new FileWriter(CAMERA_TEST_OUTPUT_FILE, true);
            BufferedWriter out = new BufferedWriter(fstream);
            if (firstStartUp) {
                out.write("First " + Type + " Startup: " + totalStartupTime + "\n");
            } else {
                long averageStartupTime = totalStartupTime / (TOTAL_NUMBER_OF_STARTUP -1);
                out.write(Type + "startup time: " + "\n");
                out.write("Number of loop: " + (TOTAL_NUMBER_OF_STARTUP -1)  + "\n");
                out.write(individualStartupTime + "\n\n");
                out.write(Type + " average startup time: " + averageStartupTime + " ms\n\n");
            }
            out.close();
            fstream.close();
        } catch (Exception e) {
            fail("Camera write output to file");
        }
    }

    @LargeTest
    public void testLaunchVideo() throws Exception {
        String individualStartupTime;
        individualStartupTime = "Individual Video Startup Time = ";
        long totalStartupTime = 0;
        long startupTime = 0;
        for (int i = 0; i < TOTAL_NUMBER_OF_STARTUP; i++) {
            if (i == 0) {
                // Capture the first startup time individually
                long firstStartUpTime = launchVideo();
                writeToOutputFile(firstStartUpTime, "na", true, "Video");
            } else {
                startupTime = launchVideo();
                totalStartupTime += startupTime;
                individualStartupTime += startupTime + " ,";
            }
        }
        Log.v(TAG, "totalStartupTime =" + totalStartupTime);
        writeToOutputFile(totalStartupTime, individualStartupTime, false, "Video");
    }

    @LargeTest
    public void testLaunchCamera() throws Exception {
        String individualStartupTime;
        individualStartupTime = "Individual Camera Startup Time = ";
        long totalStartupTime = 0;
        long startupTime = 0;
        for (int i = 0; i < TOTAL_NUMBER_OF_STARTUP; i++) {
            if (i == 0) {
                // Capture the first startup time individually
                long firstStartUpTime = launchCamera();
                writeToOutputFile(firstStartUpTime, "na", true, "Camera");
            } else {
                startupTime = launchCamera();
                totalStartupTime += startupTime;
                individualStartupTime += startupTime + " ,";
            }
        }
        Log.v(TAG, "totalStartupTime =" + totalStartupTime);
        writeToOutputFile(totalStartupTime,
                individualStartupTime, false, "Camera");
    }
}
