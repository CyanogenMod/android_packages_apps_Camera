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

import android.os.SystemClock;

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.RawTexture;

/**
 * Class to handle the animation when switching between back and front cameras.
 * The snapshot of the previous camera zooms in and fades out. The preview of
 * the new camera zooms in and fades in.
 */
public class SwitchAnimManager {
    private static final String TAG = "SwitchAnimManager";
    // The amount of change for zooming in and out.
    private static final float ZOOM_DELTA_PREVIEW = 0.2f;
    private static final float ZOOM_DELTA_REVIEW = 0.5f;
    private static final float ANIMATION_DURATION = 400;  // ms
    public static final float INITIAL_DARKEN_ALPHA = 0.8f;

    private long mAnimStartTime;  // milliseconds.
    // The center of the preview and review
    private float mCenterX;
    private float mCenterY;
    private int mDrawWidth;
    private int mDrawHeight;

    public SwitchAnimManager() {
    }

    // x, y, x and h: the rectangle area where the animation takes place.
    public void startAnimation(int x, int y, int w, int h) {
        mAnimStartTime = SystemClock.uptimeMillis();
        mDrawWidth = w;
        mDrawHeight = h;
        mCenterX = x + w / 2f;
        mCenterY = y + h / 2f;
    }

    // Returns true if the animation has been drawn.
    // preview: camera preview view.
    // review: snapshot of the preview before switching the camera.
    public boolean drawAnimation(GLCanvas canvas, CameraScreenNail preview,
                RawTexture review) {
        long timeDiff = SystemClock.uptimeMillis() - mAnimStartTime;
        if (timeDiff > ANIMATION_DURATION) return false;

        // Calculate the position and the size of the preview and review.
        float fraction = timeDiff / ANIMATION_DURATION;
        float previewScale = 1 - ZOOM_DELTA_PREVIEW * (1 - fraction);
        float reviewScale = 1 + ZOOM_DELTA_REVIEW * fraction;
        float previewWidth = mDrawWidth * previewScale;
        float previewHeight = mDrawHeight * previewScale;
        float reviewWidth = mDrawWidth * reviewScale;
        float reviewHeight = mDrawHeight * reviewScale;
        int previewX = Math.round(mCenterX - previewWidth / 2);
        int previewY = Math.round(mCenterY - previewHeight / 2);
        int reviewX = Math.round(mCenterX - reviewWidth / 2);
        int reviewY = Math.round(mCenterY - reviewHeight / 2);

        // Draw the preview and review.
        float alpha = canvas.getAlpha();
        canvas.setAlpha(fraction); // new camera preview fades in
        preview.directDraw(canvas, previewX, previewY, Math.round(previewWidth),
                Math.round(previewHeight));

        // old camera preview fades out
        canvas.setAlpha((1f - fraction) * INITIAL_DARKEN_ALPHA);
        review.draw(canvas, reviewX, reviewY, (int) reviewWidth,
                (int) reviewHeight);
        canvas.setAlpha(alpha);
        return true;
    }
}
