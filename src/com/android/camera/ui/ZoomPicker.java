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
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * A class to increase or decrease zoom
 */
public class ZoomPicker extends ZoomControl {
    private final String TAG = "ZoomPicker";

    private View mIncrementButton;
    private View mDecrementButton;
    private int mState = ZOOM_STOP;

    private Handler mHandler;

    public ZoomPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (zooming()) mHandler.postDelayed(this, 65);
        }
    };

    private boolean zooming() {
        switch (mState) {
            case ZOOM_IN:
                return zoomIn();
            case ZOOM_OUT:
                return zoomOut();
            default:
                return false;
        }
    }

    @Override
    public void initialize(Context context) {
        final View increment = getRootView().findViewById(R.id.zoom_increment);
        final View decrement = getRootView().findViewById(R.id.zoom_decrement);

        mHandler = new Handler();

        OnTouchListener touchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    mState = (v == increment) ? ZOOM_IN : ZOOM_OUT;
                    if (zooming()) mHandler.postDelayed(mRunnable, 300);
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    int eventState = (v == increment) ? ZOOM_IN : ZOOM_OUT;
                    // Ignore the event if the current state does not match.
                    if (eventState == mState) {
                        stopZooming();
                        mState = ZOOM_STOP;
                    }
                }
                return false;
            }
        };

        mIncrementButton = increment;
        mIncrementButton.setOnTouchListener(touchListener);
        mIncrementButton.setVisibility(View.VISIBLE);
        mDecrementButton = decrement;
        mDecrementButton.setOnTouchListener(touchListener);
        mDecrementButton.setVisibility(View.VISIBLE);
    }

    public void setEnabled(boolean enabled) {
        mIncrementButton.setEnabled(enabled);
        mDecrementButton.setEnabled(enabled);
    }

}
