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
 * A view that contains shutter button and camera setting indicators. The
 * indicators are spreaded around the shutter button. The first child is always
 * the shutter button.
 */
public class IndicatorWheel extends IndicatorControl {
    private static final String TAG = "IndicatorWheel";

    // The width of the edges on both sides of the wheel, which has less alpha.
    private static final float EDGE_STROKE_WIDTH = 6f;
    private static final int HIGHLIGHT_WIDTH = 4;
    private static final int HIGHLIGHT_DEGREE = 30;
    private static final int TIME_LAPSE_ARC_WIDTH = 6;

    private final int HIGHLIGHT_COLOR;
    private final int TIME_LAPSE_ARC_COLOR;

    // The center of the shutter button.
    private int mCenterX, mCenterY;
    // The width of the wheel stroke.
    private int mStrokeWidth = 60;
    private View mShutterButton;
    private double mShutterButtonRadius;
    private double mWheelRadius;
    private double mSectorInitialRadians[];
    private Paint mBackgroundPaint;
    private RectF mBackgroundRect;
    // The index of the child that is being pressed. -1 means no child is being
    // pressed.
    private int mPressedIndex = -1;

    // Time lapse recording variables.
    private int mTimeLapseInterval;  // in ms
    private long mRecordingStartTime = 0;
    private long mNumberOfFrames = 0;

    public IndicatorWheel(Context context, AttributeSet attrs) {
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

    @Override
    public void initialize(Context context, PreferenceGroup group,
            String flashSetting, String[] keys, String[] otherSettingKeys) {
        // Add CameraPicker control.
        initializeCameraPicker(context, group);
        super.initialize(context, group, flashSetting, keys, otherSettingKeys);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;

        int action = event.getAction();

        double dx = event.getX() - mCenterX;
        double dy = mCenterY - event.getY();
        double radius = Math.sqrt(dx * dx + dy * dy);

        // Check if the event should be dispatched to the shutter button.
        if (radius <= mShutterButtonRadius) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                mPressedIndex = (action == MotionEvent.ACTION_DOWN) ? 0 : -1;
                return mShutterButton.dispatchTouchEvent(event);
            }
            return false;
        }

        // Send cancel to the shutter button if it was pressed.
        if (mPressedIndex == 0) {
            event.setAction(MotionEvent.ACTION_CANCEL);
            mShutterButton.dispatchTouchEvent(event);
            mPressedIndex = -1;
            return true;
        }

        // Ignore the event if it's too near to the shutter button or too far
        // from the shutter button.
        if (radius <= mWheelRadius + mStrokeWidth) {
            double delta = Math.atan2(dy, dx);
            if (delta < 0) delta += Math.PI * 2;
            // Check which sector is pressed.
            if (delta > mSectorInitialRadians[0]) {
                for (int i = 1; i < getChildCount(); i++) {
                    if (delta < mSectorInitialRadians[i]) {
                        View child = getChildAt(i);
                        if (action == MotionEvent.ACTION_DOWN) {
                            if (child instanceof AbstractIndicatorButton) {
                                AbstractIndicatorButton b = (AbstractIndicatorButton) child;
                                // If the same setting is pressed when the popup is open,
                                // do not dismiss it because it will be handled in the child.
                                if (b.getPopupWindow() == null) {
                                    dismissSettingPopup();
                                }
                            } else {
                                // Zoom button or back/front camera switch is pressed.
                                dismissSettingPopup();
                            }
                            if (child.dispatchTouchEvent(event)) {
                                mPressedIndex = i;
                            }
                            invalidate();
                        } else if (action == MotionEvent.ACTION_UP) {
                            child.dispatchTouchEvent(event);
                            invalidate();
                            mPressedIndex = -1;
                        } else if (action == MotionEvent.ACTION_MOVE) {
                            // Dispatch the event if the location across a sector.
                            if (i != mPressedIndex) {
                                dismissSettingPopup();
                                // Cancel the previous one.
                                if (mPressedIndex != -1) {
                                    View cancelChild = getChildAt(mPressedIndex);
                                    event.setAction(MotionEvent.ACTION_CANCEL);
                                    cancelChild.dispatchTouchEvent(event);
                                    mPressedIndex = -1;
                                }
                                // Send down to the current one.
                                event.setAction(MotionEvent.ACTION_DOWN);
                                if (child.dispatchTouchEvent(event)) {
                                    mPressedIndex = i;
                                }
                                invalidate();
                            }
                            // The children do not care about ACTION_MOVE.
                            // Besides, the press state will be wrong
                            // because of View.pointInView.
                        }
                        return true;
                    }
                }
            }
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
    protected void onFinishInflate() {
        super.onFinishInflate();
        // The first view is shutter button.
        mShutterButton = getChildAt(0);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure all children.
        int childCount = getChildCount();
        int freeSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).measure(freeSpec, freeSpec);
        }

