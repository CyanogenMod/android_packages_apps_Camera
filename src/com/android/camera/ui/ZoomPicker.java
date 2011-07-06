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

import com.android.camera.R;

import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.TextView;

import java.util.Formatter;

/**
 * A class to increase or decrease zoom
 */
public class ZoomPicker {
    private final String TAG = "ZoomPicker";
    private TextView mZoomTextView;
    private int mZoomMax, mZoomIndex;
    private float[] mZoomRatios;
    private boolean mSmoothZoomSupported;
    private OnZoomChangedListener mListener;
    private boolean mIncrement, mDecrement;
    private final StringBuilder mBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mBuilder);
    private final Object[] mFormatterArgs = new Object[1];
    private String mZoomText;
    private Button mIncrementButton;
    private Button mDecrementButton;

    // The state of zoom button.
    public static final int ZOOM_IN = 0;
    public static final int ZOOM_OUT = 1;
    public static final int ZOOM_STOP = 2;

    private Handler mHandler;
    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (mIncrement) {
                mIncrementButton.setBackgroundResource(R.drawable.button_zoom_in_longpressed_holo);
                if (mSmoothZoomSupported) {
                    if (mZoomIndex != mZoomMax && mListener != null) {
                        mListener.onZoomStateChanged(ZOOM_IN);
                    }
                } else if (changeZoomIndex(mZoomIndex + 1)) {
                    mHandler.postDelayed(this, 65);
                }
            } else if (mDecrement) {
                mDecrementButton.setBackgroundResource(R.drawable.button_zoom_out_longpressed_holo);
                if (mSmoothZoomSupported) {
                    if (mZoomIndex != 0 && mListener != null) {
                        mListener.onZoomStateChanged(ZOOM_OUT);
                    }
                } else if (changeZoomIndex(mZoomIndex - 1)) {
                    mHandler.postDelayed(this, 65);
                }
            }
        }
    };

    public ZoomPicker(Context context, Button increment, Button decrement, TextView zoomText) {
        mZoomText = context.getString(R.string.zoom_text);
        mHandler = new Handler();

        OnTouchListener incrementTouchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!mIncrement && changeZoomIndex(mZoomIndex + 1)) {
                        mIncrement = true;
                        // Give bigger delay so users can tap to change only one
                        // zoom step.
                        mHandler.postDelayed(mRunnable, 300);
                    }
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    mIncrementButton.setBackgroundResource(R.drawable.btn_zoom_in);
                    mIncrement = false;
                    if (mSmoothZoomSupported) {
                        if (mListener != null) mListener.onZoomStateChanged(ZOOM_STOP);
                    }
                }
                return false;
            }
        };

        OnTouchListener decrementTouchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!mDecrement && changeZoomIndex(mZoomIndex - 1)) {
                        mDecrement = true;
                        // Give bigger delay so users can tap to change only one
                        // zoom step.
                        mHandler.postDelayed(mRunnable, 300);
                    }
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    mDecrementButton.setBackgroundResource(R.drawable.btn_zoom_out);
                    mDecrement = false;
                    if (mSmoothZoomSupported) {
                        if (mListener != null) mListener.onZoomStateChanged(ZOOM_STOP);
                    }
                }
                return false;
            }
        };

        mIncrementButton = increment;
        mIncrementButton.setOnTouchListener(incrementTouchListener);
        mIncrementButton.setVisibility(View.VISIBLE);
        mDecrementButton = decrement;
        mDecrementButton.setOnTouchListener(decrementTouchListener);
        mDecrementButton.setVisibility(View.VISIBLE);
        mZoomTextView = zoomText;
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    public interface OnZoomChangedListener {
        void onZoomValueChanged(int index);  // only for immediate zoom
        void onZoomStateChanged(int state);  // only for smooth zoom
    }

    public void setZoomRatios(float[] zoomRatios) {
        mZoomMax = zoomRatios.length - 1;
        mZoomRatios = zoomRatios;
        updateView();
    }

    public void setZoomIndex(int index) {
        if (index < 0 || index > mZoomMax) {
            throw new IllegalArgumentException("Invalid zoom value:" + index);
        }
        mZoomIndex = index;
        updateView();
    }

    public void setSmoothZoomSupported(boolean smoothZoomSupported) {
        mSmoothZoomSupported = smoothZoomSupported;
    }

    private boolean changeZoomIndex(int index) {
        if (index > mZoomMax || index < 0) return false;
        mZoomIndex = index;
        if (mListener != null) {
            mListener.onZoomValueChanged(mZoomIndex);
        }
        updateView();
        return true;
    }

    private void updateView() {
        if (mZoomTextView == null) return;

        if (mZoomIndex == 0) {
            mZoomTextView.setVisibility(View.INVISIBLE);
        } else {
            mZoomTextView.setText(String.format(mZoomText, formatZoomRatio(mZoomRatios[mZoomIndex])));
            mZoomTextView.setVisibility(View.VISIBLE);
        }
    }

    private String formatZoomRatio(float value) {
        mFormatterArgs[0] = value;
        mBuilder.delete(0, mBuilder.length());
        mFormatter.format("%2.1fx", mFormatterArgs);
        return mFormatter.toString();
    }

    public void setEnabled(boolean enabled) {
        mIncrementButton.setEnabled(enabled);
        mDecrementButton.setEnabled(enabled);
    }
}
