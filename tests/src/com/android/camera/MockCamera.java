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
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.view.SurfaceHolder;

import java.io.IOException;

public class MockCamera implements CameraDevice {
    private static Parameters mParameters;

    public MockCamera() {
    }

    @Override
    public android.hardware.Camera getCamera() {
        return null;
    }

    @Override
    public void release() {
    }

    @Override
    public void unlock() {
    }

    @Override
    public void lock() {
    }

    @Override
    public void reconnect() throws IOException {
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder holder) throws IOException {
    }

    @Override
    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
    }

    @Override
    public void startPreview() {
    }

    @Override
    public void stopPreview() {
    }

    @Override
    public void setPreviewCallbackWithBuffer(PreviewCallback cb) {
    }

    @Override
    public void addCallbackBuffer(byte[] callbackBuffer) {
    }

    @Override
    public void autoFocus(AutoFocusCallback cb) {
    }

    @Override
    public void cancelAutoFocus() {
    }

    @Override
    public void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
    }

    @Override
    public void takePicture(ShutterCallback shutter, PictureCallback raw,
            PictureCallback postview, PictureCallback jpeg) {
    }

    @Override
    public void startSmoothZoom(int value) {
    }

    @Override
    public void stopSmoothZoom() {
    }

    @Override
    public void setDisplayOrientation(int degrees) {
    }

    @Override
    public void setZoomChangeListener(OnZoomChangeListener listener) {
    }

    @Override
    public void setFaceDetectionListener(FaceDetectionListener listener) {
    }

    @Override
    public void startFaceDetection() {
    }

    @Override
    public void stopFaceDetection() {
    }

    @Override
    public void setErrorCallback(ErrorCallback cb) {
    }

    @Override
    public void setParameters(Parameters params) {
    }

    @Override
    public Parameters getParameters() {
        if (mParameters == null) mParameters = buildParameters();
        return mParameters;
    }

    private Parameters buildParameters() {
        Parameters p = android.hardware.Camera.getEmptyParameters();
        p.unflatten("preview-format-values=yuv420sp,yuv420p,yuv422i-yuyv,yuv420p;" +
                "preview-format=yuv420sp;" +
                "preview-size-values=800x480;preview-size=800x480;" +
                "picture-size-values=2592x1944;picture-size=2592x1944" +
                "jpeg-thumbnail-size-values=320x240,0x0;jpeg-thumbnail-width=320;jpeg-thumbnail-height=240;" +
                "jpeg-thumbnail-quality=60;jpeg-quality=95;" +
                "preview-frame-rate-values=30,15;preview-frame-rate=30;" +
                "focus-mode-values=continuous-video,auto,macro,infinity,continuous-picture;focus-mode=auto" +
                "preview-fps-range-values=(15000,30000);preview-fps-range=15000,30000;" +
                "scene-mode-values=auto,action,night;scene-mode=auto;" +
                "flash-mode-values=off,on,auto,torch;flash-mode=off;" +
                "whitebalance-values=auto,daylight,fluorescent,incandescent;whitebalance=auto;" +
                "effect-values=none,mono,sepia;effect=none;" +
                "zoom-supported=true;zoom-ratios=100,200,400;max-zoom=2;" +
                "picture-format-values=jpeg;picture-format=jpeg;" +
                "min-exposure-compensation=-30;max-exposure-compensation=30;" +
                "exposure-compensation=0;exposure-compensation-step=0.1");
        return p;
    }
}
