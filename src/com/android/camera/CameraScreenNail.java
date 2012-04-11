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
import android.graphics.RectF;
import android.graphics.SurfaceTexture;

import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.Raw2DTexture;
import com.android.gallery3d.ui.ScreenNail;
import com.android.gallery3d.ui.ScreenNailHolder;
import com.android.gallery3d.ui.SurfaceTextureScreenNail;

/*
 * This is a ScreenNail which can displays camera preview.
 */
public class CameraScreenNail extends SurfaceTextureScreenNail {
    private static final String TAG = "CameraScreenNail";
    private static final int ANIM_NONE = 0;
    private static final int ANIM_TO_START = 1;
    private static final int ANIM_RUNNING = 2;

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
    private final float[] mTextureTransformMatrix = new float[16];

    // Animation.
    private CaptureAnimManager mAnimManager = new CaptureAnimManager();
    private int mAnimState = ANIM_NONE;
    private Raw2DTexture mAnimTexture;

    public interface RenderListener {
        void requestRender();
    }

    public interface PositionChangedListener {
        public void onPositionChanged(int x, int y, int width, int height, boolean visible);
    }

    public CameraScreenNail(RenderListener listener) {
        mRenderListener = listener;
    }

    @Override
    public void acquireSurfaceTexture() {
        super.acquireSurfaceTexture();
        mAnimTexture = new Raw2DTexture(getWidth(), getHeight());
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

    public void animate(int animOrientation) {
        switch (mAnimState) {
            case ANIM_TO_START:
                break;
            case ANIM_NONE:
                mAnimManager.setOrientation(animOrientation);
                // No break here. Continue to set the state and request for rendering.
            case ANIM_RUNNING:
                // Don't change the animation orientation during animation.
                mRenderListener.requestRender();
                mAnimState = ANIM_TO_START;
                break;
        }
    }

    public void directDraw(GLCanvas canvas, int x, int y, int width, int height) {
        super.draw(canvas, x, y, width, height);
    }

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

        switch (mAnimState) {
            case ANIM_TO_START:
                getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
                Raw2DTexture.copy(canvas, mExtTexture, mAnimTexture);
                mAnimManager.startAnimation(mTransformedX, mTransformedY,
                        mTransformedWidth, mTransformedHeight, mTextureTransformMatrix);
                mAnimState = ANIM_RUNNING;
                // Continue to draw the animation. No break is needed here.
            case ANIM_RUNNING:
                if (mAnimManager.drawAnimation(canvas, this, mAnimTexture)) {
                    mRenderListener.requestRender();
                    break;
                }
                // No break here because we continue to the normal draw
                // procedure if the animation is not drawn.
                mAnimState = ANIM_NONE;
            case ANIM_NONE:
                super.draw(canvas, mTransformedX, mTransformedY,
                        mTransformedWidth, mTransformedHeight);
                break;
        }
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
