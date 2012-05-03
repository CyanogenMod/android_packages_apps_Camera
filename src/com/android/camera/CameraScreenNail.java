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

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.RawTexture;
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
    private RenderListener mRenderListener;
    private final float[] mTextureTransformMatrix = new float[16];

    // Animation.
    private CaptureAnimManager mAnimManager = new CaptureAnimManager();
    private int mAnimState = ANIM_NONE;
    private RawTexture mAnimTexture;

    public interface RenderListener {
        void requestRender();
    }

    public CameraScreenNail(RenderListener listener) {
        mRenderListener = listener;
    }

    @Override
    public void acquireSurfaceTexture() {
        super.acquireSurfaceTexture();
        mAnimTexture = new RawTexture(getWidth(), getHeight(), true);
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

        switch (mAnimState) {
            case ANIM_TO_START:
                copyPreviewTexture(canvas);
                mAnimManager.startAnimation(x, y, width, height);
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
                super.draw(canvas, x, y, width, height);
                break;
        }
    }

    private void copyPreviewTexture(GLCanvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.beginRenderTarget(mAnimTexture);
        // Flip preview texture vertically. OpenGL uses bottom left point
        // as the origin (0, 0).
        canvas.translate(0, height);
        canvas.scale(1, -1, 1);
        getSurfaceTexture().getTransformMatrix(mTextureTransformMatrix);
        canvas.drawTexture(mExtTexture,
                mTextureTransformMatrix, 0, 0, width, height);
        canvas.endRenderTarget();
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
        }
    }
}
