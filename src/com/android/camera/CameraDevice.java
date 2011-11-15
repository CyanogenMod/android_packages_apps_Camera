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

package com.android.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.view.SurfaceHolder;

import java.io.IOException;

public interface CameraDevice {
    public android.hardware.Camera getCamera();
    public void release();
    public void unlock();
    public void lock();
    public void reconnect() throws IOException;
    public void setPreviewDisplay(SurfaceHolder holder) throws IOException;
    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException;
    public void startPreview();
    public void stopPreview();
    public void setPreviewCallbackWithBuffer(PreviewCallback cb);
    public void addCallbackBuffer(byte[] callbackBuffer);
    public void autoFocus(AutoFocusCallback cb);
    public void cancelAutoFocus();
    public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb);
    public void takePicture(ShutterCallback shutter, PictureCallback raw,
            PictureCallback postview, PictureCallback jpeg);
    public void startSmoothZoom(int value);
    public void stopSmoothZoom();
    public void setDisplayOrientation(int degrees);
    public void setZoomChangeListener(OnZoomChangeListener listener);
    public void setFaceDetectionListener(FaceDetectionListener listener);
    public void startFaceDetection();
    public void stopFaceDetection();
    public void setErrorCallback(ErrorCallback cb);
    public void setParameters(Parameters params);
    public Parameters getParameters();
}
