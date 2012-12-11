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

import android.graphics.Color;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.RawTexture;

/**
 * Class to handle the capture animation.
 */
public class CaptureAnimManager {
    @SuppressWarnings("unused")
    private static final String TAG = "CAM_Capture";
    private static final int TIME_FLASH = 200;
    private static final int TIME_HOLD = 400;
    private static final int TIME_SLIDE = 400;  // milliseconds.

    private static final int ANIM_BOTH = 0;
    private static final int ANIM_FLASH = 1;
    private static final int ANIM_SLIDE = 2;

    private final Interpolator mSlideInterpolator = new DecelerateInterpolator();

    private int mAnimOrientation;  // Could be 0, 90, 180 or 270 degrees.
    private long mAnimStartTime;  // milliseconds.
    private float mX;  // The center of the whole view including preview and review.
    private float mY;
    private float mDelta;
    private int mDrawWidth;
    private int mDrawHeight;
    private int mAnimType;

    /* preview: camera preview view.
     * review: view of picture just taken.
     */
    public CaptureAnimManager() {
    }

    public void setOrientation(int displayRotation) {
        mAnimOrientation = (360 - displayRotation) % 360;
    }

    public void animateSlide() {
        if (mAnimType != ANIM_FLASH) {
            return;
        }
        mAnimType = ANIM_SLIDE;
        mAnimStartTime = SystemClock.uptimeMillis();
    }

    public void animateFlash() {
        mAnimType = ANIM_FLASH;
    }

    public void animateFlashAndSlide() {
        mAnimType = ANIM_BOTH;
    }

    // x, y, w and h: the rectangle area where the animation takes place.
    public void startAnimation(int x, int y, int w, int h) {
        mAnimStartTime = SystemClock.uptimeMillis();
        // Set the views to the initial positions.
        mDrawWidth = w;
        mDrawHeight = h;
        mX = x;
        mY = y;
        switch (mAnimOrientation) {
            case 0:  // Preview is on the left.
                mDelta = w;
                break;
            case 90:  // Preview is below.
                mDelta = -h;
                break;
            case 180:  // Preview on the right.
                mDelta = -w;
                break;
            case 270:  // Preview is above.
                mDelta = h;
                break;
        }
    }

    // Returns true if the animation has been drawn.
    public boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review) {
        long timeDiff = SystemClock.uptimeMillis() - mAnimStartTime;
        // Check if the animation is over
        if (mAnimType == ANIM_SLIDE && timeDiff > TIME_SLIDE) return false;
        if (mAnimType == ANIM_BOTH && timeDiff > TIME_HOLD + TIME_SLIDE) return false;

        int animStep = mAnimType;
        if (mAnimType == ANIM_BOTH) {
            animStep = (timeDiff < TIME_HOLD) ? ANIM_FLASH : ANIM_SLIDE;
            if (animStep == ANIM_SLIDE) {
                timeDiff -= TIME_HOLD;
            }
        }

        if (animStep == ANIM_FLASH) {
            review.draw(canvas, (int) mX, (int) mY, mDrawWidth, mDrawHeight);
            if (timeDiff < TIME_FLASH) {
                float f = 0.3f - 0.3f * timeDiff / TIME_FLASH;
                int color = Color.argb((int) (255 * f), 255, 255, 255);
                canvas.fillRect(mX, mY, mDrawWidth, mDrawHeight, color);
            }
        } else if (animStep == ANIM_SLIDE) {
            float fraction = (float) (timeDiff) / TIME_SLIDE;
            float x = mX;
            float y = mY;
            if (mAnimOrientation == 0 || mAnimOrientation == 180) {
                x = x + mDelta * mSlideInterpolator.getInterpolation(fraction);
            } else {
                y = y + mDelta * mSlideInterpolator.getInterpolation(fraction);
            }
            // float alpha = canvas.getAlpha();
            // canvas.setAlpha(fraction);
            preview.directDraw(canvas, (int) mX, (int) mY,
                    mDrawWidth, mDrawHeight);
            // canvas.setAlpha(alpha);

            review.draw(canvas, (int) x, (int) y, mDrawWidth, mDrawHeight);
        } else {
            return false;
        }
        return true;
    }
}
