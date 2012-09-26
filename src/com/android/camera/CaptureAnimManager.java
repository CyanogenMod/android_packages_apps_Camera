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

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.RawTexture;

/**
 * Class to handle the capture animation.
 */
public class CaptureAnimManager {
    @SuppressWarnings("unused")
    private static final String TAG = "CAM_Capture";
    private static final int TIME_FLASH = 80;
    private static final int TIME_HOLD = 200;
    private static final int TIME_SLIDE = 600;  // milliseconds.

    private final Interpolator mSlideInterpolator = new DecelerateInterpolator();

    private int mAnimOrientation;  // Could be 0, 90, 180 or 270 degrees.
    private long mAnimStartTime;  // milliseconds.
    private float mX;  // The center of the whole view including preview and review.
    private float mY;
    private float mDelta;
    private int mDrawWidth;
    private int mDrawHeight;
    private int mFlashColor;

    /* preview: camera preview view.
     * review: view of picture just taken.
     */
    public CaptureAnimManager() {
        mFlashColor = Color.argb(180, 255, 255, 255);
    }

    public void setOrientation(int animOrientation) {
        mAnimOrientation = animOrientation;
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
        if (timeDiff > TIME_HOLD + TIME_SLIDE) return false;
        if (timeDiff < TIME_HOLD) {
            review.draw(canvas, (int) mX, (int) mY, mDrawWidth, mDrawHeight);
            if (timeDiff < TIME_FLASH) {
                canvas.fillRect(mX, mY, mDrawWidth, mDrawHeight, mFlashColor);
            }
        } else {
            float fraction = (float) (timeDiff - TIME_HOLD) / TIME_SLIDE;
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
        }
        return true;
    }
}
