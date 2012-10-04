/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Instrumentation;
import android.os.Environment;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;
import com.android.camera.CameraActivity;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Junit / Instrumentation test case for measuring camera shot to shot latency
 */
public class ShotToShotLatency extends ActivityInstrumentationTestCase2<CameraActivity> {
    private String TAG = "ShotToShotLatency";
    private static final int TOTAL_NUMBER_OF_SNAPSHOTS = 250;
    private static final long SNAPSHOT_WAIT = 1000;
    private static final String CAMERA_TEST_OUTPUT_FILE =
            Environment.getExternalStorageDirectory().toString() + "/mediaStressOut.txt";
    private static final String CAMERA_IMAGE_DIRECTORY =
            Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera/";

    public ShotToShotLatency() {
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

    private void cleanupLatencyImages() {
        try {
            File sdcard = new File(CAMERA_IMAGE_DIRECTORY);
            File[] pics = null;
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jpg");
                }
            };
            pics = sdcard.listFiles(filter);
            for (File f : pics) {
                f.delete();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security manager access violation: " + e.toString());
        }
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep InterruptedException " + e.toString());
        }
    }

    @LargeTest
    public void testShotToShotLatency() {
        long sigmaOfDiffFromMeanSquared = 0;
        double mean = 0;
        double standardDeviation = 0;
        ArrayList<Long> captureTimes = new ArrayList<Long>();
        ArrayList<Long> latencyTimes = new ArrayList<Long>();

        Log.v(TAG, "start testShotToShotLatency test");
        Instrumentation inst = getInstrumentation();

        // Generate data points
        for (int i = 0; i < TOTAL_NUMBER_OF_SNAPSHOTS; i++) {
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            sleep(SNAPSHOT_WAIT);
            CameraActivity c = getActivity();
            if (c.getCaptureStartTime() > 0) {
                captureTimes.add(c.getCaptureStartTime());
            }
        }

        // Calculate latencies
        for (int j = 1; j < captureTimes.size(); j++) {
            latencyTimes.add(captureTimes.get(j) - captureTimes.get(j - 1));
        }

        // Crunch numbers
        for (long dataPoint : latencyTimes) {
            mean += (double) dataPoint;
        }
        mean /= latencyTimes.size();

        for (long dataPoint : latencyTimes) {
            sigmaOfDiffFromMeanSquared += (dataPoint - mean) * (dataPoint - mean);
        }
        standardDeviation = Math.sqrt(sigmaOfDiffFromMeanSquared / latencyTimes.size());

        // Report statistics
        File outFile = new File(CAMERA_TEST_OUTPUT_FILE);
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(outFile, true));
            output.write("Shot to shot latency - mean: " + mean + "\n");
            output.write("Shot to shot latency - standard deviation: " + standardDeviation + "\n");
            cleanupLatencyImages();
        } catch (IOException e) {
            Log.e(TAG, "testShotToShotLatency IOException writing to log " + e.toString());
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing file: " + e.toString());
            }
        }
    }
}
