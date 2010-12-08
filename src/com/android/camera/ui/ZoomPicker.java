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
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Formatter;

/**
 * A view for increasing or decresing zoom
 */
public class ZoomPicker extends LinearLayout {
    private final String TAG = "ZoomPicker";
    private TextView mZoomTextView;
    private int mZoomMax, mZoomIndex;
    private float[] mZoomRatios;
    private OnZoomChangedListener mListener;
    private boolean mIncrement, mDecrement;
    private final StringBuilder mBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mBuilder);
    private final Object[] mFormatterArgs = new Object[1];
    private String mZoomText;
    private Button mIncrementButton;
    private Button mDecrementButton;

    private Handler mHandler;
    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (mIncrement) {
                mIncrementButton.setBackgroundResource(R.drawable.button_zoom_in_longpressed_holo);
                if (changeZoomIndex(mZoomIndex + 1)) {
                    mHandler.postDelayed(this, 65);
                }
            } else if (mDecrement) {
                mDecrementButton.setBackgroundResource(R.drawable.button_zoom_out_longpressed_holo);
                if (changeZoomIndex(mZoomIndex - 1)) {
                    mHandler.postDelayed(this, 65);
                }
            }
        }
    };

    public ZoomPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        mZoomText = context.getString(R.string.zoom_text);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHandler = new Handler();

        OnTouchListener incrementTouchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!mIncrement && changeZoomIndex(mZoomIndex + 1)) {
                        mIncrement = true;
                        // Give bigger delay so users can tap to change only one
                        // zoom step.
                        mHandler.postDelayed(mRunnable, 300);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mIncrementButton.setBackgroundResource(R.drawable.btn_zoom_in);
                    mIncrement = false;
                }
                return false;
            }
        };

        OnTouchListener decrementTouchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!mDecrement && changeZoomIndex(mZoomIndex - 1)) {
                        mDecrement = true;
                        // Give bigger delay so users can tap to change only one
                        // zoom step.
                        mHandler.postDelayed(mRunnable, 300);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mDecrementButton.setBackgroundResource(R.drawable.btn_zoom_out);
                    mDecrement = false;
                }
                return false;
            }
        };

        Button incrementButton = mIncrementButton = (Button) findViewById(R.id.increment);
        incrementButton.setOnTouchListener(incrementTouchListener);
        Button decrementButton = mDecrementButton = (Button) findViewById(R.id.decrement);
        decrementButton.setOnTouchListener(decrementTouchListener);
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    public interface OnZoomChangedListener {
        void onZoomChanged(int index);
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

    private boolean changeZoomIndex(int index) {
        if (index > mZoomMax || index < 0) return false;
        mZoomIndex = index;
        if (mListener != null) {
            mListener.onZoomChanged(mZoomIndex);
        }
        updateView();
        return true;
    }

    private void updateView() {
        if (mZoomTextView == null) {
            mZoomTextView = (TextView) getRootView().findViewById(R.id.zoom_ratio);
        }
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mIncrementButton.setEnabled(enabled);
        mDecrementButton.setEnabled(enabled);
    }
}
