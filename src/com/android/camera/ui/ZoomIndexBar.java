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
import android.widget.FrameLayout;

/**
 * An indicator bar that indicates the current zoom index.
 */
public class ZoomIndexBar extends FrameLayout implements
        ZoomControl.OnZoomIndexChangedListener {
    private static final int BAR_HEIGHT_FACTOR = 7;
    private View mIndexBar;
    private double mIndexPosition; // The index position is between 0 and 1.0.
    private int mDegree;

    public ZoomIndexBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mIndexBar = findViewById(R.id.zoom_index);
    }

    public void onZoomIndexChanged(double indexPosition) {
        mIndexPosition = indexPosition;
        requestLayout();
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int barHeight = height / BAR_HEIGHT_FACTOR;
        int barTop = (int) ((height - barHeight) * (1 - mIndexPosition));
        if (mDegree == 180) {
            mIndexBar.layout(left, bottom - barTop - barHeight, right,
                    bottom - barTop);
        } else {
            mIndexBar.layout(left, barTop, right, barTop + barHeight);
        }
    }

    public void setDegree(int degree) {
        if ((degree != mDegree) && ((degree == 180) || (mDegree == 180))) requestLayout();
        mDegree = degree;
    }
}
