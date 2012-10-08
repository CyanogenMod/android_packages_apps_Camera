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

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.util.Log;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.RawTexture;
import com.android.gallery3d.ui.SurfaceTextureScreenNail;

/*
 * This is a ScreenNail which can display camera's preview.
 */
@TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
public class CameraScreenNail extends SurfaceTextureScreenNail {
    private static final String TAG = "CAM_ScreenNail";
    private static final int ANIM_NONE = 0;
    // Capture animation is about to start.
    private static final int ANIM_CAPTURE_START = 1;
    // Capture animation is running.
    private static final int ANIM_CAPTURE_RUNNING = 2;
    // Switch camera animation needs to copy texture.
    private static final int ANIM_SWITCH_COPY_TEXTURE = 3;
    // Switch camera animation shows the initial feedback by darkening the
    // preview.
    private static final int ANIM_SWITCH_DARK_PREVIEW = 4;
    // Switch camera animation is waiting for the first frame.
    private static final int ANIM_SWITCH_WAITING_FIRST_FRAME = 5;
    // Switch camera animation is about to start.
    private static final int ANIM_SWITCH_START = 6;
    // Switch camera animation is running.
    private static final int ANIM_SWITCH_RUNNING = 7;

    private boolean mVisible;
    // True if first onFrameAvailable has been called. If screen nail is drawn
    // too early, it will be all white.
    private boolean mFirstFrameArrived;
    private Listener mListener;
    private final float[] mTextureTransformMatrix = new float[16];

    // Animation.
    private CaptureAnimManager mCaptureAnimManager = new CaptureAnimManager();
    private SwitchAnimManager mSwitchAnimManager = new SwitchAnimManager();
    private int mAnimState = ANIM_NONE;
    private RawTexture mAnimTexture;
    // Some methods are called by GL thread and some are called by main thread.
    // This protects mAnimState, mVisible, and surface texture. This also makes
    // sure some code are atomic. For example, requestRender and setting
    // mAnimState.
    private Object mLock = new Object();

    private OnFrameDrawnListener mOneTimeFrameDrawnListener;
    private float mAspectRatio;
    private int mRenderWidth;
    private int mRenderHeight;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private boolean mFullScreen;
    private int mRotation;

    public interface Listener {
        void requestRender();
        // Preview has been copied to a texture.
        void onPreviewTextureCopied();

        void onCaptureTextureCopied();
    }

    public interface OnFrameDrawnListener {
        void onFrameDrawn(CameraScreenNail c);
    }

    public CameraScreenNail(Listener listener) {
        mListener = listener;
    }

    public void setFullScreen(boolean full) {
        mFullScreen = full;
    }

    @Override
    public void setSize(int w, int h) {
        super.setSize(w,  h);
        if (w > h) {
            mAspectRatio  = (float) w / h;
        } else {
            mAspectRatio = (float) h / w;
        }
        updateRenderSize();
    }

    private void setPreviewLayoutSize(int w, int h) {
        mPreviewWidth = w;
        mPreviewHeight = h;
        updateRenderSize();
    }

    private void updateRenderSize() {
        // these callbacks above come at different times,
        // so make sure we have all the data
        if (mPreviewWidth != 0 && mAspectRatio > 0) {
            if (mPreviewWidth > mPreviewHeight) {
                mRenderWidth = Math.max(mPreviewWidth,
                        (int) (mPreviewHeight * mAspectRatio));
                mRenderHeight = Math.max(mPreviewHeight,
                        (int)(mPreviewWidth / mAspectRatio));
            } else {
                mRenderWidth = Math.max(mPreviewWidth,
                        (int) (mPreviewHeight / mAspectRatio));
                mRenderHeight = Math.max(mPreviewHeight,
                        (int) (mPreviewWidth * mAspectRatio));
            }
        }
    }

    @Override
    public void acquireSurfaceTexture() {
        synchronized (mLock) {
            mFirstFrameArrived = false;
            super.acquireSurfaceTexture();
            mAnimTexture = new RawTexture(getWidth(), getHeight(), true);
        }
    }

    @Override
    public void releaseSurfaceTexture() {
        synchronized (mLock) {
            super.releaseSurfaceTexture();
            mAnimState = ANIM_NONE; // stop the animation
            mRotation = 0;
        }
    }

    public void copyTexture() {
        synchronized (mLock) {
            mListener.requestRender();
            mAnimState = ANIM_SWITCH_COPY_TEXTURE;
        }
    }

    public void animateSwitchCamera() {
        Log.v(TAG, "animateSwitchCamera");
        synchronized (mLock) {
            if (mAnimState == ANIM_SWITCH_DARK_PREVIEW) {
                // Do not request render here because camera has been just
                // started. We do not want to draw black frames.
                mAnimState = ANIM_SWITCH_WAITING_FIRST_FRAME;
            }
        }
    }

    public void animateCapture(int animOrientation) {
        synchronized (mLock) {
            mCaptureAnimManager.setOrientation(animOrientation);
            mListener.requestRender();
            mAnimState = ANIM_CAPTURE_START;
        }
    }

    public void setInitialOrientation(int initialOrientation) {
        mRotation = initialOrientation;
    }

    private void callbackIfNeeded() {
        if (mOneTimeFrameDrawnListener != null) {
            mOneTimeFrameDrawnListener.onFrameDrawn(this);
            mOneTimeFrameDrawnListener = null;
        }
    }

