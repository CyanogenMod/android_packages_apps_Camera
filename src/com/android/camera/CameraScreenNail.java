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

import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.ScreenNailHolder;
import com.android.gallery3d.ui.SurfaceTextureScreenNail;

/*
 * This is a ScreenNail which can displays camera preview.
 */
public class CameraScreenNail extends SurfaceTextureScreenNail {
    private boolean mVisible;
    private int mX, mY;
    private RenderListener mRenderListener;
    private PositionChangedListener mPositionChangedListener;

    public interface RenderListener {
        void requestRender();
    }

    public interface PositionChangedListener {
        public void onPositionChanged(int x, int y, boolean visible);
    }

    public CameraScreenNail(RenderListener listener) {
        mRenderListener = listener;
    }

    public void setPositionChangedListener(PositionChangedListener listener) {
        mPositionChangedListener = listener;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        if (getSurfaceTexture() == null) return;
        if (!mVisible) setVisibility(true);
        super.draw(canvas, x, y, width, height);
        if (mX != x || mY != y) {
            mX = x;
            mY = y;
            if (mPositionChangedListener != null) {
                mPositionChangedListener.onPositionChanged(x, y, mVisible);
            }
        }
    }

    @Override
    public synchronized void noDraw() {
        setVisibility(false);
    }

    @Override
    public synchronized void pauseDraw() {
        setVisibility(false);
    }

    @Override
    public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mVisible) {
            // We need to ask for re-render if the SurfaceTexture receives a new
            // frame.
            mRenderListener.requestRender();
        }
    }

    private void setVisibility(boolean visible) {
        mVisible = visible;
        if (mPositionChangedListener != null) {
            mPositionChangedListener.onPositionChanged(mX, mY, mVisible);
        }
    }
}

// This holds a CameraScreenNail, so we can pass it to a PhotoPage.
class CameraScreenNailHolder extends ScreenNailHolder implements CameraScreenNail.RenderListener {
    private GalleryActivity mActivity;
    private CameraScreenNail mCameraScreenNail;

    public CameraScreenNailHolder(GalleryActivity activity) {
        mActivity = activity;
    }

    public CameraScreenNail getCameraScreenNail() {
        return mCameraScreenNail;
    }

    @Override
    public void requestRender() {
        mActivity.getGLRoot().requestRender();
    }

    @Override
    public ScreenNail attach() {
        mCameraScreenNail = new CameraScreenNail(this);
        return mCameraScreenNail;
    }

    @Override
    public void detach() {
        mCameraScreenNail = null;
    }
}
