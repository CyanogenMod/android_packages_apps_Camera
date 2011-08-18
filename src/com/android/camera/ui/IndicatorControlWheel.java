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

import com.android.camera.PreferenceGroup;
import com.android.camera.R;
import com.android.camera.Util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A view that contains camera setting indicators. The
 * indicators are spreaded around the shutter button. The first child is always
 * the shutter button.
 */
public class IndicatorControlWheel extends IndicatorControl {
    public static final int HIGHLIGHT_WIDTH = 4;
    public static final int HIGHLIGHT_DEGREES = 30;
    public static final double HIGHLIGHT_RADIANS = Math.toRadians(HIGHLIGHT_DEGREES);

    private static final String TAG = "IndicatorControlWheel";

    // The width of the edges on both sides of the wheel, which has less alpha.
    private static final float EDGE_STROKE_WIDTH = 6f;
    private static final int TIME_LAPSE_ARC_WIDTH = 6;

    private final int HIGHLIGHT_COLOR;
    private final int TIME_LAPSE_ARC_COLOR;

    // The center of the shutter button.
    private int mCenterX, mCenterY;
    // The width of the wheel stroke.
    private int mStrokeWidth;
    private double mShutterButtonRadius;
    private double mWheelRadius;
    private double mChildRadians[];
    private Paint mBackgroundPaint;
    private RectF mBackgroundRect;
    // The index of the child that is being pressed. -1 means no child is being
    // pressed.
    private int mPressedIndex = -1;

    // Time lapse recording variables.
    private int mTimeLapseInterval;  // in ms
    private long mRecordingStartTime = 0;
    private long mNumberOfFrames = 0;

    // Remember the last event for event cancelling if out of bound.
    private MotionEvent mLastMotionEvent;

    public IndicatorControlWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        HIGHLIGHT_COLOR = resources.getColor(R.color.review_control_pressed_color);
        TIME_LAPSE_ARC_COLOR = resources.getColor(R.color.time_lapse_arc);

        setWillNotDraw(false);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
        mBackgroundPaint.setAntiAlias(true);

