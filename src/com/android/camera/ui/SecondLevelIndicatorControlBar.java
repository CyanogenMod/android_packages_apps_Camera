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
    private View mIndicatorHighlight; // the side highlight bar
    private View mPopupedIndicator;
    int mDegree = 0;
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
        mIndicatorHighlight = findViewById(R.id.indicator_highlight);
        mCloseIcon = findViewById(R.id.back_to_first_level);
        mCloseIcon.setOnClickListener(this);
    }

    public void initialize(Context context, PreferenceGroup group,
            String[] keys, String[] otherSettingKeys) {

        setPreferenceGroup(group);
        mNonIndicatorButtonCount = getChildCount();
        addControls(keys, otherSettingKeys);
        if (mDegree != 0) setDegree(mDegree);
    }

    public void onClick(View view) {
        dismissSettingPopup();
        mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR);
    }

    private int getTouchViewIndex(int y, int height) {
        // If the current touch location is on close icon and above.
        if (y < mCloseIcon.getBottom()) return indexOfChild(mCloseIcon);

        // Calculate if the touch event is on the indicator buttons.
        int count = getChildCount();
        if (count == mNonIndicatorButtonCount) return -1;
        // The baseline will be the first indicator button's top minus spacing.
        View firstIndicatorButton = getChildAt(mNonIndicatorButtonCount);
        int baselineY = firstIndicatorButton.getTop() - (ICON_SPACING / 2);
        if (y < baselineY) return -1;
        int iconHeight = firstIndicatorButton.getMeasuredHeight();
        int buttonRange = iconHeight + ICON_SPACING;
        return (mNonIndicatorButtonCount + (y - baselineY) / buttonRange);
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
    public IndicatorButton addIndicator(Context context, IconListPreference pref) {
        IndicatorButton b = super.addIndicator(context, pref);
        b.setIndicatorChangeListener(this);
        return b;
    }

    @Override
    public OtherSettingIndicatorButton addOtherSettingIndicator(Context context,
            int resId, String[] keys) {
        OtherSettingIndicatorButton b =
                super.addOtherSettingIndicator(context, resId, keys);
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
        int offsetY = padding;
        mCloseIcon.layout(0, padding, width, (padding + iconHeight));

        // And layout the divider line.
        offsetY += (iconHeight + padding);
        mDivider.layout(padding, offsetY,
                (width - padding), (offsetY + mDivider.getMeasuredHeight()));

        // Layout from the last icon up.
        int startY = height - iconHeight - padding;
        int decrement = iconHeight + ICON_SPACING;
        for (int i = count - 1; i >= mNonIndicatorButtonCount; --i) {
            getChildAt(i).layout(0, startY, width, startY + iconHeight);
            startY -= decrement;
        }

        // Hightlight the selected indicator if exists.
        if (mPopupedIndicator == null) {
            mIndicatorHighlight.setVisibility(View.GONE);
        } else {
            mIndicatorHighlight.setVisibility(View.VISIBLE);
            // Keep the top and bottom of the hightlight the same as
            // the 'active' indicator button.
            mIndicatorHighlight.layout(0, mPopupedIndicator.getTop(),
                    mIndicatorHighlight.getLayoutParams().width,
                    mPopupedIndicator.getBottom());
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
