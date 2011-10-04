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
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * A view that contains camera zoom control which could adjust the zoom in/out
 * if the camera supports zooming.
 */
public abstract class ZoomControl extends RelativeLayout {
    // The states of zoom button.
    public static final int ZOOM_IN = 0;
    public static final int ZOOM_OUT = 1;
    public static final int ZOOM_STOP = 2;

    private static final String TAG = "ZoomControl";
    private static final int ZOOMING_INTERVAL = 1000; // milliseconds

    protected ImageView mZoomIn;
    protected ImageView mZoomOut;
    protected ImageView mZoomSlider;
    protected int mSliderPosition = 0;
    protected int mDegree;
    private Handler mHandler;

    public interface OnZoomChangedListener {
        void onZoomValueChanged(int index);  // only for immediate zoom
        void onZoomStateChanged(int state);  // only for smooth zoom
    }

    // The interface OnZoomIndexChangedListener is used to inform the
    // ZoomIndexBar about the zoom index change. The index position is between
    // 0 (the index is zero) and 1.0 (the index is mZoomMax).
    public interface OnZoomIndexChangedListener {
        void onZoomIndexChanged(double indexPosition);
    }

    protected int mZoomMax, mZoomIndex;
    private boolean mSmoothZoomSupported;
    private OnZoomChangedListener mListener;
    private OnZoomIndexChangedListener mIndexListener;

    protected OnIndicatorEventListener mOnIndicatorEventListener;
    private int mState;
    private int mStep;

    protected final Runnable mRunnable = new Runnable() {
        public void run() {
            performZoom(mState, false);
        }
    };

    public ZoomControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mZoomIn = addImageView(context, R.drawable.ic_zoom_in);
        mZoomSlider = addImageView(context, R.drawable.ic_zoom_slider);
        mZoomOut = addImageView(context, R.drawable.ic_zoom_out);
        mHandler = new Handler();
    }

    public void startZoomControl() {
        mZoomSlider.setPressed(true);
        setZoomIndex(mZoomIndex); // Update the zoom index bar.
    }

    protected ImageView addImageView(Context context, int iconResourceId) {
        ImageView image = new RotateImageView(context);
        image.setImageResource(iconResourceId);
        if (iconResourceId == R.drawable.ic_zoom_slider) {
            image.setContentDescription(getResources().getString(
                    R.string.accessibility_zoom_control));
        } else {
            image.setContentDescription(getResources().getString(
                    R.string.empty));
        }
        addView(image);
        return image;
    }

    public void closeZoomControl() {
        mZoomSlider.setPressed(false);
        stopZooming();
        if (!mSmoothZoomSupported) mHandler.removeCallbacks(mRunnable);
        if (mOnIndicatorEventListener != null) {
            mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_ZOOM_CONTROL);
        }
    }

    public void setZoomMax(int zoomMax) {
        mZoomMax = zoomMax;

        // Layout should be requested as the maximum zoom level is the key to
        // show the correct zoom slider position.
        requestLayout();
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    public void setOnIndicatorEventListener(OnIndicatorEventListener listener) {
        mOnIndicatorEventListener = listener;
    }

    public void setZoomIndex(int index) {
        if (index < 0 || index > mZoomMax) {
            throw new IllegalArgumentException("Invalid zoom value:" + index);
        }
        mZoomIndex = index;
        invalidate();
    }

    public void setSmoothZoomSupported(boolean smoothZoomSupported) {
        mSmoothZoomSupported = smoothZoomSupported;
    }

    private boolean zoomIn() {
        return (mZoomIndex == mZoomMax) ? false : changeZoomIndex(mZoomIndex + mStep);
    }

    private boolean zoomOut() {
        return (mZoomIndex == 0) ? false : changeZoomIndex(mZoomIndex - mStep);
    }

    protected void setZoomStep(int step) {
        mStep = step;
    }

    private void stopZooming() {
        if (mSmoothZoomSupported) {
            if (mListener != null) mListener.onZoomStateChanged(ZOOM_STOP);
        }
    }

    // Called from ZoomControlWheel to change the zoom level.
    // TODO: merge the zoom control for both platforms.
    protected void performZoom(int state) {
        performZoom(state, true);
    }

    private void performZoom(int state, boolean fromUser) {
        if ((mState == state) && fromUser) return;
        if (fromUser) mHandler.removeCallbacks(mRunnable);
        mState = state;
        switch (state) {
            case ZOOM_IN:
                zoomIn();
                break;
            case ZOOM_OUT:
                zoomOut();
                break;
            case ZOOM_STOP:
                stopZooming();
                break;
        }
        if (!mSmoothZoomSupported) {
            // Repeat the zoom action on tablet as the user is still holding
            // the zoom slider.
            mHandler.postDelayed(mRunnable, ZOOMING_INTERVAL / mZoomMax);
        }
    }

    // Called from ZoomControlBar to change the zoom level.
    protected void performZoom(double zoomPercentage) {
        int index = (int) (mZoomMax * zoomPercentage);
        if (mZoomIndex == index) return;
        changeZoomIndex(index);
   }

    private boolean changeZoomIndex(int index) {
        if (mListener != null) {
            if (mSmoothZoomSupported) {
                int zoomType = (index < mZoomIndex) ? ZOOM_OUT : ZOOM_IN;
                if (((zoomType == ZOOM_IN) && (mZoomIndex != mZoomMax)) ||
                        ((zoomType == ZOOM_OUT) && (mZoomIndex != 0))) {
                    mListener.onZoomStateChanged(zoomType);
                }
            } else {
                if (index > mZoomMax) index = mZoomMax;
                if (index < 0) index = 0;
                mListener.onZoomValueChanged(index);
                mZoomIndex = index;
            }
        }
        return true;
    }

    public void setDegree(int degree) {
        mDegree = degree;
        int count = getChildCount();
        for (int i = 0 ; i < count ; ++i) {
            View view = getChildAt(i);
            if (view instanceof RotateImageView) {
                ((RotateImageView) view).setDegree(degree);
            }
        }
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        mZoomIn.setActivated(activated);
        mZoomOut.setActivated(activated);
    }
}
