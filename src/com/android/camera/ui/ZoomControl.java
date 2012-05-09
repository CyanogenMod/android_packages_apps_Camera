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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.camera.R;

/**
 * A view that contains camera zoom control which could adjust the zoom ratio
 * if the camera supports zooming.
 */
public abstract class ZoomControl extends RelativeLayout implements Rotatable {
    protected ImageView mZoomIn;
    protected ImageView mZoomOut;
    protected ImageView mZoomSlider;
    protected int mOrientation;

    public interface OnZoomChangedListener {
        void onZoomValueChanged(int index);  // only for immediate zoom
    }

    // The interface OnZoomIndexChangedListener is used to inform the listener
    // about the zoom index change. The index position is between 0 (the index
    // is zero) and 1.0 (the index is mZoomMax).
    public interface OnZoomIndexChangedListener {
        void onZoomIndexChanged(double indexPosition);
    }

    protected int mZoomMax, mZoomIndex;
    private OnZoomChangedListener mListener;

    protected OnIndicatorEventListener mOnIndicatorEventListener;

    public ZoomControl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mZoomIn = addImageView(context, R.drawable.ic_zoom_in);
        mZoomSlider = addImageView(context, R.drawable.ic_zoom_slider);
        mZoomOut = addImageView(context, R.drawable.ic_zoom_out);
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
        requestLayout();
    }

    protected void performZoom(double zoomPercentage) {
        int index = (int) (mZoomMax * zoomPercentage);
        if (mZoomIndex == index) return;
        if (mListener != null) {
            if (index > mZoomMax) index = mZoomMax;
            if (index < 0) index = 0;
            mListener.onZoomValueChanged(index);
            mZoomIndex = index;
        }
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        int count = getChildCount();
        for (int i = 0 ; i < count ; ++i) {
            View view = getChildAt(i);
            if (view instanceof RotateImageView) {
                ((RotateImageView) view).setOrientation(orientation, animation);
            }
        }
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        mZoomIn.setActivated(activated);
        mZoomOut.setActivated(activated);
    }

    public void rotate(double angle) {
    }
}
