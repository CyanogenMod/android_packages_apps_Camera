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

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.RectF;

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
    // The original draw coordinates.
    private int mOriginalX, mOriginalY, mOriginalWidth, mOriginalHeight;
    // The actual draw coordinates.
    private int mTransformedX, mTransformedY, mTransformedWidth, mTransformedHeight;
    // The matrix to transform the original coordinates to actual coordinates.
    private Matrix mMatrix = new Matrix();
    private RectF mRect = new RectF();
    private RenderListener mRenderListener;
    private PositionChangedListener mPositionChangedListener;

    public interface RenderListener {
        void requestRender();
    }

    public interface PositionChangedListener {
        public void onPositionChanged(int x, int y, int width, int height, boolean visible);
    }

    public CameraScreenNail(RenderListener listener) {
        mRenderListener = listener;
    }

    public void setPositionChangedListener(PositionChangedListener listener) {
        mPositionChangedListener = listener;
    }

    // Set the matrix to scale by scaleRatio, with a pivot point at (px, py).
    // Then translate by (translateX, translateY).
    public void setMatrix(float scaleRatio, float scalePx, float scalePy,
            float translateX, float translateY) {
        mMatrix.setScale(scaleRatio, scaleRatio, scalePx, scalePy);
        mMatrix.postTranslate(translateX, translateY);
    }

    int xx = 0;
    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        if (getSurfaceTexture() == null) return;
        if (!mVisible) setVisibility(true);

        // Check if the draw position has changed.
        if (mOriginalX != x || mOriginalY != y || mOriginalWidth != width
                || mOriginalHeight != height) {
            // Save the position and notify the listener.
            mOriginalX = x;
            mOriginalY = y;
            mOriginalWidth = width;
            mOriginalHeight = height;
            if (mPositionChangedListener != null) {
                mPositionChangedListener.onPositionChanged(x, y, width, height, mVisible);
            }

            // Calculate the actual draw position.
            mRect.set(x, y, x + width, y + height);
            mMatrix.mapRect(mRect);
            mTransformedX = Math.round(mRect.left);
            mTransformedY = Math.round(mRect.top);
            mTransformedWidth = Math.round(mRect.width());
            mTransformedHeight = Math.round(mRect.height());
        }
        super.draw(canvas, mTransformedX, mTransformedY, mTransformedWidth, mTransformedHeight);
    }

    @Override
    public synchronized void noDraw() {
        setVisibility(false);
    }

    @Override
    public synchronized void recycle() {
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
        if (mVisible != visible) {
            mVisible = visible;
            if (mPositionChangedListener != null) {
                mPositionChangedListener.onPositionChanged(mOriginalX, mOriginalY,
                        mOriginalWidth, mOriginalHeight, mVisible);
            }
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
