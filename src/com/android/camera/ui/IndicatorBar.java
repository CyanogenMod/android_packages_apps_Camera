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

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.CameraSettings;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * A view that contains the top-level indicator control.
 */
public class IndicatorBar extends IndicatorControl implements
        View.OnClickListener {
    private static final String TAG = "IndicatorBar";

    private ImageView mZoomIcon;
    private ImageView mSecondLevelIcon;

    public IndicatorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(Context context, PreferenceGroup group,
            String flashSetting, boolean zoomSupported) {
        // From UI spec, we have camera_flash setting on the first level.
        super.initialize(context, group, new String[] {flashSetting}, null);

        // Add CameraPicker control.
        initializeCameraPicker(context, group);

        // add Zoom Icon.
        if (zoomSupported) {
            mZoomIcon = (ImageView) findViewById(R.id.zoom_control_icon);
            mZoomIcon.setOnClickListener(this);
            mZoomIcon.setVisibility(View.VISIBLE);
        }

        mSecondLevelIcon = (ImageView) findViewById(R.id.second_level_indicator_bar_icon);
        mSecondLevelIcon.setOnClickListener(this);
        requestLayout();
    }

    public void onClick(View view) {
        dismissSettingPopup();
        if (view == mZoomIcon) {
            mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_ENTER_ZOOM_CONTROL_BAR);
        } else if (view == mSecondLevelIcon) {
            mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_ENTER_SECOND_LEVEL_INDICATOR_BAR);
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        // Layout the static components.
        super.onLayout(changed, left, top, right, bottom);

        int count = getChildCount();
        if (count == 0) return;
        int width = right - left;
        int offset = 0;

        for (int i = 0 ; i < count ; i++) {
            View view = getChildAt(i);
            if (view instanceof IndicatorButton) {
                view.layout(0, offset, width, offset + width);
                offset += width;
            }
        }
        if (mCameraPicker != null) mCameraPicker.layout(0, offset, width, offset + width);
    }
}
