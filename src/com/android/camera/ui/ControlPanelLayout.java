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
import android.util.Log;
import android.widget.RelativeLayout;

/**
 * A layout which handles the the width of the control panel, which contains
 * the shutter button, thumbnail, front/back camera picker, and mode picker.
 * The purpose of this is to have a consistent width of control panel in camera,
 * camcorder, and panorama modes. The control panel can also be GONE and the
 * preview can expand to full-screen in panorama.
 */
public class ControlPanelLayout extends RelativeLayout {
    private static final String TAG = "ControlPanelLayout";

    public ControlPanelLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int widthSpecSize = MeasureSpec.getSize(widthSpec);
        int heightSpecSize = MeasureSpec.getSize(heightSpec);
        int widthMode = MeasureSpec.getMode(widthSpec);
        int measuredWidth = 0;

        if (widthSpecSize > 0 && heightSpecSize > 0 && widthMode == MeasureSpec.AT_MOST) {
            // Calculate how big 4:3 preview occupies. Then deduct it from the
            // width of the parent.
            measuredWidth = (int) (widthSpecSize - heightSpecSize / 3.0 * 4.0 - 16);
        } else {
            Log.e(TAG, "layout_width of ControlPanelLayout should be wrap_content");
        }

        // Make sure the width is bigger than the minimum width.
        int minWidth = getSuggestedMinimumWidth();
        if (minWidth > measuredWidth) {
            measuredWidth = minWidth;
        }

        // The width cannot be bigger than the constraint.
        if (widthMode == MeasureSpec.AT_MOST && measuredWidth > widthSpecSize) {
            measuredWidth = widthSpecSize;
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                heightSpec);
    }
}
