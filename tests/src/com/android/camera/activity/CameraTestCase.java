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

import android.app.Activity;
import android.app.Instrumentation;
import android.hardware.Camera.CameraInfo;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.camera.CameraHolder;
import com.android.camera.MockCamera;
import com.android.camera.R;

public class CameraTestCase<T extends Activity> extends ActivityInstrumentationTestCase2<T> {
    protected CameraInfo mCameraInfo[];
    protected MockCamera mMockCamera[];
    protected CameraInfo mOneCameraInfo[];
    protected MockCamera mOneMockCamera[];

    public CameraTestCase(Class<T> activityClass) {
        super(activityClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCameraInfo = new CameraInfo[2];
        mCameraInfo[0] = new CameraInfo();
        mCameraInfo[0].facing = CameraInfo.CAMERA_FACING_BACK;
        mCameraInfo[1] = new CameraInfo();
        mCameraInfo[1].facing = CameraInfo.CAMERA_FACING_FRONT;
        mMockCamera = new MockCamera[2];
        mMockCamera[0] = new MockCamera();
        mMockCamera[1] = new MockCamera();

        mOneCameraInfo = new CameraInfo[1];
        mOneCameraInfo[0] = new CameraInfo();
        mOneCameraInfo[0].facing = CameraInfo.CAMERA_FACING_BACK;
        mOneMockCamera = new MockCamera[1];
        mOneMockCamera[0] = new MockCamera();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        CameraHolder.injectMockCamera(null,  null);
    }

    protected void internalTestFailToConnect() throws Exception {
        CameraHolder.injectMockCamera(mCameraInfo, null);

        getActivity();
        Instrumentation inst = getInstrumentation();
        inst.waitForIdleSync();
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); // close dialog
    }

    protected void internalTestRestoreDefault() throws Exception {
        CameraHolder.injectMockCamera(mCameraInfo, mMockCamera);

        getActivity();
        getInstrumentation().waitForIdleSync();
        performClick(R.id.second_level_indicator);
        performClick(R.id.other_setting_indicator);
        performClick(R.id.restore_default);
        performClick(R.id.rotate_dialog_button1);
    }

    protected void internalTestOneCamera() throws Exception {
        CameraHolder.injectMockCamera(mOneCameraInfo, mOneMockCamera);

        getActivity();
        getInstrumentation().waitForIdleSync();
        assertViewNotExist(R.id.camera_picker);
    }

    protected void performClick(final int id) {
        Activity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        assertNotNull(activity.findViewById(id));
        Instrumentation inst = getInstrumentation();
        inst.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View v = getActivity().findViewById(id);
                float x = (v.getLeft() + v.getRight()) / 2;
                float y = (v.getTop() + v.getBottom()) / 2;
                MotionEvent down = MotionEvent.obtain(0, 0,
                        MotionEvent.ACTION_DOWN, x, y, 0, 0, 0, 0, 0, 0, 0);
                MotionEvent up = MotionEvent.obtain(0, 0,
                        MotionEvent.ACTION_UP, x, y, 0, 0, 0, 0, 0, 0, 0);
                View parent = (View) v.getParent();
                parent.dispatchTouchEvent(down);
                parent.dispatchTouchEvent(up);
            }
        });
        inst.waitForIdleSync();
    }

    protected void assertViewNotExist(int id) {
        Activity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        assertNull(activity.findViewById(id));
    }
}
