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

import com.android.camera.IconListPreference;
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
        View.OnClickListener, AbstractIndicatorButton.IndicatorChangeListener {
    private static final String TAG = "SecondLevelIndicatorControlBar";
    private static int ICON_SPACING = Util.dpToPixel(16);
    private View mCloseIcon;
    private View mDivider; // the divider line
    private View mPopupedIndicator;
    int mOrientation = 0;
    int mSelectedIndex = -1;
    // There are some views in the ViewGroup before adding the indicator buttons,
    // such as Close icon, divider line and the hightlight bar, we need to
    // remember the count of the non-indicator buttons for getTouchViewIndex().
    int mNonIndicatorButtonCount;

    public SecondLevelIndicatorControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mDivider = findViewById(R.id.divider);
        mCloseIcon = findViewById(R.id.back_to_first_level);
        mCloseIcon.setOnClickListener(this);
    }

    public void initialize(Context context, PreferenceGroup group,
            String[] keys, String[] otherSettingKeys) {

        setPreferenceGroup(group);
        mNonIndicatorButtonCount = getChildCount();
        addControls(keys, otherSettingKeys);
        if (mOrientation != 0) setOrientation(mOrientation);
    }

    public void onClick(View view) {
        dismissSettingPopup();
        mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR);
    }

    private int getTouchViewIndex(int x, int width) {
        // If the current touch location is on close icon and above.
        if (x > mCloseIcon.getLeft()) return indexOfChild(mCloseIcon);

        // Calculate if the touch event is on the indicator buttons.
        int count = getChildCount();
        if (count == mNonIndicatorButtonCount) return -1;
        // The baseline will be the first indicator button's top minus spacing.
        View firstIndicatorButton = getChildAt(mNonIndicatorButtonCount);
        int baselineX = firstIndicatorButton.getRight() + (ICON_SPACING / 2);
        if (x > baselineX) return -1;
        int iconWidth = firstIndicatorButton.getMeasuredWidth();
        int buttonRange = iconWidth + ICON_SPACING;
        return (mNonIndicatorButtonCount + ((baselineX - x) / buttonRange));
    }

    private void dispatchRelativeTouchEvent(View view, MotionEvent event) {
        event.offsetLocation(-view.getLeft(), -view.getTop());
        view.dispatchTouchEvent(event);
        event.offsetLocation(view.getLeft(), view.getTop());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!onFilterTouchEventForSecurity(event)) return false;

        int action = event.getAction();
        if (!isEnabled()) return false;

        double x = (double) event.getX();
        int width = getWidth();
        if (width == 0) return false; // the event is sent before onMeasure()
        if (x > width) x = width;

        int index = getTouchViewIndex((int) x, width);

        // Cancel the previous target if we moved out of it
        if ((mSelectedIndex != -1) && (index != mSelectedIndex)) {
            View p = getChildAt(mSelectedIndex);

            int oldAction = event.getAction();
            event.setAction(MotionEvent.ACTION_CANCEL);
            dispatchRelativeTouchEvent(p, event);
            event.setAction(oldAction);

            if (p instanceof AbstractIndicatorButton) {
                AbstractIndicatorButton b = (AbstractIndicatorButton) p;
                b.dismissPopup();
            }
        }

        // Send event to the target
        View v = getChildAt(index);
        if (v == null) return true;

        // Change MOVE to DOWN if this is a new target
        if (mSelectedIndex != index && action == MotionEvent.ACTION_MOVE) {
            event.setAction(MotionEvent.ACTION_DOWN);
        }
        dispatchRelativeTouchEvent(v, event);
        mSelectedIndex = index;
        return true;
    }

    @Override
    public IndicatorButton addIndicator(Context context, IconListPreference pref) {
        IndicatorButton b = super.addIndicator(context, pref);
        b.setBackgroundResource(R.drawable.bg_pressed);
        b.setIndicatorChangeListener(this);
        return b;
    }

    @Override
    public OtherSettingIndicatorButton addOtherSettingIndicator(Context context,
            int resId, String[] keys) {
        OtherSettingIndicatorButton b =
                super.addOtherSettingIndicator(context, resId, keys);
        b.setBackgroundResource(R.drawable.bg_pressed);
        b.setIndicatorChangeListener(this);
        return b;
    }

    @Override
    public void onShowIndicator(View view, boolean showed) {
        // Ignore those events if not current popup.
        if (!showed && (mPopupedIndicator != view)) return;
        mPopupedIndicator = (showed ? view : null);
        // Show or dismiss the side indicator highlight.
        requestLayout();
    }

    @Override
    public void setOrientation(int orientation) {
        mOrientation = orientation;
        super.setOrientation(orientation);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;
        int width = right - left;
        int height = bottom - top;
        int iconWidth = mCloseIcon.getMeasuredWidth();
        int padding = getPaddingLeft();

        // Layout from the last icon up.
        int offsetX = padding;
        int increment = iconWidth + ICON_SPACING;
        for (int i = count - 1; i >= mNonIndicatorButtonCount; --i) {
            getChildAt(i).layout(offsetX, 0, offsetX + iconWidth, height);
            offsetX += increment;
        }

        // And layout the divider line.
        offsetX = width - iconWidth - 2 * padding;
        mDivider.layout(offsetX, padding, (offsetX + mDivider.getMeasuredWidth()),
                (height - padding));

        offsetX = width - iconWidth - padding;
        // The first icon is close button.
        mCloseIcon.layout(offsetX, 0, (offsetX + iconWidth), height);
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
