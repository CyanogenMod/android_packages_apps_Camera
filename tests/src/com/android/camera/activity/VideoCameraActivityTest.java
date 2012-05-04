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

import com.android.camera.VideoCamera;

import android.test.suitebuilder.annotation.LargeTest;

public class VideoCameraActivityTest extends CameraTestCase <VideoCamera> {
    public VideoCameraActivityTest() {
        super(VideoCamera.class);
    }

    @LargeTest
    public void testFailToConnect() throws Exception {
        super.internalTestFailToConnect();
    }

    @LargeTest
    public void testRestoreDefault() throws Exception {
        super.internalTestRestoreDefault();
    }

    @LargeTest
    public void testOneCamera() throws Exception {
        super.internalTestOneCamera();
    }

    @LargeTest
    public void testSwitchCamera() throws Exception {
        super.internalTestSwitchCamera();
    }
}
