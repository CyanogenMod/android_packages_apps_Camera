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
import com.android.camera.VideoCamera;

/**
 * Junit / Instrumentation test case for camera test
 * 
 */

public class SwitchPreview extends ActivityInstrumentationTestCase2 <VideoCamera>{
    private String TAG = "SwitchPreview";
    private static final int TOTAL_NUMBER_OF_SWITCHING = 200;
    private static final long WAIT_FOR_PREVIEW = 2000;
    
    
    public SwitchPreview() {
        super("com.android.camera", VideoCamera.class);      
    }
    
    @Override 
    protected void setUp() throws Exception {  
        getActivity();
        super.setUp();
    }
    
    @Override 
    protected void tearDown() throws Exception {   
        getActivity().finish();
        super.tearDown();              
    }
        
    @LargeTest
    public void testSwitchMode() {
        //Switching the video and the video recorder mode
        Instrumentation inst = getInstrumentation();
        try{
            for (int i=0; i< TOTAL_NUMBER_OF_SWITCHING; i++) {
                Thread.sleep(WAIT_FOR_PREVIEW);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                Thread.sleep(WAIT_FOR_PREVIEW);
            }
        } catch (Exception e){
            Log.v(TAG, e.toString());
        }
            assertTrue("testSwitchMode",true);
    }
}
    
