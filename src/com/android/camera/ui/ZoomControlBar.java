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
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A view that contains camera zoom control and its layout.
 */
public class ZoomControlBar extends ZoomControl {
    private static final String TAG = "ZoomControlBar";
    private static int THRESHOLD_FIRST_MOVE = Util.dpToPixel(10); // pixels
    private View mBar;
    private boolean mStartChanging;

    public ZoomControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBar = new View(context);
        mBar.setBackgroundResource(R.drawable.ic_zoom_big);
        addView(mBar);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;

        int action = event.getAction();

        if (!isEnabled()) return false;

        double y = (double) event.getY();

        // Calculate the absolute offset of the slider in the zoom control bar.
        // For left-hand users, as the device is rotated for 180 degree for
        // landscape mode, the zoom-in bottom should be on the top, so the
        // position should be reversed.
        int offset = 5 * getWidth() / 4; // the padding and the icon height
        int height = getHeight();
        int range = height - 2 * offset; // the range of the zoom slider
        int pos; // the relative position in the zoom slider bar
        if (mDegree == 180) {
            pos = (int) y - offset;
        } else {
            pos = height - (int) y - offset;
        }
        if (pos < 0) pos = 0;
        if (pos > range) pos = range;

        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_CANCEL:
                closeZoomControl();
                break;

            case MotionEvent.ACTION_DOWN:
                mStartChanging = false;
            case MotionEvent.ACTION_MOVE:
                // Make sure the movement is large enough before we start
                // changing the zoom.
                if (!mStartChanging && (Math.abs(mSliderPosition - pos)
                        > THRESHOLD_FIRST_MOVE)) {
                    mStartChanging = true;
                }
                if (mStartChanging) {
                    performZoom(1.0d * pos / range);
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
        int height = bottom - top;
        int range = height - 10 * width / 4;
        int pos;

        // TODO: remove offset once we have correct ic_zoom_big.9.png.
        int offset = 3 * width / 4;

        // For left-hand users, as the device is rotated for 180 degree,
        // the zoom-in button should be on the top.
        if (mDegree == 180) {
            mZoomOut.layout(0, 0, width, width);
            mZoomIn.layout(0, height - width, width, height);
            pos = offset + mZoomIndex * range / mZoomMax;
            mZoomSlider.layout(0, pos, width, pos + width);
        } else {
            mZoomIn.layout(0, 0, width, width);
            mZoomOut.layout(0, height - width, width, height);
            pos = offset + (mZoomMax - mZoomIndex) * range / mZoomMax;
            mZoomSlider.layout(0, pos, width, pos + width);
        }
        mBar.layout(0, width, width, bottom - top - width);
    }
}
