/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.os.Bundle;
import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;
import junit.framework.TestSuite;

public class CameraStressTestRunner extends InstrumentationTestRunner {

    // Default recorder settings
    public static int mVideoDuration = 20000; // set default to 20 seconds
    public static int mVideoIterations = 100; // set default to 100 videos
    public static int mImageIterations = 100; // set default to 100 images

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(ImageCapture.class);
        suite.addTestSuite(VideoCapture.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return CameraStressTestRunner.class.getClassLoader();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        String video_iterations = (String) icicle.get("video_iterations");
        String image_iterations = (String) icicle.get("image_iterations");
        String video_duration = (String) icicle.get("video_duration");

        if ( video_iterations != null ) {
            mVideoIterations = Integer.parseInt(video_iterations);
        }
        if ( image_iterations != null) {
            mImageIterations = Integer.parseInt(image_iterations);
        }
        if ( video_duration != null) {
            mVideoDuration = Integer.parseInt(video_duration);
        }
    }
}
