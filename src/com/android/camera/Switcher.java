/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

/**
 * A widget which switchs between the {@code Camera} and the {@code VideoCamera}
 * activities.
 */
public class Switcher extends ImageView implements View.OnTouchListener {

    @SuppressWarnings("unused")
    private static final String TAG = "Switcher";

    /** A callback to be called when the user wants to switch activity. */
    public interface OnSwitchListener {
        // Returns true if the listener agrees that the switch can be changed.
        public boolean onSwitchChanged(Switcher source, boolean onOff);
    }

    private static final int ANIMATION_SPEED = 200;
    private static final long NO_ANIMATION = -1;

    private boolean mSwitch = false;
    private int mPosition = 0;
    private long mAnimationStartTime = NO_ANIMATION;
    private int mAnimationStartPosition;
    private OnSwitchListener mListener;

    public Switcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSwitch(boolean onOff) {
        if (mSwitch == onOff) return;
        mSwitch = onOff;
        invalidate();
    }

    // Try to change the switch position. (The client can veto it.)
    private void tryToSetSwitch(boolean onOff) {
        try {
            if (mSwitch == onOff) return;

            if (mListener != null) {
                if (!mListener.onSwitchChanged(this, onOff)) {
                    return;
                }
            }

            mSwitch = onOff;
        } finally {
            startParkingAnimation();
        }
    }

    public void setOnSwitchListener(OnSwitchListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        final int available = getHeight() - getPaddingTop() - getPaddingBottom()
                - getDrawable().getIntrinsicHeight();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mAnimationStartTime = NO_ANIMATION;
                setPressed(true);
                trackTouchEvent(event);
                break;

            case MotionEvent.ACTION_MOVE:
                trackTouchEvent(event);
                break;

            case MotionEvent.ACTION_UP:
                trackTouchEvent(event);
                tryToSetSwitch(mPosition >= available / 2);
                setPressed(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                tryToSetSwitch(mSwitch);
                setPressed(false);
                break;
        }
        return true;
    }

    private void startParkingAnimation() {
        mAnimationStartTime = AnimationUtils.currentAnimationTimeMillis();
        mAnimationStartPosition = mPosition;
    }

    private void trackTouchEvent(MotionEvent event) {
        Drawable drawable = getDrawable();
        int drawableHeight = drawable.getIntrinsicHeight();
        final int height = getHeight();
        final int available = height - getPaddingTop() - getPaddingBottom()
                - drawableHeight;
        int x = (int) event.getY();
        mPosition = x - getPaddingTop() - drawableHeight / 2;
        if (mPosition < 0) mPosition = 0;
        if (mPosition > available) mPosition = available;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        Drawable drawable = getDrawable();
        int drawableHeight = drawable.getIntrinsicHeight();
        int drawableWidth = drawable.getIntrinsicWidth();

        if (drawableWidth == 0 || drawableHeight == 0) {
            return;     // nothing to draw (empty bounds)
        }

        final int available = getHeight() - getPaddingTop()
                - getPaddingBottom() - drawableHeight;
        if (mAnimationStartTime != NO_ANIMATION) {
            long time = AnimationUtils.currentAnimationTimeMillis();
            int deltaTime = (int) (time - mAnimationStartTime);
            mPosition = mAnimationStartPosition +
                    ANIMATION_SPEED * (mSwitch ? deltaTime : -deltaTime) / 1000;
            if (mPosition < 0) mPosition = 0;
            if (mPosition > available) mPosition = available;
            boolean done = (mPosition == (mSwitch ? available : 0));
            if (!done) {
                invalidate();
            } else {
                mAnimationStartTime = NO_ANIMATION;
            }
        } else if (!isPressed()){
            mPosition = mSwitch ? available : 0;
        }
        int offsetTop = getPaddingTop() + mPosition;
        int offsetLeft = (getWidth()
                - drawableWidth - getPaddingLeft() - getPaddingRight()) / 2;
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(offsetLeft, offsetTop);
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    // Consume the touch events for the specified view.
    public void addTouchView(View v) {
        v.setOnTouchListener(this);
    }

    // This implements View.OnTouchListener so we intercept the touch events
    // and pass them to ourselves.
    public boolean onTouch(View v, MotionEvent event) {
        onTouchEvent(event);
        return true;
    }
}
