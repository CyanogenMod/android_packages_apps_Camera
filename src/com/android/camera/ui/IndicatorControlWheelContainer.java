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

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;
import com.android.camera.Util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * On the tablet UI, we have IndicatorControlWheelContainer which contains a
 * ShutterButton, an IndicatorControlWheel(which combines first-level and
 * second-level indicators and a ZoomControlWheel).
 */
public class IndicatorControlWheelContainer extends IndicatorControlContainer {
    public static final int STROKE_WIDTH = 87;
    public static final int SHUTTER_BUTTON_RADIUS = 74;
    public static final int FULL_WHEEL_RADIUS = 93;

    private static final String TAG = "IndicatorControlWheelContainer";

    private View mShutterButton;
    private double mShutterButtonRadius;
    private IndicatorControlWheel mIndicatorControlWheel;
    private int mCenterX, mCenterY;

    public IndicatorControlWheelContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mShutterButton = findViewById(R.id.shutter_button);
        mShutterButtonRadius = Util.dpToPixel(SHUTTER_BUTTON_RADIUS);

        mIndicatorControlWheel = (IndicatorControlWheel) findViewById(
                R.id.indicator_control_wheel);
    }

    public void initialize(Context context, PreferenceGroup group,
            boolean isZoomSupported, String[] keys, String[] otherSettingKeys) {
        mIndicatorControlWheel.initialize(context, group, isZoomSupported,
                keys, otherSettingKeys);
    }

    public void onIndicatorEvent(int event) {
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
            if (mIndicatorControlWheel.getVisibility() == View.VISIBLE) {
                mIndicatorControlWheel.onTouchOutBound();
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                return mShutterButton.dispatchTouchEvent(event);
            }
            return false;
        }

        if (mShutterButton.isPressed()) {
            // Send cancel to the shutter button if it was pressed.
            event.setAction(MotionEvent.ACTION_CANCEL);
            mShutterButton.dispatchTouchEvent(event);
            return true;
        }

        return mIndicatorControlWheel.dispatchTouchEvent(event);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {

        // Layout the shutter button.
        int shutterButtonWidth = mShutterButton.getMeasuredWidth();
        int shutterButtonHeight = mShutterButton.getMeasuredHeight();
        mCenterX = right - left - Util.dpToPixel(FULL_WHEEL_RADIUS);
        mCenterY = (bottom - top) / 2;
        mShutterButton.layout(right - left - shutterButtonWidth,
                mCenterY - shutterButtonHeight / 2,
                right - left,
                mCenterY + shutterButtonHeight - shutterButtonHeight / 2);
        // Layout the control wheel.
        mIndicatorControlWheel.layout(0, 0, right - left, bottom - top);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure all children.
        int freeSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        mShutterButton.measure(freeSpec, freeSpec);
        mIndicatorControlWheel.measure(freeSpec, freeSpec);

        // Measure myself. Add some buffer for highlight arc.
        int desiredWidth = mShutterButton.getMeasuredWidth()
                + IndicatorControlWheel.HIGHLIGHT_WIDTH * 4;
        int desiredHeight = mShutterButton.getMeasuredHeight()
                + IndicatorControlWheel.HIGHLIGHT_WIDTH * 4;
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
    public void setListener(OnPreferenceChangedListener listener) {
        mIndicatorControlWheel.setListener(listener);
    }

    @Override
    public void reloadPreferences() {
        mIndicatorControlWheel.reloadPreferences();
    }

    @Override
    public View getActiveSettingPopup() {
        return mIndicatorControlWheel.getActiveSettingPopup();
    }

    @Override
    public boolean dismissSettingPopup(boolean multiLevel) {
        return mIndicatorControlWheel.dismissSettingPopup(multiLevel);
    }

    @Override
    public void setOrientation(int orientation) {
        mIndicatorControlWheel.setOrientation(orientation);
    }

    public void startTimeLapseAnimation(int timeLapseInterval, long startTime) {
        mIndicatorControlWheel.startTimeLapseAnimation(
                timeLapseInterval, startTime);
    }

    public void stopTimeLapseAnimation() {
        mIndicatorControlWheel.stopTimeLapseAnimation();
    }

    @Override
    public void setEnabled(boolean enabled) {
        mIndicatorControlWheel.setEnabled(enabled);
    }

    @Override
    public void enableZoom(boolean enabled) {
        mIndicatorControlWheel.enableZoom(enabled);
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        mIndicatorControlWheel.overrideSettings(keyvalues);
    }

    @Override
    public void dismissSecondLevelIndicator() {
        mIndicatorControlWheel.dismissSecondLevelIndicator();
    }
}
