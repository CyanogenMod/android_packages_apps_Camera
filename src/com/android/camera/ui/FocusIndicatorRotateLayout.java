/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.ui;

import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.content.Context;
import android.util.AttributeSet;

import com.android.camera.R;

// A view that indicates the focus area or the metering area.
public class FocusIndicatorRotateLayout extends RotateLayout implements FocusIndicator {
    // Sometimes continuous autofucus starts and stops several times quickly.
    // These states are used to make sure the animation is run for at least some
    // time.
    private int mState;
    private ScaleAnimation mAnimation = new ScaleAnimation();
    private static final int STATE_IDLE = 0;
    private static final int STATE_FOCUSING = 1;
    private static final int STATE_FINISHING = 2;

    private Runnable mDisappear = new Disappear();
    private Animation.AnimationListener mEndAction = new EndAction();
    private static final int SCALING_UP_TIME = 1000;
    private static final int SCALING_DOWN_TIME = 200;
    private static final int DISAPPEAR_TIMEOUT = 200;
    private static final float MAX_SCALE = 1.5f;

    public FocusIndicatorRotateLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void showStart() {
        if (mState == STATE_IDLE) {
            startAnimation(R.drawable.ic_focus_focusing, SCALING_UP_TIME,
                    false, 1f, MAX_SCALE);
            mState = STATE_FOCUSING;
        }
    }

    @Override
    public void showSuccess(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(R.drawable.ic_focus_focused, SCALING_DOWN_TIME,
                    timeout, 1f);
            mState = STATE_FINISHING;
        }
    }

    @Override
    public void showFail(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(R.drawable.ic_focus_failed, SCALING_DOWN_TIME,
                    timeout, 1f);
            mState = STATE_FINISHING;
        }
    }

    @Override
    public void clear() {
        mAnimation.cancel();
        removeCallbacks(mDisappear);
        mDisappear.run();
    }

    private void startAnimation(int resid, long duration, boolean timeout,
            float toScale) {
        startAnimation(resid, duration, timeout, mAnimation.getCurrentScale(),
                toScale);
    }

    private void startAnimation(int resid, long duration, boolean timeout,
            float fromScale, float toScale) {
        mChild.setBackgroundDrawable(getResources().getDrawable(resid));
        mAnimation.cancel();
        mAnimation.reset();
        mAnimation.setDuration(duration);
        mAnimation.setScale(fromScale, toScale);
        mAnimation.setAnimationListener(timeout ? mEndAction : null);
        startAnimation(mAnimation);
    }

    private class EndAction implements Animation.AnimationListener {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Keep the focus indicator for some time.
            postDelayed(mDisappear, DISAPPEAR_TIMEOUT);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    private class Disappear implements Runnable {
        @Override
        public void run() {
            mChild.setBackgroundDrawable(null);
            mState = STATE_IDLE;
        }
    }

    private class ScaleAnimation extends Animation {
        private float mFrom = 1f;
        private float mTo = 1f;
        private float mCurrentScale = 1f;

        public ScaleAnimation() {
            setFillAfter(true);
        }

        public void setScale(float from, float to) {
            mFrom = from;
            mTo = to;
        }

        public float getCurrentScale() {
            return mCurrentScale;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mCurrentScale = mFrom + (mTo - mFrom) * interpolatedTime;
            t.getMatrix().setScale(mCurrentScale, mCurrentScale,
                    getWidth() / 2f, getHeight() / 2f);
        }
    }
}
