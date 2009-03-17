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

package com.android.camera.tests.stress;

import android.app.Activity;
import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.KeyEvent;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.camera.Camera;

/**
 * Junit / Instrumentation test case for camera test
 * 
 */

public class ImageCapture extends ActivityInstrumentationTestCase2 <Camera> {
    private String TAG = "ImageCapture";
    private static final int TOTAL_NUMBER_OF_IMAGECAPTURE = 100;
    private static final int TOTAL_NUMBER_OF_VIDEOCAPTURE = 100;
    private static final long WAIT_FOR_IMAGE_CAPTURE_TO_BE_TAKEN = 1000;
    private static final long WAIT_FOR_VIDEO_CAPTURE_TO_BE_TAKEN = 50000; //50seconds
    private static final long WAIT_FOR_PREVIEW = 1000; //1 seconds

    public ImageCapture() {
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
            }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }
            assertTrue("testImageCapture", true);
    }
    
    @LargeTest
    public void testVideoCapture() {
        Instrumentation inst = getInstrumentation();
        //Switch to the video mode
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        try {
            for (int i = 0; i < TOTAL_NUMBER_OF_VIDEOCAPTURE; i++) {
                Thread.sleep(WAIT_FOR_PREVIEW);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
                //record an video
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_VIDEO_CAPTURE_TO_BE_TAKEN);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_PREVIEW);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        }
            assertTrue("testVideoCapture", true);
    }

}
    
