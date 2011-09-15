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
 * A view that contains camera setting indicators which are spread over a
 * vertical bar in preview frame.
 */
public class SecondLevelIndicatorControlBar extends IndicatorControl implements
        View.OnClickListener {
    private static final String TAG = "SecondLevelIndicatorControlBar";
    private static int ICON_SPACING = Util.dpToPixel(32);
    private ImageView mCloseIcon;
    private View mDivider; // the divider line
    int mDegree = 0;
    int mSelectedIndex = -1;

    public SecondLevelIndicatorControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mDivider = findViewById(R.id.divider);
    }

    public void initialize(Context context, PreferenceGroup group,
            String[] keys, String[] otherSettingKeys) {
        if (mCloseIcon == null) {
            mCloseIcon = new ColorFilterImageView(context);
            mCloseIcon.setImageResource(R.drawable.btn_close_settings);
            mCloseIcon.setOnClickListener(this);
            addView(mCloseIcon);
        }
        setPreferenceGroup(group);
        addControls(keys, otherSettingKeys);
        if (mDegree != 0) setDegree(mDegree);
    }

    public void onClick(View view) {
        dismissSettingPopup();
        mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR);
    }

    private int getTouchViewIndex(int y, int height) {
        int padding = getPaddingTop();
        int iconHeight = mCloseIcon.getMeasuredHeight();
        int totalIconHeight = iconHeight + ICON_SPACING;
        // If the current touch location is on close icon and above.
        if (y < padding + totalIconHeight) return indexOfChild(mCloseIcon);

        // The first two views are close icon and divider line, we have to
        // calculate if the touch event is on the rest indicator buttons.
        int count = getChildCount();
        if (count < 3) return -1;
        int selectionHeight = (count - 2) * totalIconHeight - (ICON_SPACING / 2);
        int selectionY = height - padding - selectionHeight;
        if (y < selectionY) return -1;
        return (2 + (y - selectionY) / totalIconHeight);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;

        int action = event.getAction();
        if (!isEnabled()) return false;

        double x = (double) event.getX();
        double y = (double) event.getY();
        int height = getHeight();
        if (height == 0) return false; // the event is sent before onMeasure()
        if (x > getWidth()) x = getWidth();
        if (y >= height) y = height - 1;

        int index = getTouchViewIndex((int) y, height);
        if (index == -1) return true;
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
        int iconHeight = mCloseIcon.getMeasuredHeight();
        int padding = getPaddingTop();

        // The first icon is close button.
        mCloseIcon.layout(0, padding, width, (padding + iconHeight));
        // And layout the divider line.
        mDivider.layout(0, (padding + iconHeight),
                width, (padding + iconHeight + mDivider.getMeasuredHeight()));

        // Layout from the last icon up.
        int startY = height - iconHeight - padding;
        int decrement = iconHeight + ICON_SPACING;
        for (int i = count - 1; i > 1; --i) {
            getChildAt(i).layout(0, startY, width, startY + iconHeight);
            startY -= decrement;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mCurrentMode == MODE_VIDEO) {
            mCloseIcon.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        }
        mCloseIcon.setEnabled(enabled);
    }
}
