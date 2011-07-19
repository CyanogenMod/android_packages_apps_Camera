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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * A view that contains camera setting indicators which are spread over a
 * vertical bar in preview frame.
 */
public class IndicatorBar extends IndicatorControl {
    private static final String TAG = "IndicatorBar";
    int mSelectedIndex = -1;

    public IndicatorBar(Context context) {
        super(context);
    }

    public IndicatorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;

        int action = event.getAction();

        if (!isEnabled()) return false;

        double x = (double) event.getX();
        double y = (double) event.getY();
        if (x > getWidth() || x < 0) return false;
        if (y > getHeight() || y < 0) return false;

        int index = (int) (y * getChildCount()) / getHeight();
        AbstractIndicatorButton b = (AbstractIndicatorButton) getChildAt(index);
        b.dispatchTouchEvent(event);
        if ((mSelectedIndex != -1) && (index != mSelectedIndex)) {
            AbstractIndicatorButton c = (AbstractIndicatorButton) getChildAt(mSelectedIndex);
            event.setAction(MotionEvent.ACTION_CANCEL);
            c.dispatchTouchEvent(event);
            c.dismissPopup();

            if (action == MotionEvent.ACTION_MOVE) {
                event.setAction(MotionEvent.ACTION_DOWN);
                b.dispatchTouchEvent(event);
            }
        }
        mSelectedIndex = index;
        return true;
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;
        int width = right - left;
        int height = bottom - top;
        int h = height / count;
        for (int i = 0; i < count; i++) {
            getChildAt(i).layout(0, top + i * height / count, width,
                    top + i * height / count + h);
        }
    }
}
