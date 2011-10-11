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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A view that contains camera zoom control and its layout.
 */
public class ZoomControlBar extends ZoomControl {
    private static final String TAG = "ZoomControlBar";
    private static final int THRESHOLD_FIRST_MOVE = Util.dpToPixel(10); // pixels
    // Space between indicator icon and the zoom-in/out icon.
    private static final int ICON_SPACING = Util.dpToPixel(12);

    private View mBar;
    private boolean mStartChanging;
    private int mSliderLength;
    private int mHeight;
    private int mIconHeight;
    private int mTotalIconHeight;

    public ZoomControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBar = new View(context);
        mBar.setBackgroundResource(R.drawable.zoom_slider_bar);
        addView(mBar);
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        mBar.setActivated(activated);
    }

    private int getSliderPosition(int y) {
        // Calculate the absolute offset of the slider in the zoom control bar.
        // For left-hand users, as the device is rotated for 180 degree for
        // landscape mode, the zoom-in bottom should be on the top, so the
        // position should be reversed.
        int pos; // the relative position in the zoom slider bar
        if (mDegree == 180) {
            pos = y - mTotalIconHeight;
        } else {
            pos = mHeight - mTotalIconHeight - y;
        }
        if (pos < 0) pos = 0;
        if (pos > mSliderLength) pos = mSliderLength;
        return pos;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mHeight = h;
        mIconHeight = mZoomIn.getMeasuredHeight();
        mTotalIconHeight = mIconHeight + ICON_SPACING;
        mSliderLength = mHeight - (2 * mTotalIconHeight);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isEnabled() || (mHeight == 0)) return false;
        int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                setActivated(false);
                closeZoomControl();
                break;

            case MotionEvent.ACTION_DOWN:
                setActivated(true);
                mStartChanging = false;
            case MotionEvent.ACTION_MOVE:
                int pos = getSliderPosition((int) event.getY());
                if (!mStartChanging) {
                    // Make sure the movement is large enough before we start
                    // changing the zoom.
                    int delta = mSliderPosition - pos;
                    if ((delta > THRESHOLD_FIRST_MOVE) ||
                            (delta < -THRESHOLD_FIRST_MOVE)) {
                        mStartChanging = true;
                    }
                }
                if (mStartChanging) {
                    performZoom(1.0d * pos / mSliderLength);
                    mSliderPosition = pos;
                }
                requestLayout();
        }
        return true;
    }

    @Override
    public void setDegree(int degree) {
        // layout for the left-hand camera control
        if ((degree == 180) || (mDegree == 180)) requestLayout();
        super.setDegree(degree);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (mZoomMax == 0) return;
        int width = right - left;
        mBar.layout(0, mTotalIconHeight, width, mHeight - mTotalIconHeight);
        // For left-hand users, as the device is rotated for 180 degree,
        // the zoom-in button should be on the top.
        int pos; // slider position
        int sliderPosition;
        if (mSliderPosition != -1) { // -1 means invalid
            sliderPosition = mSliderPosition;
        } else {
            sliderPosition = (int) ((double) mSliderLength * mZoomIndex / mZoomMax);
        }
        if (mDegree == 180) {
            mZoomOut.layout(0, 0, width, mIconHeight);
            mZoomIn.layout(0, mHeight - mIconHeight, width, mHeight);
            pos = mBar.getTop() + sliderPosition;
        } else {
            mZoomIn.layout(0, 0, width, mIconHeight);
            mZoomOut.layout(0, mHeight - mIconHeight, width, mHeight);
            pos = mBar.getBottom() - sliderPosition;
        }
        int sliderHeight = mZoomSlider.getMeasuredHeight();
        mZoomSlider.layout(0, (pos - sliderHeight / 2),
                width, (pos + sliderHeight / 2));
    }

    @Override
    public void setZoomIndex(int index) {
        super.setZoomIndex(index);
        mSliderPosition = -1; // -1 means invalid
        requestLayout();
    }
}
