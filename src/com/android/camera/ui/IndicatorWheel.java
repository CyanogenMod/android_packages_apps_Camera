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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;

import java.lang.Math;

/**
 * A view that contains shutter button and camera setting indicators. The
 * indicators are spreaded around the shutter button. The first child is always
 * the shutter button.
 */
public class IndicatorWheel extends ViewGroup {
    private static final String TAG = "IndicatorWheel";
    // The width of the edges on both sides of the wheel, which has less alpha.
    private static final float EDGE_STROKE_WIDTH = 6f;
    private static final int HIGHLIGHT_WIDTH = 4;
    private static final int HIGHLIGHT_DEGREE = 30;

    private final int HIGHLIGHT_COLOR;
    private final int DISABLED_COLOR;

    private Listener mListener;
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
    private int mSelectedIndex = -1;

    static public interface Listener {
        public void onIndicatorClicked(int index);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public IndicatorWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        HIGHLIGHT_COLOR = resources.getColor(R.color.review_control_pressed_color);
        DISABLED_COLOR = resources.getColor(R.color.icon_disabled_color);
        setWillNotDraw(false);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
        mBackgroundPaint.setAntiAlias(true);

        mBackgroundRect = new RectF();
    }

    public void unselectIndicator() {
        setHighlight(mSelectedIndex, false);
        mSelectedIndex = -1;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

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
            // Ignore the event if it's too far from the shutter button.
            if ((radius - mWheelRadius) > mStrokeWidth) return false;

            double delta = Math.atan2(dy, dx);
            if (delta < 0) delta += Math.PI * 2;
            // Check which sector is pressed.
            if (delta > mSectorInitialRadians[0]) {
                for (int i = 1; i < count; i++) {
                    if (delta < mSectorInitialRadians[i]) {
                        // Do nothing if scene mode overrides the setting.
                        View child = getChildAt(i);
                        if (child instanceof IndicatorButton) {
                            if (((IndicatorButton) child).isOverridden()) {
                                return true;
                            }
                        }
                        setHighlight(mSelectedIndex, false);
                        mSelectedIndex = i - 1;
                        setHighlight(mSelectedIndex, true);
                        invalidate();
                        mListener.onIndicatorClicked(i - 1);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // The first view is shutter button.
        mShutterButton = getChildAt(0);
    }

    public void removeIndicators() {
        // Remove everything but the shutter button.
        int count = getChildCount();
        if (count > 1) {
            removeViews(1, count - 1);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure all children.
        int childCount = getChildCount();
        int freeSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).measure(freeSpec, freeSpec);
        }

        // Measure myself.
        int desiredWidth = (int)(mShutterButton.getMeasuredWidth() * 3);
        int desiredHeight = (int)(mShutterButton.getMeasuredHeight() * 4.5) + 2;
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
        mShutterButtonRadius = shutterButtonWidth / 2.0;
        int shutterButtonHeight = mShutterButton.getMeasuredHeight();
        mStrokeWidth = (int) (mShutterButtonRadius * 1.05);
        int innerRadius = (int) (mShutterButtonRadius + mStrokeWidth * 0.8);
        mCenterX = right - left - innerRadius;
        mCenterY = (bottom - top) / 2 + 3;
        mShutterButton.layout(mCenterX - shutterButtonWidth / 2,
                mCenterY - shutterButtonHeight / 2,
                mCenterX + shutterButtonWidth / 2,
                mCenterY + shutterButtonHeight / 2);

        // Layout the settings. The icons are spreaded on the left side of the
        // shutter button. So the angle starts from 90 to 270 degrees.
        if (count == 1) return;
        mWheelRadius = innerRadius + mStrokeWidth * 0.5;
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

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw highlight.
        float delta = mStrokeWidth * 0.5f;
        float radius = (float) (mWheelRadius + mStrokeWidth * 0.5 + EDGE_STROKE_WIDTH);
        mBackgroundRect.set((float)(mCenterX - radius),
                (float)(mCenterY - radius),
                (float)(mCenterX + radius),
                (float)(mCenterY + radius));
        if (mSelectedIndex >= 0) {
            int count = getChildCount();
            float initialDegrees = 90.0f;
            float intervalDegrees = (count <= 2) ? 0.0f : 180.0f / (count - 2);
            float degree = initialDegrees + intervalDegrees * mSelectedIndex;
            mBackgroundPaint.setStrokeWidth(HIGHLIGHT_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
            mBackgroundPaint.setColor(HIGHLIGHT_COLOR);
            canvas.drawArc(mBackgroundRect, -degree - HIGHLIGHT_DEGREE / 2,
                    HIGHLIGHT_DEGREE, false, mBackgroundPaint);
        }

        super.onDraw(canvas);
    }

    public void updateIndicator(int index) {
        IndicatorButton indicator = (IndicatorButton) getChildAt(index + 1);
        indicator.reloadPreference();
    }

    // Scene mode may override other camera settings (ex: flash mode).
    public void overrideSettings(String key, String value) {
        int count = getChildCount();
        for (int j = 1; j < count; j++) {
            View v = getChildAt(j);
            if (v instanceof IndicatorButton) {  // skip the button of "other settings"
                IndicatorButton indicator = (IndicatorButton) v;
                if (key.equals(indicator.getKey())) {
                    indicator.overrideSettings(value);
                }
            }
        }
    }

    // Sets/unsets highlight on the specified setting icon
    private void setHighlight(int index, boolean enabled) {
        if ((index < 0) || (index >= getChildCount() - 1)) return;
        ImageView child = (ImageView) getChildAt(index + 1);
        if (enabled) {
            child.setColorFilter(HIGHLIGHT_COLOR);
        } else {
            child.clearColorFilter();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int count = getChildCount();
        if (enabled) {
            for (int i = 1; i < count; i++) {
                ((ImageView) getChildAt(i)).clearColorFilter();
            }
        } else {
            for (int i = 1; i < count; i++) {
                ((ImageView) getChildAt(i)).setColorFilter(DISABLED_COLOR);
            }
        }
    }
}
