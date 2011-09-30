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

import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MosaicRendererSurfaceViewRenderer implements GLSurfaceView.Renderer
{
    private static final String TAG = "MosaicRendererSurfaceViewRenderer";

    private MosaicSurfaceCreateListener mSurfaceCreateListener;

    /** A callback to be called when the surface is created */
    public interface MosaicSurfaceCreateListener {
        public void onMosaicSurfaceCreated(final int surface);
        public void onMosaicSurfaceChanged();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        MosaicRenderer.step();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        MosaicRenderer.reset(width, height);
        Log.i(TAG, "Renderer: onSurfaceChanged");
        if (mSurfaceCreateListener != null) {
            mSurfaceCreateListener.onMosaicSurfaceChanged();
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        if (mSurfaceCreateListener != null) {
            mSurfaceCreateListener.onMosaicSurfaceCreated(MosaicRenderer.init());
        }
    }

    public void setMosaicSurfaceCreateListener(MosaicSurfaceCreateListener listener) {
        mSurfaceCreateListener = listener;
    }

    public void setReady() {
        MosaicRenderer.ready();
    }

    public void preprocess(float[] transformMatrix) {
        MosaicRenderer.preprocess(transformMatrix);
    }

    public void transferGPUtoCPU() {
        MosaicRenderer.transferGPUtoCPU();
    }

    public void setWarping(boolean flag) {
        MosaicRenderer.setWarping(flag);
    }
}
