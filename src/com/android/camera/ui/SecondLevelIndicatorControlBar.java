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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

/**
 * A view that contains camera setting indicators which are spread over a
 * vertical bar in preview frame.
 */
public class SecondLevelIndicatorControlBar extends IndicatorControl implements
        View.OnClickListener {
    private static final String TAG = "SecondLevelIndicatorControlBar";
    private ImageView mCloseIcon;
    int mDegree = 0;
    int mSelectedIndex = -1;

    public SecondLevelIndicatorControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void initialize(Context context, PreferenceGroup group,
            String[] keys, String[] otherSettingKeys) {
        if (mCloseIcon == null) {
            mCloseIcon = new ImageView(context);
            mCloseIcon.setImageResource(R.drawable.btn_close_settings);
            mCloseIcon.setOnClickListener(this);
            addView(mCloseIcon);
        }
        super.initialize(context, group, keys, otherSettingKeys);
        if (mDegree != 0) setDegree(mDegree);
    }

    public void onClick(View view) {
        dismissSettingPopup();
        mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;

        int action = event.getAction();
        if (!isEnabled()) return false;

        double x = (double) event.getX();
        double y = (double) event.getY();
        int height = getHeight();
        if (x > getWidth()) x = getWidth();
        if (y >= height) y = height - 1;

        int index = (int) (y * getChildCount()) / height;
        View b = getChildAt(index);
        b.dispatchTouchEvent(event);
        if ((mSelectedIndex != -1) && (index != mSelectedIndex)) {
            View v = getChildAt(mSelectedIndex);
            if (v instanceof AbstractIndicatorButton) {
                AbstractIndicatorButton c = (AbstractIndicatorButton) v;
                event.setAction(MotionEvent.ACTION_CANCEL);
                c.dispatchTouchEvent(event);
                c.dismissPopup();
            }

            if (action == MotionEvent.ACTION_MOVE) {
                event.setAction(MotionEvent.ACTION_DOWN);
                b.dispatchTouchEvent(event);
            }
        }
        mSelectedIndex = index;
        return true;
    }

    @Override
    public void setDegree(int degree) {
        mDegree = degree;
        super.setDegree(degree);
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
