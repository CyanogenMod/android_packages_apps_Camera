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

package com.android.camera.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import com.android.camera.R;

public class FocusRenderer extends OverlayRenderer
        implements FocusIndicator {

    private static final String TAG = "CAM Focus";

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


    private Paint mFocusPaint;
    private Paint mSuccessPaint;
    private int mCircleSize;
    private int mFocusX;
    private int mFocusY;
    private int mCenterX;
    private int mCenterY;

    private int mDialAngle;
    private RectF mCircle;
    private RectF mDial;
    private Point mPoint1;
    private Point mPoint2;
    private int mStartAnimationAngle;
    private boolean mFocused;

    public FocusRenderer() {
        mFocusPaint = new Paint();
        mFocusPaint.setAntiAlias(true);
        mFocusPaint.setColor(Color.WHITE);
        mFocusPaint.setStyle(Paint.Style.STROKE);
        mFocusPaint.setStrokeWidth(2);
        mSuccessPaint = new Paint(mFocusPaint);
        mSuccessPaint.setColor(Color.GREEN);
        mCircle = new RectF();
        mDial = new RectF();
        mPoint1 = new Point();
        mPoint2 = new Point();
    }

    public void setFocus(int x, int y) {
        mFocusX = x;
        mFocusY = y;
        setCircle();
    }

    public int getSize() {
        return mCircleSize;
    }

    private int getRandomAngle() {
        return (int)(90 * Math.random());
    }

    private int getRandomRange() {
        return (int)(120 * Math.random());
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCircleSize = Math.min(200, Math.min(getWidth(), getHeight()) / 5);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;
        mFocusX = mCenterX;
        mFocusY = mCenterY;
        setCircle();
    }

    private void setCircle() {
        mCircle.set(mFocusX - mCircleSize, mFocusY - mCircleSize,
                mFocusX + mCircleSize, mFocusY + mCircleSize);
        mDial.set(mFocusX - mCircleSize + 30, mFocusY - mCircleSize + 30,
                mFocusX + mCircleSize - 30, mFocusY + mCircleSize - 30);
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawCircle((float) mFocusX, (float) mFocusY, (float) mCircleSize, mFocusPaint);
        Paint inner = (mFocused ? mSuccessPaint : mFocusPaint);
        canvas.drawArc(mDial, mDialAngle, 45, false, inner);
        canvas.drawArc(mDial, mDialAngle + 180, 45, false, inner);
        drawLine(canvas, mDialAngle, inner);
        drawLine(canvas, mDialAngle + 45, inner);
        drawLine(canvas, mDialAngle + 180, inner);
        drawLine(canvas, mDialAngle + 225, inner);
    }

    private void drawLine(Canvas canvas, int angle, Paint p) {
        convertCart(angle, mCircleSize - 31, mPoint1);
        convertCart(angle, mCircleSize - 5, mPoint2);
        canvas.drawLine(mPoint1.x + mFocusX, mPoint1.y + mFocusY,
                mPoint2.x + mFocusX, mPoint2.y + mFocusY, p);
    }

    private static void convertCart(int angle, int radius, Point out) {
        float a = (float) (2 * Math.PI * (angle % 360) / 360);
        out.x = (int) (radius * Math.cos(a));
        out.y = (int) (radius * Math.sin(a));
    }

    @Override
    public void showStart() {
        if (mState == STATE_IDLE) {
            int angle = getRandomAngle();
            int range = getRandomRange();
            startAnimation(R.drawable.ic_focus_focusing, SCALING_UP_TIME,
                    false, angle, angle + range);
            mState = STATE_FOCUSING;
            mStartAnimationAngle = angle;
        }
    }

    @Override
    public void showSuccess(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(R.drawable.ic_focus_focused, SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = true;
        }
    }

    @Override
    public void showFail(boolean timeout) {
        if (mState == STATE_FOCUSING) {
            startAnimation(R.drawable.ic_focus_failed, SCALING_DOWN_TIME,
                    timeout, mStartAnimationAngle);
            mState = STATE_FINISHING;
            mFocused = false;
        }
    }

    @Override
    public void clear() {
        mAnimation.cancel();
        mFocused = false;
        mOverlay.removeCallbacks(mDisappear);
        mDisappear.run();
    }

    private void startAnimation(int resid, long duration, boolean timeout,
            float toScale) {
        startAnimation(resid, duration, timeout, mDialAngle,
                toScale);
    }

    private void startAnimation(int resid, long duration, boolean timeout,
            float fromScale, float toScale) {
        setVisible(true);
        mAnimation.cancel();
        mAnimation.reset();
        mAnimation.setDuration(duration);
        mAnimation.setScale(fromScale, toScale);
        mAnimation.setAnimationListener(timeout ? mEndAction : null);
        mOverlay.startAnimation(mAnimation);
    }

    private class EndAction implements Animation.AnimationListener {
        @Override
        public void onAnimationEnd(Animation animation) {
            // Keep the focus indicator for some time.
            mOverlay.postDelayed(mDisappear, DISAPPEAR_TIMEOUT);
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
            setVisible(false);
            mFocusX = mCenterX;
            mFocusY = mCenterY;
            mState = STATE_IDLE;
            setCircle();
            mFocused = false;
        }
    }

    private class ScaleAnimation extends Animation {
        private float mFrom = 1f;
        private float mTo = 1f;

        public ScaleAnimation() {
            setFillAfter(true);
        }

        public void setScale(float from, float to) {
            mFrom = from;
            mTo = to;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mDialAngle = (int)(mFrom + (mTo - mFrom) * interpolatedTime);
        }
    }

}
