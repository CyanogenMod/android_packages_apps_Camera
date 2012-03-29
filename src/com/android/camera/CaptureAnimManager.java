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

import android.animation.Animator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

/**
 * Class to handle the capture animation.
 */
public class CaptureAnimManager implements
        ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {

    private static final float ZOOM_DELTA = 0.2f;  // The amount of change for zooming out.
    private static final float ZOOM_IN_BEGIN = 1f - ZOOM_DELTA;  // Pre-calculated value for
                                                                 // convenience.
    private static final long CAPTURE_ANIM_DURATION = 800;  // milliseconds.
    private static final float VIEW_GAP_RATIO = 0.1f;  // The gap between preview and review based
                                                       // on the view dimension.
    private static final float TOTAL_RATIO = 1f + VIEW_GAP_RATIO;

    private final ImageView mReview;
    private final View mPreview;

    private float mXDelta;
    private float mYDelta;
    private float mXDeltaScaled;
    private float mYDeltaScaled;
    private float mOffset; // The offset where preview should be put relative to the review.
    private int mAnimOrientation;

    private final Interpolator ZoomOutInterpolator = new DecelerateInterpolator();
    private final Interpolator ZoomInInterpolator = new AccelerateInterpolator();

    private final ValueAnimator mCaptureAnim = new ValueAnimator();
    // The translation value for 4 different orientations.
    private final PropertyValuesHolder[] mReviewSlideValue = new PropertyValuesHolder[4];

    /* preview: camera preview view.
     * review: view of picture just taken.
     */
    public CaptureAnimManager(View preview, ImageView review) {
        mReview = review;
        mPreview = preview;

        mCaptureAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mCaptureAnim.setDuration(CAPTURE_ANIM_DURATION);
        mCaptureAnim.addListener(this);
        mCaptureAnim.addUpdateListener(this);
    }

    void initializeDelta(int xDelta, int yDelta) {
        mXDelta = xDelta;
        mYDelta = yDelta;
        mXDeltaScaled = mXDelta * TOTAL_RATIO;
        mYDeltaScaled = mYDelta * TOTAL_RATIO;
        mReviewSlideValue[0] = PropertyValuesHolder.ofFloat("", 0f, mXDeltaScaled);
        mReviewSlideValue[1] = PropertyValuesHolder.ofFloat("", 0f, -mYDeltaScaled);
        mReviewSlideValue[2] = PropertyValuesHolder.ofFloat("", 0f, -mXDeltaScaled);
        mReviewSlideValue[3] = PropertyValuesHolder.ofFloat("", 0f, mYDeltaScaled);
    }

    // xDelta, yDelta: The dimension of the viewfinder in which animation happens.
    public void startAnimation(Bitmap bitmap, int animOrientation, int xDelta, int yDelta) {
        if (xDelta != mXDelta || mYDelta != yDelta) {
            initializeDelta(xDelta, yDelta);
        }
        mAnimOrientation = animOrientation;
        // Reset the views before the animation begins.
        mCaptureAnim.cancel();
        mCaptureAnim.setValues(mReviewSlideValue[(mAnimOrientation / 90) % 4]);
        switch (mAnimOrientation) {
            case 0:
                mPreview.setTranslationX(-mXDeltaScaled);
                mPreview.setPivotX(mXDelta * (1f + VIEW_GAP_RATIO / 2f)); //left
                mReview.setPivotX(mXDelta * -VIEW_GAP_RATIO / 2f); //right
                mPreview.setPivotY(mYDeltaScaled / 2f);
                mReview.setPivotY(mYDeltaScaled / 2f);
                mOffset = -mXDeltaScaled;
                break;
            case 90:
                mPreview.setTranslationY(mYDeltaScaled);
                mPreview.setPivotX(mXDeltaScaled / 2f);
                mReview.setPivotX(mXDeltaScaled / 2f);
                mPreview.setPivotY(mYDelta * -VIEW_GAP_RATIO / 2f); //down
                mReview.setPivotY(mYDelta * (1f + VIEW_GAP_RATIO / 2f)); //up
                mOffset = (mYDeltaScaled);
                break;
            case 180:
                mPreview.setTranslationX(mXDeltaScaled);
                mPreview.setPivotX(mXDelta * -VIEW_GAP_RATIO / 2f); //right
                mReview.setPivotX(mXDelta * (1f + VIEW_GAP_RATIO / 2f)); //left
                mPreview.setPivotY(mYDeltaScaled / 2f);
                mReview.setPivotY(mYDeltaScaled / 2f);
                mOffset = mXDeltaScaled;
                break;
            case 270:
                mPreview.setTranslationY(-mYDeltaScaled);
                mPreview.setPivotX(mXDeltaScaled / 2f);
                mReview.setPivotX(mXDeltaScaled / 2f);
                mPreview.setPivotY(mYDelta * (1f + VIEW_GAP_RATIO / 2f)); //up
                mReview.setPivotY(mYDelta * -VIEW_GAP_RATIO / 2f); //down
                mOffset = -mYDeltaScaled;
                break;
        }

        mReview.setImageBitmap(bitmap);
        mReview.setTranslationX(0f);
        mReview.setTranslationY(0f);
        mReview.setScaleX(1f);
        mReview.setScaleY(1f);
        mReview.setVisibility(View.VISIBLE);
        mCaptureAnim.start();
    }

    @Override
    public void onAnimationStart(Animator anim) {
    }

    @Override
    public void onAnimationEnd(Animator anim) {
        mReview.setVisibility(View.GONE);
        mReview.setImageBitmap(null);
    }

    @Override
    public void onAnimationRepeat(Animator anim) {}

    @Override
    public void onAnimationCancel(Animator anim) {}

    @Override
    public void onAnimationUpdate(ValueAnimator anim) {
        float fraction = anim.getAnimatedFraction();
        zoomAnimation(fraction);
        mPreview.setAlpha(fraction);
        slideAnimation((Float) anim.getAnimatedValue());
    }

    // Take a translation value of the review and calculate the corresponding value for
    // the preview.
    private void slideAnimation(float value) {
        if (mAnimOrientation == 0 || mAnimOrientation == 180) {
            // Slide horizontally.
            mReview.setTranslationX(value);
            mPreview.setTranslationX(value + mOffset);
        } else {
            // Slide vertically.
            mReview.setTranslationY(value);
            mPreview.setTranslationY(value + mOffset);
        }
    }

    // Calculate the zoom factor based on the given time fraction.
    private void zoomAnimation(float fraction) {
        float value = 1f;
        if (fraction <= 0.5f) {
            // Zoom in for the beginning.
            value = 1f - ZOOM_DELTA * ZoomOutInterpolator.getInterpolation(
                    fraction * 2);
        } else {
            // Zoom out for the last.
            value = ZOOM_IN_BEGIN + ZOOM_DELTA * ZoomInInterpolator.getInterpolation(
                    (fraction - 0.5f) * 2f);
        }
        mReview.setScaleX(value);
        mReview.setScaleY(value);
        mPreview.setScaleX(value);
        mPreview.setScaleY(value);
    }
}