        // Measure myself. Add some buffer for highlight arc.
        int desiredWidth = mShutterButton.getMeasuredWidth() + HIGHLIGHT_WIDTH * 4;
        int desiredHeight = mShutterButton.getMeasuredHeight() + HIGHLIGHT_WIDTH * 4;
        int widthMode = MeasureSpec.getMode(widthSpec);
        int heightMode = MeasureSpec.getMode(heightSpec);
        int measuredWidth, measuredHeight;
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            measuredWidth = desiredWidth;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            measuredWidth = Math.min(desiredWidth, MeasureSpec.getSize(widthSpec));
        } else {  // MeasureSpec.EXACTLY
            measuredWidth = MeasureSpec.getSize(widthSpec);
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            measuredHeight = desiredHeight;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            measuredHeight = Math.min(desiredHeight, MeasureSpec.getSize(heightSpec));
        } else {  // MeasureSpec.EXACTLY
            measuredHeight = MeasureSpec.getSize(heightSpec);
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;

        // Layout the shutter button.
        int shutterButtonWidth = mShutterButton.getMeasuredWidth();
        int shutterButtonHeight = mShutterButton.getMeasuredHeight();
        // These numbers have to sync with the background of the shutter button.
        mShutterButtonRadius = Util.dpToPixel(74);
        mStrokeWidth = Util.dpToPixel(87);
        mCenterX = right - left - Util.dpToPixel(93);
        mCenterY = (bottom - top) / 2;
        mShutterButton.layout(right - left - shutterButtonWidth,
                mCenterY - shutterButtonHeight / 2,
                right - left,
                mCenterY + shutterButtonHeight - shutterButtonHeight / 2);

        // Layout the settings. The icons are spreaded on the left side of the
        // shutter button. So the angle starts from 90 to 270 degrees.
        if (count == 1) return;
        mWheelRadius = mShutterButtonRadius + mStrokeWidth * 0.5;
        double intervalDegrees = (count == 2) ? 90.0 : 180.0 / (count - 2);
        double initialDegrees = 90.0;
        int index = 0;
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view == mShutterButton) continue;
            double degree = initialDegrees + intervalDegrees * index;
            double radian = Math.toRadians(degree);
            int x = mCenterX + (int)(mWheelRadius * Math.cos(radian));
            int y = mCenterY - (int)(mWheelRadius * Math.sin(radian));
            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            view.layout(x - width / 2, y - height / 2, x + width / 2,
                    y + height / 2);
            index++;
        }

        // Store the radian intervals for each icon.
        mSectorInitialRadians = new double[count];
        mSectorInitialRadians[0] = Math.toRadians(
                initialDegrees - intervalDegrees / 2.0);
        for (int i = 1; i < count; i++) {
            mSectorInitialRadians[i] = mSectorInitialRadians[i - 1]
                    + Math.toRadians(intervalDegrees);
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
        if (selectedIndex >= 0 || mPressedIndex > 0) {
            int count = getChildCount();
            float initialDegrees = 90.0f;
            float intervalDegrees = (count <= 2) ? 0.0f : 180.0f / (count - 2);
            float degree;
            if (selectedIndex >= 0) {
                degree = initialDegrees + intervalDegrees * (selectedIndex - 1);
            } else {
                degree = initialDegrees + intervalDegrees * (mPressedIndex - 1);
            }
            mBackgroundPaint.setStrokeWidth(HIGHLIGHT_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            mBackgroundPaint.setColor(HIGHLIGHT_COLOR);
            canvas.drawArc(mBackgroundRect, -degree - HIGHLIGHT_DEGREE / 2,
                    HIGHLIGHT_DEGREE, false, mBackgroundPaint);
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
}
