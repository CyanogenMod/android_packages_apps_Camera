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

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.Log;

public class MosaicRendererSurfaceViewRenderer implements GLSurfaceView.Renderer
{
    @Override
    public void onDrawFrame(GL10 gl) {
        MosaicRenderer.step();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(TAG, "Renderer: onSurfaceChanged");
        MosaicRenderer.reset(width, height);
        Log.i(TAG, "Renderer: onSurfaceChanged");
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mTextureID = MosaicRenderer.init();

        mActivity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mActivity.createSurfaceTextureAndStartPreview(mTextureID);
                setSurfaceTexture(mActivity.getSurfaceTexture());
            }
        });
    }

    public void setReady() {
        MosaicRenderer.ready();
    }

    public void preprocess() {
        MosaicRenderer.preprocess(mSTMatrix);
    }

    public void transferGPUtoCPU() {
        MosaicRenderer.transferGPUtoCPU();
    }

    public void setWarping(boolean flag) {
        MosaicRenderer.setWarping(flag);
    }

    public void updateSurfaceTexture() {
        mSurface.updateTexImage();
        mSurface.getTransformMatrix(mSTMatrix);
    }

    public void setUIObject(Activity activity) {
        mActivity = (PanoramaActivity)activity;
    }

    public int getTextureID() {
        return mTextureID;
    }

    public void setSurfaceTexture(SurfaceTexture surface) {
        mSurface = surface;
    }

    private float[] mSTMatrix = new float[16];
    private int mTextureID;

    private PanoramaActivity mActivity;

    private static String TAG = "MosaicRendererSurfaceViewRenderer";

    private SurfaceTexture mSurface;
}
