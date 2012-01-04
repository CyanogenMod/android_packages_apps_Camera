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
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.OnZoomChangeListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.view.SurfaceHolder;

import java.io.IOException;

public class RealCamera implements CameraDevice {
    private Camera mCamera;

    public RealCamera(Camera camera) {
        mCamera = camera;
    }

    @Override
    public android.hardware.Camera getCamera() {
        return mCamera;
    }

    @Override
    public void release() {
        mCamera.release();
    }

    @Override
    public void unlock() {
        mCamera.unlock();
    }

    @Override
    public void lock() {
        mCamera.lock();
    }

    @Override
    public void reconnect() throws IOException {
        mCamera.reconnect();
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder holder) throws IOException {
        mCamera.setPreviewDisplay(holder);
    }

    @Override
    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
        mCamera.setPreviewTexture(surfaceTexture);
    }

    @Override
    public void startPreview() {
        mCamera.startPreview();
    }

    @Override
    public void stopPreview() {
        mCamera.stopPreview();
    }

    @Override
    public void setPreviewCallbackWithBuffer(PreviewCallback cb) {
        mCamera.setPreviewCallbackWithBuffer(cb);
    }

    @Override
    public void addCallbackBuffer(byte[] callbackBuffer) {
        mCamera.addCallbackBuffer(callbackBuffer);
    }

    @Override
    public void autoFocus(AutoFocusCallback cb) {
        mCamera.autoFocus(cb);
    }

    @Override
    public void cancelAutoFocus() {
        mCamera.cancelAutoFocus();
    }

    @Override
    public void takePicture(ShutterCallback shutter, PictureCallback raw,
            PictureCallback postview, PictureCallback jpeg) {
        mCamera.takePicture(shutter, raw, postview, jpeg);
    }

    @Override
    public void startSmoothZoom(int value) {
        mCamera.startSmoothZoom(value);
    }

    @Override
    public void stopSmoothZoom() {
        mCamera.stopSmoothZoom();
    }

    @Override
    public void setDisplayOrientation(int degrees) {
        mCamera.setDisplayOrientation(degrees);
    }

    @Override
    public void setZoomChangeListener(OnZoomChangeListener listener) {
        mCamera.setZoomChangeListener(listener);
    }

    @Override
    public void setFaceDetectionListener(FaceDetectionListener listener) {
        mCamera.setFaceDetectionListener(listener);
    }

    @Override
    public void startFaceDetection() {
        mCamera.startFaceDetection();
    }

    @Override
    public void stopFaceDetection() {
        mCamera.stopFaceDetection();
    }

    @Override
    public void setErrorCallback(ErrorCallback cb) {
        mCamera.setErrorCallback(cb);
    }

    @Override
    public void setParameters(Parameters params) {
        mCamera.setParameters(params);
    }

    @Override
    public Parameters getParameters() {
        return mCamera.getParameters();
    }
}
