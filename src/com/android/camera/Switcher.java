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
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

/**
 * A (vertical) widget which switches between the {@code Camera} and the
 * {@code VideoCamera} activities.
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

            // Switch may be changed during the callback so set it before the
            // callback.
            mSwitch = onOff;
            if (mListener != null) {
                if (!mListener.onSwitchChanged(this, onOff)) {
                    mSwitch = !onOff;
                    return;
                }
            }
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
                tryToSetSwitch(mPosition >= getAvailableLength() / 2);
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

    protected int getAvailableLength() {
        return getHeight() - getPaddingTop() - getPaddingBottom()
                - getDrawable().getIntrinsicHeight();
    }

    /** Returns the logical position of this switch. */
    protected int trackTouch(MotionEvent event) {
        return (int) event.getY() - getPaddingTop()
                - (getDrawable().getIntrinsicHeight() / 2);
    }

    private void trackTouchEvent(MotionEvent event) {
        final int available = getAvailableLength();
        mPosition = trackTouch(event);
        if (mPosition < 0) {
            mPosition = 0;
        } else if (mPosition > available) {
            mPosition = available;
        }
        invalidate();
    }

    protected int getOffsetTopToDraw() {
        return getPaddingTop() + mPosition;
    }

    protected int getOffsetLeftToDraw() {
        return (getWidth() - getDrawable().getIntrinsicWidth()) / 2;
    }

    protected int getLogicalPosition() {
        return mPosition;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        Drawable drawable = getDrawable();

        if ((drawable.getIntrinsicHeight() == 0)
                || (drawable.getIntrinsicWidth() == 0)) {
            return;     // nothing to draw (empty bounds)
        }

        final int available = getAvailableLength();
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
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(getOffsetLeftToDraw(), getOffsetTopToDraw());
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
