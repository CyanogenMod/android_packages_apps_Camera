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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.PreferenceGroup;
import com.android.camera.R;
import com.android.camera.Util;

/**
 * A view that contains camera setting indicators in two levels. The first-level
 * indicators including the zoom, camera picker and second-level control.
 * The second-level indicators are merely for the camera settings.
 */
public class IndicatorControlWheel extends IndicatorControl implements
        View.OnClickListener {
    public static final int HIGHLIGHT_WIDTH = 4;

    @SuppressWarnings("unused")
    private static final String TAG = "IndicatorControlWheel";
    private static final int HIGHLIGHT_DEGREES = 30;
    private static final double HIGHLIGHT_RADIANS = Math.toRadians(HIGHLIGHT_DEGREES);

    // The following angles are based in the zero degree on the right, measured
    // counter-clockwise.
    //
    // For landscape orientation:
    //                   90
    //             ______|______
    //            |      |      |
    //    180 <---|------|------|---> 0
    //            |______|______|
    //                   |
    //                  270
    //
    // For portrait orientation:
    //                   90
    //                ___|___
    //               |   |   |
    //               |   |   |
    //       180 <---|---|---|---> 0
    //               |   |   |
    //               |___|___|
    //                   |
    //                  270
    //
    // Here we have the CameraPicker, ZoomControl and the Settings icons in the
    // first-level. For consistency, we treat the zoom control as one of the
    // indicator buttons but it needs additional efforts for rotation animation.
    // For second-level indicators, the indicators are located evenly between start
    // and end angle. In addition, these indicators for the second-level hidden
    // in the same wheel with larger angle values are visible after rotation.
    private static final int LANDSCAPE_FIRST_LEVEL_START_DEGREES = 74;
    private static final int LANDSCAPE_FIRST_LEVEL_END_DEGREES = 286;
    private static final int LANDSCAPE_SECOND_LEVEL_START_DEGREES = 60;
    private static final int LANDSCAPE_SECOND_LEVEL_END_DEGREES = 300;
    private static final int LANDSCAPE_MAX_ZOOM_CONTROL_DEGREES = 264;
    private static final int LANDSCAPE_CLOSE_ICON_DEFAULT_DEGREES = 315;

    // Will be calculated based on orientation.
    private final int FIRST_LEVEL_START_DEGREES;
    private final int FIRST_LEVEL_END_DEGREES;
    private final int SECOND_LEVEL_START_DEGREES;
    private final int SECOND_LEVEL_END_DEGREES;
    private final int MAX_ZOOM_CONTROL_DEGREES;
    private final int CLOSE_ICON_DEFAULT_DEGREES;

    private static final int ANIMATION_TIME = 300; // milliseconds

    // The width of the edges on both sides of the wheel, which has less alpha.
    private static final float EDGE_STROKE_WIDTH = 6f;
    private static final int TIME_LAPSE_ARC_WIDTH = 6;

    private final int HIGHLIGHT_COLOR;
    private final int HIGHLIGHT_FAN_COLOR;
    private final int TIME_LAPSE_ARC_COLOR;

    // The center of the shutter button.
    private int mCenterX, mCenterY;
    // The width of the wheel stroke.
    private int mStrokeWidth;  // in pixel
    private double mShutterButtonRadius;  // in pixel
    private double mWheelRadius;  // in pixel
    private double mChildRadians[];
    private Paint mBackgroundPaint;
    private RectF mBackgroundRect;
    private Path mFanPath;
    // The index of the child that is being pressed. -1 means no child is being
    // pressed.
    private int mPressedIndex = -1;

    // Time lapse recording variables.
    private int mTimeLapseInterval;  // in ms
    private long mRecordingStartTime = 0;
    private long mNumberOfFrames = 0;

    // Remember the last event for event cancelling if out of bound.
    private MotionEvent mLastMotionEvent;

    private ImageView mSecondLevelIcon;
    private ImageView mCloseIcon;

    // Variables for animation.
    private long mAnimationStartTime;
    private boolean mInAnimation = false;
    private Handler mHandler = new Handler();
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            requestLayout();
        }
    };

    // Variables for level control.
    private int mCurrentLevel = 0;
    private int mSecondLevelStartIndex = -1;
    private double mStartVisibleRadians[] = new double[2];
    private double mEndVisibleRadians[] = new double[2];
    private double mSectorRadians[] = new double[2];
    private double mTouchSectorRadians[] = new double[2];

    private boolean mInitialized;

    public IndicatorControlWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        HIGHLIGHT_COLOR = resources.getColor(R.color.review_control_pressed_color);
        HIGHLIGHT_FAN_COLOR = resources.getColor(R.color.review_control_pressed_fan_color);
        TIME_LAPSE_ARC_COLOR = resources.getColor(R.color.time_lapse_arc);

        setWillNotDraw(false);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
        mBackgroundPaint.setAntiAlias(true);

        mBackgroundRect = new RectF();
        mFanPath = new Path();

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            // tablet in landscape orientation
            FIRST_LEVEL_START_DEGREES = LANDSCAPE_FIRST_LEVEL_START_DEGREES;
            FIRST_LEVEL_END_DEGREES = LANDSCAPE_FIRST_LEVEL_END_DEGREES;
            SECOND_LEVEL_START_DEGREES = LANDSCAPE_SECOND_LEVEL_START_DEGREES;
            SECOND_LEVEL_END_DEGREES = LANDSCAPE_SECOND_LEVEL_END_DEGREES;
            MAX_ZOOM_CONTROL_DEGREES = LANDSCAPE_MAX_ZOOM_CONTROL_DEGREES;
            CLOSE_ICON_DEFAULT_DEGREES = LANDSCAPE_CLOSE_ICON_DEFAULT_DEGREES;
        } else {
            // tablet in portrait orientation
            FIRST_LEVEL_START_DEGREES = LANDSCAPE_FIRST_LEVEL_START_DEGREES - 90;
            FIRST_LEVEL_END_DEGREES = LANDSCAPE_FIRST_LEVEL_END_DEGREES - 90;
            SECOND_LEVEL_START_DEGREES = LANDSCAPE_SECOND_LEVEL_START_DEGREES - 90;
            SECOND_LEVEL_END_DEGREES = LANDSCAPE_SECOND_LEVEL_END_DEGREES - 90;
            MAX_ZOOM_CONTROL_DEGREES = LANDSCAPE_MAX_ZOOM_CONTROL_DEGREES - 90;
            CLOSE_ICON_DEFAULT_DEGREES = LANDSCAPE_CLOSE_ICON_DEFAULT_DEGREES - 90;
        }
    }

    private int getChildCountByLevel(int level) {
        // Get current child count by level.
        if (level == 1) {
            return (getChildCount() - mSecondLevelStartIndex);
        } else {
            return mSecondLevelStartIndex;
        }
    }

    private void changeIndicatorsLevel() {
        mPressedIndex = -1;
        dismissSettingPopup();
        mInAnimation = true;
        mAnimationStartTime = SystemClock.uptimeMillis();
        requestLayout();
    }

    @Override
    public void onClick(View view) {
        changeIndicatorsLevel();
    }

    public void initialize(Context context, PreferenceGroup group,
            boolean isZoomSupported, String[] keys, String[] otherSettingKeys) {
        mShutterButtonRadius = Util.dpToPixel(
                IndicatorControlWheelContainer.SHUTTER_BUTTON_RADIUS);
        mStrokeWidth = Util.dpToPixel(IndicatorControlWheelContainer.STROKE_WIDTH);
        mWheelRadius = mShutterButtonRadius + mStrokeWidth * 0.5;

        setPreferenceGroup(group);

        // Add the ZoomControl if supported.
        initializeZoomControl(isZoomSupported);

        // Add CameraPicker.
        initializeCameraPicker();

        // Add second-level Indicator Icon.
        if (mSecondLevelIcon == null) {
            mSecondLevelIcon = addImageButton(context,
                    R.drawable.ic_settings_holo_light, true);
            mSecondLevelIcon.setId(R.id.second_level_indicator);
            mSecondLevelStartIndex = getChildCount();
        }

        // Add second-level buttons.
        if (mCloseIcon == null) {
            mCloseIcon = addImageButton(context,
                    R.drawable.btn_wheel_close_settings, false);
        }
        // Remove all the views after close icon. This happens when switching
        // between front and back cameras.
        int index = indexOfChild(mCloseIcon) + 1;
        int count = getChildCount() - index;
        if (count > 0) removeControls(index, count);

        addControls(keys, otherSettingKeys);

        // The angle(in radians) of each icon for touch events.
        mChildRadians = new double[getChildCount()];
        presetFirstLevelChildRadians();
        presetSecondLevelChildRadians();

        // Do not grey out the icons when taking a picture.
        setupFilter(mCurrentMode != MODE_CAMERA);
        mInitialized = true;
        requestLayout();
    }

    private ImageView addImageButton(Context context, int resourceId, boolean rotatable) {
        ImageView view;
        if (rotatable) {
            view = new RotateImageView(context);
        } else {
            view = new TwoStateImageView(context);
        }
        view.setImageResource(resourceId);
        view.setOnClickListener(this);
        addView(view);
        return view;
    }

    private int getTouchIndicatorIndex(double delta) {
        // The delta is the angle of touch point in radians.
        if (mInAnimation) return -1;
        int count = getChildCountByLevel(mCurrentLevel);
        if (count == 0) return -1;
        int sectors = count - 1;
        int startIndex = (mCurrentLevel == 0) ? 0 : mSecondLevelStartIndex;
        int endIndex;
        double halfTouchSectorRadians = mTouchSectorRadians[mCurrentLevel];
        // The effective touch area is defined by the range [startRadians,
        // endRadians], counter-clockwise.
        double startRadians = 0;
        if (mCurrentLevel == 0) {
            // Skip the first component if it is zoom control, as we will
            // deal with it specifically.
            if (mZoomControl != null) {
                startIndex++;
                if (mCameraPicker == null) {
                    startRadians = Math.toRadians(FIRST_LEVEL_START_DEGREES)
                            + halfTouchSectorRadians;
                } else {
                    startRadians = mChildRadians[startIndex] - halfTouchSectorRadians;
                }
            } else {
                startRadians = mChildRadians[startIndex] - halfTouchSectorRadians;
            }
            endIndex = mSecondLevelStartIndex - 1;
        } else {
            endIndex = getChildCount() - 1;
            startRadians = mChildRadians[startIndex] - halfTouchSectorRadians;
        }

        if (startRadians < 0) startRadians += Math.PI * 2;

        double endRadians = mChildRadians[endIndex] + halfTouchSectorRadians;
        // True if the touch point is in the effective touch area.
        boolean touchInRange;
        if (startRadians > endRadians) {
            // range crosses 0 degree
            touchInRange = (delta >= startRadians || delta <= endRadians);
        } else {
            touchInRange = (delta >= startRadians && delta <= endRadians);
        }

        if (touchInRange) {
            // For portrait orientation, we offset the angles by 90 degrees to
            // make it share the same logic with landscape orientation.
            final int offset;
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                offset = 0;
            } else {
                offset = 90;
                delta = rebase(delta, offset);
            }

            int index = 0;
            if (mCurrentLevel == 1) { // for second-level indicators
                index = (int) ((delta - rebase(mChildRadians[startIndex], offset))
                        / mSectorRadians[mCurrentLevel]);
                // greater than the center of ending indicator
                if (index > sectors) return (startIndex + sectors);
                // less than the center of starting indicator
                if (index < 0) return startIndex;
            }
            if ((mCurrentLevel == 0) && (mCameraPicker == null)) {
                if (delta >= (rebase(mChildRadians[startIndex], offset)
                        - halfTouchSectorRadians)) {
                    return (startIndex + index);
                }
            } else {
                if (delta <= (rebase(mChildRadians[startIndex + index], offset)
                        + halfTouchSectorRadians)) {
                    return (startIndex + index);
                }
                if (delta >= (rebase(mChildRadians[startIndex + index + 1], offset)
                        - halfTouchSectorRadians)) {
                    return (startIndex + index + 1);
                }
            }

            // It must be for zoom control if the touch event is in the visible
            // range and not for other indicator buttons.
            if ((mCurrentLevel == 0) && (mZoomControl != null)) return 0;
        }
        return -1;
    }

    /*
     * Calculate the new value of the given angle based on the given offset to
     * the 0 degree position.
     *
     * @param oldAngle the old value of the angle to be calculated. The unit is
     *                 in radians, measured counter-clockwise from 0 degree
     *                 position.
     * @param offset   the angle offset to the 0 degree. The unit is in radians,
     *                 towards clockwise.
     */
    private double rebase(double oldAngle, int offset) {
        return (oldAngle + offset) % (Math.PI * 2);
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
        double centerToTouchPoint = Math.sqrt(dx * dx + dy * dy);

        // Ignore the event if too far from the shutter button.
        if ((centerToTouchPoint <= (mWheelRadius + mStrokeWidth))
                && (centerToTouchPoint > mShutterButtonRadius)) {
            double delta = Math.atan2(dy, dx);
            if (delta < 0) delta += Math.PI * 2;
            int index = getTouchIndicatorIndex(delta);
            // Check if the touch event is for zoom control.
            if ((mZoomControl != null) && (index == 0)) {
                mZoomControl.dispatchTouchEvent(event);
            }
            // Move over from one indicator to another.
            if ((index != mPressedIndex) || (action == MotionEvent.ACTION_DOWN)) {
                if (mPressedIndex != -1) {
                    injectMotionEvent(mPressedIndex, event, MotionEvent.ACTION_CANCEL);
                } else {
                    // Cancel the popup if it is different from the selected.
                    if (getSelectedIndicatorIndex() != index) dismissSettingPopup();
                }
                if ((index != -1) && (action == MotionEvent.ACTION_MOVE)) {
                    if (mCurrentLevel != 0) {
                        injectMotionEvent(index, event, MotionEvent.ACTION_DOWN);
                    }
                }
            }
            if ((index != -1) && (action != MotionEvent.ACTION_MOVE)) {
                getChildAt(index).dispatchTouchEvent(event);
            }
            // Do not highlight the CameraPicker or Settings icon if we
            // touch from the zoom control to one of them.
            if ((mCurrentLevel == 0) && (index != 0)
                    && (action == MotionEvent.ACTION_MOVE)) {
                return true;
            }
            // Once the button is up, reset the press index.
            mPressedIndex = (action == MotionEvent.ACTION_UP) ? -1 : index;
            invalidate();
            return true;
        }
        // The event is not on any of the child.
        onTouchOutBound();
        return false;
    }

    private void rotateWheel() {
        int totalDegrees = CLOSE_ICON_DEFAULT_DEGREES - SECOND_LEVEL_START_DEGREES;
        int startAngle = ((mCurrentLevel == 0) ? CLOSE_ICON_DEFAULT_DEGREES
                                               : SECOND_LEVEL_START_DEGREES);
        if (mCurrentLevel == 0) totalDegrees = -totalDegrees;

        int elapsedTime = (int) (SystemClock.uptimeMillis() - mAnimationStartTime);
        if (elapsedTime >= ANIMATION_TIME) {
            elapsedTime = ANIMATION_TIME;
            mCurrentLevel = (mCurrentLevel == 0) ? 1 : 0;
            mInAnimation = false;
        }

        int expectedAngle = startAngle + (totalDegrees * elapsedTime / ANIMATION_TIME);
        double increment = Math.toRadians(expectedAngle)
                - mChildRadians[mSecondLevelStartIndex];
        for (int i = 0 ; i < getChildCount(); ++i) mChildRadians[i] += increment;
        // We also need to rotate the zoom control wheel as well.
        if (mZoomControl != null) {
            mZoomControl.rotate(mChildRadians[0]
                    - Math.toRadians(MAX_ZOOM_CONTROL_DEGREES));
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (!mInitialized) return;
        if (mInAnimation) {
            rotateWheel();
            mHandler.post(mRunnable);
        }

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            mCenterX = right - left - Util.dpToPixel(
                    IndicatorControlWheelContainer.WHEEL_CENTER_TO_SECANT);
            mCenterY = (bottom - top) / 2;
        } else {
            mCenterX = (right - left) / 2;
            mCenterY = bottom - top - Util.dpToPixel(
                    IndicatorControlWheelContainer.WHEEL_CENTER_TO_SECANT);
        }

        // Layout the indicators based on the current level.
        // The icons are spread on the left (top) side of the shutter button in
        // landscape (portrait) orientation.
        for (int i = 0; i < getChildCount(); ++i) {
            View view = getChildAt(i);
            // We still need to show the disabled indicators in the second level.
            double radian = mChildRadians[i];
            double startVisibleRadians = mInAnimation
                    ? mStartVisibleRadians[1]
                    : mStartVisibleRadians[mCurrentLevel];
            double endVisibleRadians = mInAnimation
                    ? mEndVisibleRadians[1]
                    : mEndVisibleRadians[mCurrentLevel];
            // Only hide the views in video mode. Hiding the views when taking
            // a picture is disturbing.
            if ((!view.isEnabled() && (mCurrentLevel == 0)
                    && (mCurrentMode == MODE_VIDEO))
                    || (radian < (startVisibleRadians - HIGHLIGHT_RADIANS / 2))
                    || (radian > (endVisibleRadians + HIGHLIGHT_RADIANS / 2))) {
                view.setVisibility(View.GONE);
                continue;
            }
            view.setVisibility(View.VISIBLE);
            int x = mCenterX + (int)(mWheelRadius * Math.cos(radian));
            int y = mCenterY - (int)(mWheelRadius * Math.sin(radian));
            int width = view.getMeasuredWidth();
            int height = view.getMeasuredHeight();
            if (view == mZoomControl) {
                // ZoomControlWheel matches the size of its parent view.
                view.layout(0, 0, right - left, bottom - top);
            } else {
                view.layout(x - width / 2, y - height / 2, x + width / 2,
                        y + height / 2);
            }
        }
    }

    private void presetFirstLevelChildRadians() {
        // Set the visible range in the first-level indicator wheel.
        mStartVisibleRadians[0] = Math.toRadians(FIRST_LEVEL_START_DEGREES);
        mTouchSectorRadians[0] = HIGHLIGHT_RADIANS;
        mEndVisibleRadians[0] = Math.toRadians(FIRST_LEVEL_END_DEGREES);

        // Set the angle of each component in the first-level indicator wheel.
        int startIndex = 0;
        if (mZoomControl != null) {
            mChildRadians[startIndex++] = Math.toRadians(MAX_ZOOM_CONTROL_DEGREES);
        }
        if (mCameraPicker != null) {
            mChildRadians[startIndex++] = Math.toRadians(FIRST_LEVEL_START_DEGREES);
        }
        mChildRadians[startIndex++] = Math.toRadians(FIRST_LEVEL_END_DEGREES);
    }

    private void presetSecondLevelChildRadians() {
        int count = getChildCountByLevel(1);
        int sectors = (count <= 1) ? 1 : (count - 1);
        double sectorDegrees =
                ((SECOND_LEVEL_END_DEGREES - SECOND_LEVEL_START_DEGREES) / sectors);
        mSectorRadians[1] = Math.toRadians(sectorDegrees);

        double degrees = CLOSE_ICON_DEFAULT_DEGREES;
        mStartVisibleRadians[1] = Math.toRadians(SECOND_LEVEL_START_DEGREES);

        int startIndex = mSecondLevelStartIndex;
        for (int i = 0; i < count; i++) {
            mChildRadians[startIndex + i] = Math.toRadians(degrees);
            degrees += sectorDegrees;
        }

        // The radians for the touch sector of an indicator.
        mTouchSectorRadians[1] =
                Math.min(HIGHLIGHT_RADIANS, Math.toRadians(sectorDegrees));

        mEndVisibleRadians[1] = Math.toRadians(SECOND_LEVEL_END_DEGREES);
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
            View v = getChildAt(mPressedIndex);
            if (!(v instanceof AbstractIndicatorButton) && v.isEnabled()) {
                return mPressedIndex;
            }
        }
        return -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int selectedIndex = getSelectedIndicatorIndex();

        // Draw the highlight arc if an indicator is selected or being pressed.
        // And skip the zoom control which index is zero.
        if (selectedIndex >= 1) {
            int degree = (int) Math.toDegrees(mChildRadians[selectedIndex]);
            float innerR = (float) mShutterButtonRadius;
            float outerR = (float) (mShutterButtonRadius + mStrokeWidth +
                    EDGE_STROKE_WIDTH * 0.5);

            // Construct the path of the fan-shaped semi-transparent area.
            mFanPath.rewind();
            mBackgroundRect.set(mCenterX - innerR, mCenterY - innerR,
                    mCenterX + innerR, mCenterY + innerR);
            mFanPath.arcTo(mBackgroundRect, -degree + HIGHLIGHT_DEGREES / 2,
                    -HIGHLIGHT_DEGREES);
            mBackgroundRect.set(mCenterX - outerR, mCenterY - outerR,
                    mCenterX + outerR, mCenterY + outerR);
            mFanPath.arcTo(mBackgroundRect, -degree - HIGHLIGHT_DEGREES / 2,
                    HIGHLIGHT_DEGREES);
            mFanPath.close();

            mBackgroundPaint.setStrokeWidth(HIGHLIGHT_WIDTH);
            mBackgroundPaint.setStrokeCap(Paint.Cap.SQUARE);
            mBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mBackgroundPaint.setColor(HIGHLIGHT_FAN_COLOR);
            canvas.drawPath(mFanPath, mBackgroundPaint);

            // Draw the highlight edge
            mBackgroundPaint.setStyle(Paint.Style.STROKE);
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!mInitialized) return;
        if (mCurrentMode == MODE_VIDEO) {
            mSecondLevelIcon.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
            mCloseIcon.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        } else {
            // We also disable the zoom button during snapshot.
            enableZoom(enabled);
        }
        mSecondLevelIcon.setEnabled(enabled);
        mCloseIcon.setEnabled(enabled);
        // Request layout because the visibility of children may be decided by
        // isEnabled in onLayout.
        requestLayout();
    }

    public void enableZoom(boolean enabled) {
        if (mZoomControl != null) mZoomControl.setEnabled(enabled);
    }

    public void onTouchOutBound() {
        dismissSettingPopup();
        if (mPressedIndex != -1) {
            injectMotionEvent(mPressedIndex, mLastMotionEvent, MotionEvent.ACTION_CANCEL);
            mPressedIndex = -1;
            invalidate();
        }
    }

    public void dismissSecondLevelIndicator() {
        if (mCurrentLevel == 1) {
            changeIndicatorsLevel();
        }
    }
}
