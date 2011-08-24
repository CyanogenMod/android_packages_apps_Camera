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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A view that contains camera zoom control and its layout.
 */
public class ZoomControlBar extends ZoomControl {
    private static final String TAG = "ZoomControlBar";
    private View mBar;

    public ZoomControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void initialize(Context context) {
        super.initialize(context);
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
        int offset = getHeight() / 2;

        // For left-hand users, as the device is rotated for 180 degree for
        // landscape mode, the zoom-in bottom should be on the top, so the
        // position should be reversed.
        if (mDegree == 180) {
            mSliderPosition = offset - (int) y;
        } else {
            mSliderPosition = (int) y - offset;
        }
        // TODO: add fast zoom change here

        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                closeZoomControl();
                break;
            default:
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
        int width = right - left;
        int height = bottom - top;
        int h = height / 2;
        int pos;

        // For left-hand users, as the device is rotated for 180 degree,
        // the zoom-in button should be on the top.
        if (mDegree == 180) {
            pos = h - mSliderPosition - width / 2;
            mZoomOut.layout(0, top, width, top + width);
            mZoomIn.layout(0, bottom - width, width, bottom);
        } else {
            pos = h + mSliderPosition - width / 2;
            mZoomIn.layout(0, top, width, top + width);
            mZoomOut.layout(0, bottom - width, width, bottom);
        }
        mBar.layout(0, top + width, width, bottom - width);
        if (pos < width) {
            pos = width;
        } else if (pos > (height - 2 * width)) {
            pos = height - 2 * width;
        }
        mZoomSlider.layout(0, pos, width, pos + width);
   }
}
