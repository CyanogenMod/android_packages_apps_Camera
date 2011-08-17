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
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * A view that contains camera zoom control and its layout.
 */
public class ZoomControlBar extends ZoomControl {
    private static final String TAG = "ZoomControlBar";

    private static final int ZOOMING_INTERVAL = 300; // milliseconds

    private ImageView mZoomIn;
    private ImageView mZoomOut;
    private ImageView mZoomSlider;
    private View mBar;
    private int mSliderPosition = 0;
    private Handler mHandler;
    private int mDegree;

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (mSliderPosition < 0) {
                zoomIn();
            } else if (mSliderPosition > 0) {
                zoomOut();
            }
            if (mSliderPosition != 0) mHandler.postDelayed(mRunnable, ZOOMING_INTERVAL);
        }
    };

    public ZoomControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void initialize(Context context) {
        mZoomIn = addImageView(context, R.drawable.ic_zoom_in_holo_light);
        mBar = new View(context);
        mBar.setBackgroundResource(R.drawable.ic_zoom_big);
        addView(mBar);
        mZoomSlider = addImageView(context, R.drawable.btn_zoom_slider);
        mZoomOut = addImageView(context, R.drawable.ic_zoom_out_holo_light);
        mHandler = new Handler();
    }

    ImageView addImageView(Context context, int iconResourceId) {
        ImageView image = new RotateImageView(context);
        image.setImageResource(iconResourceId);
        addView(image);
        return image;
    }

    private void closeControl() {
        mHandler.removeCallbacks(mRunnable);
        mSliderPosition = 0;
        stopZooming();
        mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_ZOOM_CONTROL_BAR);
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
            case MotionEvent.ACTION_DOWN:
                mHandler.postDelayed(mRunnable, ZOOMING_INTERVAL);
                mZoomSlider.setPressed(true);
                break;
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mZoomSlider.setPressed(false);
                closeControl();
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
        mDegree = degree;
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
