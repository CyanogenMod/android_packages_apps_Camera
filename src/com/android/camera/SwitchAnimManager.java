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
import android.util.Log;

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.RawTexture;

/**
 * Class to handle the animation when switching between back and front cameras.
 * An image of the previous camera zooms in and fades out. The preview of the
 * new camera zooms in and fades in. The image of the previous camera is called
 * review in this class.
 */
public class SwitchAnimManager {
    private static final String TAG = "SwitchAnimManager";
    // The amount of change for zooming in and out.
    private static final float ZOOM_DELTA_PREVIEW = 0.2f;
    private static final float ZOOM_DELTA_REVIEW = 0.5f;
    private static final float ANIMATION_DURATION = 400;  // ms
    public static final float INITIAL_DARKEN_ALPHA = 0.8f;

    private long mAnimStartTime;  // milliseconds.
    // The drawing width and height of the review image. This is saved when the
    // texture is copied.
    private int mReviewDrawingWidth;
    private int mReviewDrawingHeight;
    // The maximum width of the camera screen nail width from onDraw. We need to
    // know how much the preview is scaled and scale the review the same amount.
    // For example, the preview is not full screen in film strip mode.
    private int mPreviewFrameLayoutWidth;

    public SwitchAnimManager() {
    }

    public void setReviewDrawingSize(int width, int height) {
        mReviewDrawingWidth = width;
        mReviewDrawingHeight = height;
    }

    // width: the width of PreviewFrameLayout view.
    // height: the height of PreviewFrameLayout view. Not used. Kept for
    //         consistency.
    public void setPreviewFrameLayoutSize(int width, int height) {
        mPreviewFrameLayoutWidth = width;
    }

    // w and h: the rectangle area where the animation takes place.
    public void startAnimation() {
        mAnimStartTime = SystemClock.uptimeMillis();
    }

    // Returns true if the animation has been drawn.
    // preview: camera preview view.
    // review: snapshot of the preview before switching the camera.
    public boolean drawAnimation(GLCanvas canvas, int x, int y, int width,
            int height, CameraScreenNail preview, RawTexture review) {
        long timeDiff = SystemClock.uptimeMillis() - mAnimStartTime;
        if (timeDiff > ANIMATION_DURATION) return false;
        float fraction = timeDiff / ANIMATION_DURATION;

        // Calculate the position and the size of the preview.
        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        float previewAnimScale = 1 - ZOOM_DELTA_PREVIEW * (1 - fraction);
        float previewWidth = width * previewAnimScale;
        float previewHeight = height * previewAnimScale;
        int previewX = Math.round(centerX - previewWidth / 2);
        int previewY = Math.round(centerY - previewHeight / 2);

        // Calculate the position and the size of the review.
        float reviewAnimScale = 1 + ZOOM_DELTA_REVIEW * fraction;

        // Calculate how much preview is scaled.
        // The scaling is done by PhotoView in Gallery so we don't have the
        // scaling information but only the width and the height passed to this
        // method. The inference of the scale ratio is done by matching the
        // current width and the original width we have at first when the camera
        // layout is inflated.
        float scaleRatio = 1;
        if (mPreviewFrameLayoutWidth != 0) {
            scaleRatio = (float) width / mPreviewFrameLayoutWidth;
        } else {
            Log.e(TAG, "mPreviewFrameLayoutWidth is 0.");
        }
        float reviewWidth = mReviewDrawingWidth * reviewAnimScale * scaleRatio;
        float reviewHeight = mReviewDrawingHeight * reviewAnimScale * scaleRatio;
        int reviewX = Math.round(centerX - reviewWidth / 2);
        int reviewY = Math.round(centerY - reviewHeight / 2);

        // Draw the preview.
        float alpha = canvas.getAlpha();
        canvas.setAlpha(fraction); // fade in
        preview.directDraw(canvas, previewX, previewY, Math.round(previewWidth),
                Math.round(previewHeight));

        // Draw the review.
        canvas.setAlpha((1f - fraction) * INITIAL_DARKEN_ALPHA); // fade out
        review.draw(canvas, reviewX, reviewY, Math.round(reviewWidth),
                Math.round(reviewHeight));
        canvas.setAlpha(alpha);
        return true;
    }

    public boolean drawDarkPreview(GLCanvas canvas, int x, int y, int width,
            int height, RawTexture review) {
        // Calculate the position and the size.
        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        float scaleRatio = 1;
        if (mPreviewFrameLayoutWidth != 0) {
            scaleRatio = (float) width / mPreviewFrameLayoutWidth;
        } else {
            Log.e(TAG, "mPreviewFrameLayoutWidth is 0.");
        }
        float reviewWidth = mReviewDrawingWidth * scaleRatio;
        float reviewHeight = mReviewDrawingHeight * scaleRatio;
        int reviewX = Math.round(centerX - reviewWidth / 2);
        int reviewY = Math.round(centerY - reviewHeight / 2);

        // Draw the review.
        float alpha = canvas.getAlpha();
        canvas.setAlpha(INITIAL_DARKEN_ALPHA);
        review.draw(canvas, reviewX, reviewY, Math.round(reviewWidth),
                Math.round(reviewHeight));
        canvas.setAlpha(alpha);
        return true;
    }

}
