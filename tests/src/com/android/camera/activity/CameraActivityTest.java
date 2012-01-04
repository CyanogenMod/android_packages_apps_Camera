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

package com.android.camera.activity;

import com.android.camera.Camera;
import com.android.camera.CameraDevice;
import com.android.camera.CameraHolder;
import com.android.camera.IntentExtras;

import android.app.Instrumentation;
import android.hardware.Camera.CameraInfo;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;

public class CameraActivityTest extends ActivityInstrumentationTestCase2 <Camera> {
    private static final String TAG = "CameraActivityTest";
    private CameraInfo mCameraInfo[];

    public CameraActivityTest() {
        super(Camera.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCameraInfo = new CameraInfo[2];
        mCameraInfo[0] = new CameraInfo();
        mCameraInfo[0].facing = CameraInfo.CAMERA_FACING_BACK;
        mCameraInfo[1] = new CameraInfo();
        mCameraInfo[1].facing = CameraInfo.CAMERA_FACING_FRONT;
    }

    @Override
    protected void tearDown() throws Exception {
        CameraHolder.injectMockCamera(null,  null);
    }

    @LargeTest
    public void testFailToConnect() throws Exception {
        CameraHolder.injectMockCamera(mCameraInfo, null);

        getActivity();
        Instrumentation inst = getInstrumentation();
        inst.waitForIdleSync();
    }
}
