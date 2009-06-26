package com.android.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;


public class Switcher extends ImageView {
    public interface OnSwitchListener {
        public void onSwitchChanged(Switcher source, boolean onOff);
    }

    private static final int ANIMATION_SPEED = 200;
    private static final long NO_ANIMATION = -1;

    private boolean mSwitch = false;
    private int mPosition = 0;
    private long mAnimationStartTime = 0;
    private OnSwitchListener mListener;

    public Switcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSwitch(boolean onOff) {
        if (mSwitch == onOff) return;
        mSwitch = onOff;
        if (mListener != null) mListener.onSwitchChanged(this, mSwitch);
    }

    public void setOnSwitchListener(OnSwitchListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        final int available = getHeight() - mPaddingTop - mPaddingBottom
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
                setSwitch(mPosition >= available / 2);
                startParkingAnimation();
                setPressed(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                startParkingAnimation();
                break;
        }
        return true;
    }

    private void startParkingAnimation() {
        int drawableHeight = getDrawable().getIntrinsicHeight();
        int target = mSwitch
                ? getHeight() - drawableHeight - mPaddingBottom
                : mPaddingTop;
        mAnimationStartTime = AnimationUtils.currentAnimationTimeMillis();
    }

    private void trackTouchEvent(MotionEvent event) {
        Drawable drawable = getDrawable();
        int drawableHeight = drawable.getIntrinsicHeight();
        final int height = getHeight();
        final int available = height - mPaddingTop - mPaddingBottom
                - drawableHeight;
        int x = (int) event.getY();
        mPosition = x - mPaddingTop - drawableHeight / 2;
        if (mPosition < 0) mPosition = 0;
        if (mPosition > available) mPosition = available;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        Drawable drawable = getDrawable();
        int drawableHeight = drawable.getIntrinsicHeight();
        int drawableWidth = drawable.getIntrinsicWidth();

        if (drawable == null) {
            return; // couldn't resolve the URI
        }

        if (drawableWidth == 0 || drawableHeight == 0) {
            return;     // nothing to draw (empty bounds)
        }

        if (mAnimationStartTime != NO_ANIMATION) {
            final int available = getHeight() - mPaddingTop - mPaddingBottom
                    - drawableHeight;
            long time = AnimationUtils.currentAnimationTimeMillis();
            long deltaTime = time - mAnimationStartTime;
            mPosition += ANIMATION_SPEED
                    * (mSwitch ? deltaTime : -deltaTime) / 1000;
            mAnimationStartTime = time;
            if (mPosition < 0) mPosition = 0;
            if (mPosition > available) mPosition = available;
            if (mPosition != 0 && mPosition != available) {
                postInvalidate();
            } else {
                mAnimationStartTime = NO_ANIMATION;
            }
        }

        int offsetTop = mPaddingTop + mPosition;
        int offsetLeft = (getWidth()
                - drawableWidth - mPaddingLeft - mPaddingRight) / 2;
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(offsetLeft, offsetTop);
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

}
