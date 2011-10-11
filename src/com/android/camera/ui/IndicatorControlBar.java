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

import com.android.camera.PreferenceGroup;
import com.android.camera.R;
import com.android.camera.Util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * A view that contains the top-level indicator control.
 */
public class IndicatorControlBar extends IndicatorControl implements
        View.OnClickListener {
    private static final String TAG = "IndicatorControlBar";

    // Space between indicator icons.
    public static final int ICON_SPACING = Util.dpToPixel(16);

    private ImageView mZoomIcon;
    private ImageView mSecondLevelIcon;
    private ZoomControlBar mZoomControl;

    public IndicatorControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mSecondLevelIcon = (ImageView)
                findViewById(R.id.second_level_indicator_bar_icon);
        mSecondLevelIcon.setOnClickListener(this);
    }

    public void initialize(Context context, PreferenceGroup group,
            boolean zoomSupported) {
        setPreferenceGroup(group);

        // Add CameraPicker control.
        initializeCameraPicker();

        // Add the ZoomControl if supported.
        if (zoomSupported) {
            mZoomControl = (ZoomControlBar) findViewById(R.id.zoom_control);
            mZoomControl.setVisibility(View.VISIBLE);
        }
        requestLayout();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);
        // We need to consume the event, or it will trigger tap-to-focus.
        return true;
    }

    public void onClick(View view) {
        dismissSettingPopup();
        // Only for the click on mSecondLevelIcon.
        mOnIndicatorEventListener.onIndicatorEvent(
                OnIndicatorEventListener.EVENT_ENTER_SECOND_LEVEL_INDICATOR_BAR);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int padding = getPaddingTop();

        int count = getChildCount();
        if (count == 0) return;
        int width = right - left;

        // First indicator will be CameraPicker if exists.
        if (mCameraPicker != null) {
            mCameraPicker.layout(0, padding, width, padding + width);
        }

        // Layout the zoom control if required.
        int offset = padding + width; // the padding and the icon height
        if (mZoomControl != null)  {
            mZoomControl.layout(0, offset, width, bottom - top - offset);
        }

        mSecondLevelIcon.layout(0, bottom - top - offset, width, bottom - top);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mCurrentMode == MODE_VIDEO) {
            mSecondLevelIcon.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        } else {
            // We also disable the zoom button during snapshot.
            enableZoom(enabled);
        }
        mSecondLevelIcon.setEnabled(enabled);
    }

    public void enableZoom(boolean enabled) {
        if (mZoomControl != null)  mZoomControl.setEnabled(enabled);
    }
}