        mBackgroundRect = new RectF();
    }

    public void initialize(Context context, PreferenceGroup group,
            String flashSetting, String[] keys, String[] otherSettingKeys) {
        mShutterButtonRadius = IndicatorControlWheelContainer.SHUTTER_BUTTON_RADIUS;
        mStrokeWidth = Util.dpToPixel(IndicatorControlWheelContainer.STROKE_WIDTH);
        mWheelRadius = mShutterButtonRadius + mStrokeWidth * 0.5;
        // Add CameraPicker control.
        initializeCameraPicker(context, group);
        super.initialize(context, group, flashSetting, keys, otherSettingKeys);

        // The radian intervals for each icon for touch events.
        mChildRadians = new double[getChildCount()];
    }

    private int getTouchIndicatorIndex(double delta) {
        // The delta is the touch point in radians.
        int count = getChildCount();
        if (count == 0) return -1;
        int sectors = count - 1;
        double sectorDegrees = Math.min(HIGHLIGHT_RADIANS,
                (count == 1) ? HIGHLIGHT_RADIANS : (Math.PI / sectors));
        // Check which indicator is touched.
        if ((delta >= (Math.PI - HIGHLIGHT_RADIANS) / 2) &&
            (delta <= (Math.PI + (Math.PI + HIGHLIGHT_RADIANS) / 2))) {
            int index = (int) ((delta - Math.PI / 2) * sectors / Math.PI);
            if (index > sectors) return sectors ; // degree greater than 270
            if (index < 0) return 0;  // degree less than 90
            if (delta <= (mChildRadians[index] + sectorDegrees / 2)) return index;
            if (delta >= (mChildRadians[index + 1] - sectorDegrees / 2)) {
                return index + 1;
            }
        }
        return -1;
    }

    private void injectMotionEvent(int viewIndex, MotionEvent event, int action) {
        View v = getChildAt(viewIndex);
        event.setAction(action);
        v.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;
        mLastMotionEvent = event;
        int action = event.getAction();

        double dx = event.getX() - mCenterX;
        double dy = mCenterY - event.getY();
        double radius = Math.sqrt(dx * dx + dy * dy);

        // Ignore the event if too far from the shutter button.
        if ((radius <= mWheelRadius + mStrokeWidth) && (radius > mShutterButtonRadius)) {
            double delta = Math.atan2(dy, dx);
            if (delta < 0) delta += Math.PI * 2;
            int index = getTouchIndicatorIndex(delta);
            // Move over from one indicator to another.
            if ((index != mPressedIndex) || (action == MotionEvent.ACTION_DOWN)) {
                if (mPressedIndex != -1) {
                    injectMotionEvent(mPressedIndex, event, MotionEvent.ACTION_CANCEL);
                } else {
                    // Cancel the popup if it is different from the selected.
                    if (getSelectedIndicatorIndex() != index) dismissSettingPopup();
                }
                if ((index != -1) && (action == MotionEvent.ACTION_MOVE)) {
                    injectMotionEvent(index, event, MotionEvent.ACTION_DOWN);
                }
            }
            if ((index != -1) && (action != MotionEvent.ACTION_MOVE)) {
                getChildAt(index).dispatchTouchEvent(event);
            }
            // Once the button is up, reset the press index.
            mPressedIndex = (action == MotionEvent.ACTION_UP) ? -1 : index;
            invalidate();
            return true;
        }
        // The event is not on any of the child.
        dismissSettingPopup();
        if (mPressedIndex != -1) {
            View cancelChild = getChildAt(mPressedIndex);
            event.setAction(MotionEvent.ACTION_CANCEL);
            cancelChild.dispatchTouchEvent(event);
            mPressedIndex = -1;
        }
        invalidate();
        return false;
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;

        mCenterX = right - left - Util.dpToPixel(
                IndicatorControlWheelContainer.FULL_WHEEL_RADIUS);
        mCenterY = (bottom - top) / 2;

        // Layout the settings. The icons are spreaded on the left side of the
        // shutter button. So the angle starts from 90 to 270 degrees.

        // This will just get rid of Divide-By-Zero.
        double intervalDegrees = (count == 1) ? 90.0 : 180.0 / (count - 1);
        double initialDegrees = 90.0;

        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            double degree = initialDegrees + intervalDegrees * i;
            double radian = Math.toRadians(degree);
            int x = mCenterX + (int)(mWheelRadius * Math.cos(radian));
            int y = mCenterY - (int)(mWheelRadius * Math.sin(radian));
            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            view.layout(x - width / 2, y - height / 2, x + width / 2,
                    y + height / 2);
            // Store the radian intervals for each icon.
            mChildRadians[i] = Math.toRadians(degree);
        }
    }

    public void startTimeLapseAnimation(int timeLapseInterval, long startTime) {
        mTimeLapseInterval = timeLapseInterval;
        mRecordingStartTime = startTime;
        mNumberOfFrames = 0;
        invalidate();
    }

    public void stopTimeLapseAnimation() {
        mTimeLapseInterval = 0;
        invalidate();
    }

    private int getSelectedIndicatorIndex() {
        for (int i = 0; i < mIndicators.size(); i++) {
            AbstractIndicatorButton b = mIndicators.get(i);
            if (b.getPopupWindow() != null) {
                return indexOfChild(b);
            }
        }
        if (mPressedIndex != -1) {
            if (!(getChildAt(mPressedIndex) instanceof AbstractIndicatorButton)) {
                return mPressedIndex;
            }
        }
        return -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw highlight.
        float delta = mStrokeWidth * 0.5f;
        float radius = (float) (mWheelRadius + mStrokeWidth * 0.5 + EDGE_STROKE_WIDTH);
        mBackgroundRect.set(mCenterX - radius, mCenterY - radius, mCenterX + radius,
                 mCenterY + radius);

        int selectedIndex = getSelectedIndicatorIndex();

        // Draw the highlight arc if an indicator is selected or being pressed.
       if (selectedIndex >= 0) {
            int count = getChildCount();
            float initialDegrees = 90.0f;
            float intervalDegrees = (count <= 1) ? 0.0f : 180.0f / (count - 1);
            float degree;
            degree = initialDegrees + intervalDegrees * selectedIndex;
            mBackgroundPaint.setStrokeWidth(HIGHLIGHT_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            mBackgroundPaint.setColor(HIGHLIGHT_COLOR);
            canvas.drawArc(mBackgroundRect, -degree - HIGHLIGHT_DEGREES / 2,
                    HIGHLIGHT_DEGREES, false, mBackgroundPaint);
        }

        // Draw arc shaped indicator in time lapse recording.
        if (mTimeLapseInterval != 0) {
            // Setup rectangle and paint.
            mBackgroundRect.set((float)(mCenterX - mShutterButtonRadius),
                    (float)(mCenterY - mShutterButtonRadius),
                    (float)(mCenterX + mShutterButtonRadius),
                    (float)(mCenterY + mShutterButtonRadius));
            mBackgroundRect.inset(3f, 3f);
            mBackgroundPaint.setStrokeWidth(TIME_LAPSE_ARC_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            mBackgroundPaint.setColor(TIME_LAPSE_ARC_COLOR);

            // Compute the start angle and sweep angle.
            long timeDelta = SystemClock.uptimeMillis() - mRecordingStartTime;
            long numberOfFrames = timeDelta / mTimeLapseInterval;
            float sweepAngle;
            if (numberOfFrames > mNumberOfFrames) {
                // The arc just acrosses 0 degree. Draw a full circle so it
                // looks better.
                sweepAngle = 360;
                mNumberOfFrames = numberOfFrames;
            } else {
                sweepAngle = timeDelta % mTimeLapseInterval * 360f / mTimeLapseInterval;
            }

            canvas.drawArc(mBackgroundRect, 0, sweepAngle, false, mBackgroundPaint);
            invalidate();
        }

        super.onDraw(canvas);
    }

    public void onTouchOutBound() {
        if (mPressedIndex != -1) {
            dismissSettingPopup();
            injectMotionEvent(mPressedIndex, mLastMotionEvent, MotionEvent.ACTION_CANCEL);
            mPressedIndex = -1;
            invalidate();
        }
    }
}
