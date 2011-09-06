/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.camera.R;
import com.android.camera.Util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A view that contains camera zoom control and its layout.
 */
public class ZoomControlWheel extends ZoomControl {
    private static final String TAG = "ZoomControlWheel";
    private static final int HIGHLIGHT_WIDTH = 4;
    private static final int HIGHLIGHT_DEGREES = 30;
    private static final int TRAIL_WIDTH = 2;
    private static final int ZOOM_IN_ICON_DEGREES = 60;
    private static final int ZOOM_OUT_ICON_DEGREES = 300;
    private static final int DEFAULT_SLIDER_POSITION = 180;
    private static final int MAX_SLIDER_ANGLE =
            ZOOM_OUT_ICON_DEGREES - (HIGHLIGHT_DEGREES / 2);
    private static final int MIN_SLIDER_ANGLE =
            ZOOM_IN_ICON_DEGREES + (HIGHLIGHT_DEGREES / 2);
    private static final float EDGE_STROKE_WIDTH = 6f;
    private static final double BUFFER_RADIANS = Math.toRadians(HIGHLIGHT_DEGREES / 2);
    private double mSliderRadians = Math.toRadians(DEFAULT_SLIDER_POSITION);

    private final int HIGHLIGHT_COLOR;
    private final int TRAIL_COLOR;

    // The center of the shutter button.
    private int mCenterX, mCenterY;
    // The width of the wheel stroke.
    private int mStrokeWidth;
    private double mShutterButtonRadius;
    private double mWheelRadius;
    private Paint mBackgroundPaint;
    private RectF mBackgroundRect;

    public ZoomControlWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
        mBackgroundPaint.setAntiAlias(true);

        mBackgroundRect = new RectF();
        Resources resources = context.getResources();
        HIGHLIGHT_COLOR = resources.getColor(R.color.review_control_pressed_color);
        TRAIL_COLOR = resources.getColor(R.color.icon_disabled_color);

        mShutterButtonRadius = IndicatorControlWheelContainer.SHUTTER_BUTTON_RADIUS;
        mStrokeWidth = Util.dpToPixel(IndicatorControlWheelContainer.STROKE_WIDTH);
        mWheelRadius = mShutterButtonRadius + mStrokeWidth * 0.5;
        super.setZoomStep(1); // one zoom level at a time
    }

    private void performZoom() {
        if (mSliderRadians > (Math.PI + BUFFER_RADIANS)) {
            super.performZoom(ZOOM_OUT);
        } else if (mSliderRadians < (Math.PI - BUFFER_RADIANS)) {
            super.performZoom(ZOOM_IN);
        } else {
            super.performZoom(ZOOM_STOP);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;
        int action = event.getAction();

        double dx = event.getX() - mCenterX;
        double dy = mCenterY - event.getY();
        double radius = Math.sqrt(dx * dx + dy * dy);
        // Ignore the event if too far from the shutter button.
        mSliderRadians = Math.atan2(dy, dx);
        if (mSliderRadians < 0) mSliderRadians += Math.PI * 2;
        if (mSliderRadians > (Math.PI + BUFFER_RADIANS)) {
            mSliderPosition = 1;
        } else {
            mSliderPosition = (mSliderRadians < (Math.PI - BUFFER_RADIANS)) ? -1 : 0;
        }
        // We assume the slider button is pressed all the time when the
        // zoom control is active. So we take care of the following events
        // only.
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                closeZoomControl();
                break;
            case MotionEvent.ACTION_MOVE:
                performZoom();
                requestLayout();
        }
        return true;
    }

    @Override
    public void startZoomControl() {
        super.startZoomControl();
        mSliderRadians = Math.toRadians(DEFAULT_SLIDER_POSITION);
    }

    private void layoutIcon(View view, double radian) {
        int x = mCenterX + (int)(mWheelRadius * Math.cos(radian));
        int y = mCenterY - (int)(mWheelRadius * Math.sin(radian));
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        view.layout(x - width / 2, y - height / 2, x + width / 2,
                y + height / 2);
    }

    private double getSliderDrawAngle() {
        double sliderAngle = mSliderRadians;
        if (sliderAngle > Math.toRadians(MAX_SLIDER_ANGLE)) {
            return Math.toRadians(MAX_SLIDER_ANGLE);
        } else if (sliderAngle < Math.toRadians(MIN_SLIDER_ANGLE)) {
            return Math.toRadians(MIN_SLIDER_ANGLE);
        }
        return sliderAngle;
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        mCenterX = right - left - Util.dpToPixel(
                IndicatorControlWheelContainer.FULL_WHEEL_RADIUS);
        mCenterY = (bottom - top) / 2;
        layoutIcon(mZoomIn, Math.toRadians(ZOOM_IN_ICON_DEGREES));
        layoutIcon(mZoomOut, Math.toRadians(ZOOM_OUT_ICON_DEGREES));
        layoutIcon(mZoomSlider, getSliderDrawAngle());
   }

    private double getZoomIndexAngle() {
        if (mZoomMax == 0) return Math.PI;
        return Math.toRadians(MAX_SLIDER_ANGLE -
                (MAX_SLIDER_ANGLE - MIN_SLIDER_ANGLE) * mZoomIndex / mZoomMax);
    }

    private void drawArc(Canvas canvas, int startAngle, int sweepAngle,
            double radius, int color, int width) {
        mBackgroundRect.set((float) (mCenterX - radius), (float) (mCenterY - radius),
                (float) (mCenterX + radius), (float) (mCenterY + radius));
        mBackgroundPaint.setStrokeWidth(width);
        mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        mBackgroundPaint.setColor(color);
        canvas.drawArc(mBackgroundRect, startAngle, sweepAngle, false, mBackgroundPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw zoom index highlight.
        float radius = (float) (mWheelRadius + mStrokeWidth * 0.5 + EDGE_STROKE_WIDTH);
        int degree = (int) Math.toDegrees(getZoomIndexAngle());
        drawArc(canvas, (-degree - HIGHLIGHT_DEGREES / 2), HIGHLIGHT_DEGREES,
                radius, HIGHLIGHT_COLOR, HIGHLIGHT_WIDTH);
        // Draw the slider trail.
        drawArc(canvas, -MAX_SLIDER_ANGLE, (MAX_SLIDER_ANGLE - MIN_SLIDER_ANGLE),
                mWheelRadius, TRAIL_COLOR, TRAIL_WIDTH);
        super.onDraw(canvas);
    }
}
