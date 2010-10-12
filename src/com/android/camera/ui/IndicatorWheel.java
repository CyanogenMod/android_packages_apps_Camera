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

import com.android.camera.ComboPreferences;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;

import java.lang.Math;

/**
 * A view that contains camera settings and shutter buttons. The settings are
 * spreaded around the shutter button.
 */
public class IndicatorWheel extends ViewGroup {
    private static final String TAG = "IndicatorWheel";
    private Listener mListener;
    private int mCenterX, mCenterY;
    private double mShutterButtonRadius;
    private double mSectorInitialRadians[];

    static public interface Listener {
        public void onIndicatorClicked(int index);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int count = getChildCount();
        if (mListener == null || count <= 1) return false;

        // Check if any setting is pressed.
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE) {
            double dx = event.getX() - mCenterX;
            double dy = mCenterY - event.getY();
            double radius = Math.sqrt(dx * dx + dy * dy);
            // Ignore the event if it's too near to the shutter button.
            if (radius < mShutterButtonRadius) return false;

            double intervalDegrees = 180.0 / (count - 2);
            double delta = Math.atan2(dy, dx);
            if (delta < 0) delta += Math.PI * 2;
            // Check which sector is pressed.
            if (delta > mSectorInitialRadians[0]) {
                for (int i = 1; i < count; i++) {
                    if (delta < mSectorInitialRadians[i]) {
                        mListener.onIndicatorClicked(i - 1);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public IndicatorWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure all children.
        int freeSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(freeSpec, freeSpec);
        }

        // Measure myself.
        View shutterButton = getChildAt(0);
        int desiredWidth = (int)(shutterButton.getMeasuredWidth() * 2.5);
        int desiredHeight = (int)(shutterButton.getMeasuredHeight() * 2.5);
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
        View shutterButton = findViewById(R.id.shutter_button);
        int width = shutterButton.getMeasuredWidth();
        mShutterButtonRadius = width / 2.0 + 10;
        int height = shutterButton.getMeasuredHeight();
        mCenterX = (right - left) - width / 2;
        mCenterY = (bottom - top) / 2;
        shutterButton.layout(mCenterX - width / 2, mCenterY - height / 2,
                mCenterX + width / 2, mCenterY + height / 2);

        // Layout the settings. The icons are spreaded on the left side of the
        // shutter button. So the angle starts from 90 to 270 degrees.
        if (count == 1) return;
        double radius = shutterButton.getMeasuredWidth();
        double intervalDegrees = 180.0 / (count - 2);
        double initialDegrees = 90.0;
        int index = 0;
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view == shutterButton) continue;
            double degree = initialDegrees + intervalDegrees * index;
            double radian = Math.toRadians(degree);
            int x = mCenterX + (int)(radius * Math.cos(radian));
            int y = mCenterY - (int)(radius * Math.sin(radian));
            width = view.getMeasuredWidth();
            height = view.getMeasuredHeight();
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

    public void updateIndicator(int index) {
        IndicatorButton indicator = (IndicatorButton) getChildAt(index + 1);
        indicator.reloadPreference();
    }
}