    public void directDraw(GLCanvas canvas, int x, int y, int width, int height) {
        if (mRotation != 0) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            canvas.rotate(mRotation, 0f, 0f, 1.0f);
        }
        super.draw(canvas, x, y, width, height);
        if (mRotation != 0) {
            canvas.restore();
        }
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        synchronized (mLock) {
            if (!mVisible) mVisible = true;
            SurfaceTexture surfaceTexture = getSurfaceTexture();
            if (surfaceTexture == null || !mFirstFrameArrived) return;

            switch (mAnimState) {
                case ANIM_NONE:
                    if (mFullScreen && (mRenderWidth != 0)) {
                        // overscale image to make it fullscreen
                        x = (x + width / 2) - mRenderWidth / 2;
                        y = (y + height / 2) - mRenderHeight / 2;
                        width = mRenderWidth;
                        height = mRenderHeight;
                    }
                    super.draw(canvas, x, y, width, height);
                    break;
                case ANIM_SWITCH_COPY_TEXTURE:
                    copyPreviewTexture(canvas);
                    mSwitchAnimManager.setReviewDrawingSize(width, height);
                    mListener.onPreviewTextureCopied();
                    mAnimState = ANIM_SWITCH_DARK_PREVIEW;
                    // The texture is ready. Fall through to draw darkened
                    // preview.
                case ANIM_SWITCH_DARK_PREVIEW:
                case ANIM_SWITCH_WAITING_FIRST_FRAME:
                    // Consume the frame. If the buffers are full,
                    // onFrameAvailable will not be called. Animation state
                    // relies on onFrameAvailable.
                    surfaceTexture.updateTexImage();
                    if (mRenderWidth != 0) {
                        // overscale image to make it fullscreen
                        x = (x + width / 2) - mRenderWidth / 2;
                        y = (y + height / 2) - mRenderHeight / 2;
                        width = mRenderWidth;
                        height = mRenderHeight;
                    }
                    mSwitchAnimManager.drawDarkPreview(canvas, x, y, width,
                            height, mAnimTexture);
                    break;
                case ANIM_SWITCH_START:
                    mSwitchAnimManager.startAnimation();
                    mAnimState = ANIM_SWITCH_RUNNING;
                    break;
                case ANIM_CAPTURE_START:
                    copyPreviewTexture(canvas);
                    mListener.onCaptureTextureCopied();
                    if (mRenderWidth > 0) {
                        // overscale image to make it fullscreen
                        x = (x + width / 2) - mRenderWidth / 2;
                        y = (y + height / 2) - mRenderHeight / 2;
                        width = mRenderWidth;
                        height = mRenderHeight;
                    }
                    mCaptureAnimManager.startAnimation(x, y, width, height);
                    mAnimState = ANIM_CAPTURE_RUNNING;
                    break;
            }

            if (mAnimState == ANIM_CAPTURE_RUNNING || mAnimState == ANIM_SWITCH_RUNNING) {
                boolean drawn;
                if (mAnimState == ANIM_CAPTURE_RUNNING) {
                    drawn = mCaptureAnimManager.drawAnimation(canvas, this, mAnimTexture);
                } else {
                    if (mRenderWidth != 0) {
                        // overscale image to make it fullscreen
                        x = (x + width / 2) - mRenderWidth / 2;
                        y = (y + height / 2) - mRenderHeight / 2;
                        width = mRenderWidth;
                        height = mRenderHeight;
                    }
                    drawn = mSwitchAnimManager.drawAnimation(canvas, x, y,
                            width, height, this, mAnimTexture);
                }
                if (drawn) {
                    mListener.requestRender();
                } else {
                    // Continue to the normal draw procedure if the animation is
                    // not drawn.
                    mAnimState = ANIM_NONE;
                    if (mRenderWidth != 0) {
                        // overscale image to make it fullscreen
                        x = (x + width / 2) - mRenderWidth / 2;
                        y = (y + height / 2) - mRenderHeight / 2;
                        width = mRenderWidth;
                        height = mRenderHeight;
                    }
                    super.draw(canvas, x, y, width, height);
                }
            }
            callbackIfNeeded();
        } // mLock
    }

    private void copyPreviewTexture(GLCanvas canvas) {
        int width = mAnimTexture.getWidth();
        int height = mAnimTexture.getHeight();
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
    public void noDraw() {
        synchronized (mLock) {
            mVisible = false;
        }
    }

    @Override
    public void recycle() {
        synchronized (mLock) {
            mVisible = false;
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (mLock) {
            if (getSurfaceTexture() != surfaceTexture) {
                return;
            }
            mFirstFrameArrived = true;
            if (mVisible) {
                if (mAnimState == ANIM_SWITCH_WAITING_FIRST_FRAME) {
                    mAnimState = ANIM_SWITCH_START;
                }
                // We need to ask for re-render if the SurfaceTexture receives a new
                // frame.
                mListener.requestRender();
            }
        }
    }

    // We need to keep track of the size of preview frame on the screen because
    // it's needed when we do switch-camera animation. See comments in
    // SwitchAnimManager.java. This is based on the natural orientation, not the
    // view system orientation.
    public void setPreviewFrameLayoutSize(int width, int height) {
        synchronized (mLock) {
            mSwitchAnimManager.setPreviewFrameLayoutSize(width, height);
            setPreviewLayoutSize(width, height);
        }
    }

    public void setOneTimeOnFrameDrawnListener(OnFrameDrawnListener l) {
        synchronized (mLock) {
            mFirstFrameArrived = false;
            mOneTimeFrameDrawnListener = l;
        }
    }
}
