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
import android.view.View;
import android.widget.RelativeLayout;

/**
 * A view that contains camera zoom control which could adjust the zoom in/out
 * if the camera supports zooming.
 */
public abstract class ZoomControl extends RelativeLayout {
    private static final String TAG = "ZoomControl";

    public interface OnZoomChangedListener {
        void onZoomValueChanged(int index);  // only for immediate zoom
        void onZoomStateChanged(int state);  // only for smooth zoom
    }

    private int mZoomMax, mZoomIndex;
    private boolean mSmoothZoomSupported;
    private OnZoomChangedListener mListener;
    private boolean mZoomSupported = true;

    // The state of zoom button.
    public static final int ZOOM_IN = 0;
    public static final int ZOOM_OUT = 1;
    public static final int ZOOM_STOP = 2;

    protected OnIndicatorEventListener mOnIndicatorEventListener;

    public ZoomControl(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public abstract void initialize(Context context);

    public void setZoomMax(int zoomMax) {
        mZoomMax = zoomMax;
    }

    public void setZoomSupported(boolean supported) {
        mZoomSupported = supported;
    }

    public boolean isZoomSupported() {
        return mZoomSupported;
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
    }

    public void setSmoothZoomSupported(boolean smoothZoomSupported) {
        mSmoothZoomSupported = smoothZoomSupported;
    }

    public boolean zoomIn() {
        return (mZoomIndex == mZoomMax) ? false : changeZoomIndex(mZoomIndex + 1);
    }

    public boolean zoomOut() {
        return (mZoomIndex == 0) ? false : changeZoomIndex(mZoomIndex - 1);
    }

    public void stopZooming() {
        if (mSmoothZoomSupported) {
            if (mListener != null) mListener.onZoomStateChanged(ZOOM_STOP);
        }
    }

    private boolean changeZoomIndex(int index) {
        int zoomType = (index < mZoomIndex) ? ZOOM_OUT : ZOOM_IN;
        if (mListener != null) {
            if (mSmoothZoomSupported) {
                if (((zoomType == ZOOM_IN) && (mZoomIndex != mZoomMax)) ||
                        ((zoomType == ZOOM_OUT) && (mZoomIndex != 0))) {
                    mListener.onZoomStateChanged(zoomType);
                }
            } else {
                mListener.onZoomStateChanged(index);
            }
            mZoomIndex = index;
        }
        return true;
    }

    public void setDegree(int degree) {
        int count = getChildCount();
        for (int i = 0 ; i < count ; ++i) {
            View view = getChildAt(i);
            if (view instanceof RotateImageView) {
                ((RotateImageView) view).setDegree(degree);
            }
        }
    }

    protected int getZoomIndex() {
        return mZoomIndex;
    }
}
