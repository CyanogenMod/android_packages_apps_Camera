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
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.AutoFocusMoveCallback;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.view.SurfaceHolder;

import java.io.IOException;

public class CameraDevice {
    private Camera mCamera;

    public CameraDevice() {
    }

    public CameraDevice(Camera camera) {
        mCamera = camera;
    }

    public android.hardware.Camera getCamera() {
        return mCamera;
    }

    public void release() {
        mCamera.release();
    }

    public void unlock() {
        mCamera.unlock();
    }

    public void lock() {
        mCamera.lock();
    }

    public void reconnect() throws IOException {
        mCamera.reconnect();
    }

    public void setPreviewDisplay(SurfaceHolder holder) throws IOException {
        mCamera.setPreviewDisplay(holder);
    }

    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
        mCamera.setPreviewTexture(surfaceTexture);
    }

    public void startPreview() {
        mCamera.startPreview();
    }

    public void stopPreview() {
        mCamera.stopPreview();
    }

    public void setPreviewCallbackWithBuffer(PreviewCallback cb) {
        mCamera.setPreviewCallbackWithBuffer(cb);
    }

    public void addCallbackBuffer(byte[] callbackBuffer) {
        mCamera.addCallbackBuffer(callbackBuffer);
    }

    public void autoFocus(AutoFocusCallback cb) {
        mCamera.autoFocus(cb);
    }

    public void cancelAutoFocus() {
        mCamera.cancelAutoFocus();
    }

    public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
        mCamera.setAutoFocusMoveCallback(cb);
    }

    public void takePicture(ShutterCallback shutter, PictureCallback raw,
            PictureCallback postview, PictureCallback jpeg) {
        mCamera.takePicture(shutter, raw, postview, jpeg);
    }

    public void startSmoothZoom(int value) {
        mCamera.startSmoothZoom(value);
    }

    public void stopSmoothZoom() {
        mCamera.stopSmoothZoom();
    }

    public void setDisplayOrientation(int degrees) {
        mCamera.setDisplayOrientation(degrees);
    }

    public void setZoomChangeListener(OnZoomChangeListener listener) {
        mCamera.setZoomChangeListener(listener);
    }

    public void setFaceDetectionListener(FaceDetectionListener listener) {
        mCamera.setFaceDetectionListener(listener);
    }

    public void startFaceDetection() {
        mCamera.startFaceDetection();
    }

    public void stopFaceDetection() {
        mCamera.stopFaceDetection();
    }

    public void setErrorCallback(ErrorCallback cb) {
        mCamera.setErrorCallback(cb);
    }

    public void setParameters(Parameters params) {
        mCamera.setParameters(params);
    }

    public Parameters getParameters() {
        return mCamera.getParameters();
    }
}
