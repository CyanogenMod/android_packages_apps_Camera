/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.camera.panorama;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

class Preview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "Preview";
    private android.hardware.Camera mCameraDevice;

    public Preview(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void setCameraDevice(android.hardware.Camera camera) {
        mCameraDevice = camera;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mCameraDevice != null) {
            try {
                mCameraDevice.setPreviewDisplay(holder);
            } catch (Throwable ex) {
                throw new RuntimeException("setPreviewDisplay failed", ex);
            }
        }
    }
}
