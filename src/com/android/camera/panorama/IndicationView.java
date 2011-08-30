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

package com.android.camera.panorama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

class IndicationView extends View {
    private static final String TAG = "IndicationView";
    private static final int PAN_DIRECTION_NONE = 0;
    private static final int PAN_DIRECTION_LEFT = 1;
    private static final int PAN_DIRECTION_RIGHT = 2;
    private int mSweepAngle = 0;
    private int mStartAngle = 0;
    private int mMaxSweepAngle = 0;
    private int mLeftMostAngle = 0;
    private int mRightMostAngle = 0;
    private int mAngleOffset = 0;
    private int mPanningDirection = 0;
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mSweepAreaPaint = new Paint();
    private final Paint mIndicatorPaint = new Paint();
    private RectF mRect;

    public IndicationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSweepAreaPaint.setStyle(Paint.Style.FILL);
        mSweepAreaPaint.setColor(Color.RED);

        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(Color.BLUE);

        mIndicatorPaint.setStyle(Paint.Style.FILL);
        mIndicatorPaint.setColor(0xFFFFBBBB);

        mRect = new RectF();
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mRect.set(0, 0, w, h * 2);
    }

    public void setMaxSweepAngle(int angle) {
        mStartAngle = -90 - angle / 2;
        mMaxSweepAngle = angle;
    }

    public void setSweepAngle(int angle) {
        // The panning direction will be decided after user pan more than 10 degrees in one
        // direction.
        if (mPanningDirection == PAN_DIRECTION_NONE) {
            if (angle > 10) {
                mPanningDirection = PAN_DIRECTION_RIGHT;
                mAngleOffset = -mMaxSweepAngle / 2;
            }
            if (angle < -10) {
                mPanningDirection = PAN_DIRECTION_LEFT;
                mAngleOffset = mMaxSweepAngle / 2;
            }
        } else {
            mSweepAngle = angle;
            if (mPanningDirection == PAN_DIRECTION_RIGHT) {
                // Bounded by the left most angle.
                mSweepAngle = Math.max(mLeftMostAngle, mSweepAngle);
                // Bounded by the max angle in the right direction.
                mSweepAngle = Math.min(mMaxSweepAngle, mSweepAngle);
                // The right most angle is adjusted.
                mRightMostAngle = Math.max(mRightMostAngle, mSweepAngle);
            }
            if (mPanningDirection == PAN_DIRECTION_LEFT) {
                // Bounded by the right most angle.
                mSweepAngle = Math.min(mRightMostAngle, mSweepAngle);
                // Bounded by the max angle in the left direction.
                mSweepAngle = Math.max(-mMaxSweepAngle, mSweepAngle);
                // The left most angle is adjusted.
                mLeftMostAngle = Math.min(mLeftMostAngle, mSweepAngle);
            }
        }
    }

    public void resetAngles() {
        mSweepAngle = 0;
        mLeftMostAngle = 0;
        mRightMostAngle = 0;
        mAngleOffset = 0;
        mPanningDirection = PAN_DIRECTION_NONE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // the background
        canvas.drawArc(mRect, mStartAngle, mMaxSweepAngle, true, mBackgroundPaint);
        if (mPanningDirection != PAN_DIRECTION_NONE) {
            // the spanned area
            canvas.drawArc(mRect, -90 + mLeftMostAngle + mAngleOffset,
                           mRightMostAngle - mLeftMostAngle, true, mSweepAreaPaint);
            // the indication line
            canvas.drawArc(mRect, -91 + mSweepAngle + mAngleOffset, 2, true, mIndicatorPaint);
        }
    }
}
