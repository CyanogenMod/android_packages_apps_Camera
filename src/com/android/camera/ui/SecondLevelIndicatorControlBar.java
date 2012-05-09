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
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.camera.IconListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;
import com.android.camera.Util;

/**
 * A view that contains camera setting indicators which are spread over a
 * vertical bar in preview frame.
 */
public class SecondLevelIndicatorControlBar extends IndicatorControl implements
        View.OnClickListener, AbstractIndicatorButton.IndicatorChangeListener {
    @SuppressWarnings("unused")
    private static final String TAG = "SecondLevelIndicatorControlBar";
    private static int ICON_SPACING = Util.dpToPixel(16);
    private View mCloseIcon;
    private View mDivider; // the divider line
    private View mPopupedIndicator;
    int mOrientation = 0;
    int mSelectedIndex = -1;
    // There are some views in the ViewGroup before adding the indicator buttons,
    // such as Close icon, divider line and the highlight bar, we need to
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
        mNonIndicatorButtonCount = getChildCount();
    }

    public void initialize(Context context, PreferenceGroup group,
            String[] keys, String[] otherSettingKeys) {

        setPreferenceGroup(group);

        // Remove the original setting indicators. This happens when switching
        // between front and back cameras.
        int count = getChildCount() - mNonIndicatorButtonCount;
        if (count > 0) removeControls(mNonIndicatorButtonCount, count);

        addControls(keys, otherSettingKeys);
        if (mOrientation != 0) setOrientation(mOrientation, false);

        // Do not grey out the icons when taking a picture.
        setupFilter(mCurrentMode != MODE_CAMERA);
    }

    @Override
    public void onClick(View view) {
        dismissSettingPopup();
        mOnIndicatorEventListener.onIndicatorEvent(
                    OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR);
    }

    private int getTouchViewIndex(int touchPosition, boolean isLandscape) {
        // If the current touch location is on close icon and above.
        if (isLandscape) {
            if (touchPosition < mCloseIcon.getBottom()) return indexOfChild(mCloseIcon);
        } else {
            if (touchPosition > mCloseIcon.getLeft()) return indexOfChild(mCloseIcon);
        }

        // Calculate if the touch event is on the indicator buttons.
        int count = getChildCount();
        if (count == mNonIndicatorButtonCount) return -1;
        // The baseline will be the first indicator button's top minus spacing.
        View firstIndicatorButton = getChildAt(mNonIndicatorButtonCount);
        if (isLandscape) {
            int baseline = firstIndicatorButton.getTop() - (ICON_SPACING / 2);
            if (touchPosition < baseline) return -1;
            int iconHeight = firstIndicatorButton.getMeasuredHeight();
            int buttonRange = iconHeight + ICON_SPACING;
            return (mNonIndicatorButtonCount + ((touchPosition - baseline) / buttonRange));
        } else {
            int baseline = firstIndicatorButton.getRight() + (ICON_SPACING / 2);
            if (touchPosition > baseline) return -1;
            int iconWidth = firstIndicatorButton.getMeasuredWidth();
            int buttonRange = iconWidth + ICON_SPACING;
            return (mNonIndicatorButtonCount + ((baseline - touchPosition) / buttonRange));
        }
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

        int index = 0;
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // the X (Y) of touch point for portrait (landscape) orientation
        int touchPosition = (int) (isLandscape ? event.getY() : event.getX());
        // second-level indicator control bar width (height) for portrait
        // (landscape) orientation
        int controlBarLength = isLandscape ? getHeight() : getWidth();
        if (controlBarLength == 0) return false; // the event is sent before onMeasure()
        if (touchPosition >= controlBarLength) {
            touchPosition = controlBarLength - 1;
        }
        index = getTouchViewIndex(touchPosition, isLandscape);

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
        b.setId(R.id.other_setting_indicator);
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
    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        super.setOrientation(orientation, animation);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;
        int width = right - left;
        int height = bottom - top;

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            int iconSize = mCloseIcon.getMeasuredHeight();
            int padding = getPaddingTop();
            // The first icon is close button.
            int offset = padding;
            mCloseIcon.layout(0, offset, width, (offset + iconSize));
            // And layout the divider line.
            offset += (iconSize + padding);
            mDivider.layout(padding, offset, (width - padding),
                    (offset + mDivider.getMeasuredHeight()));
            // Layout from the last icon up.
            int startY = height - iconSize - padding;
            int decrement = iconSize + ICON_SPACING;
            for (int i = count - 1; i >= mNonIndicatorButtonCount; --i) {
                getChildAt(i).layout(0, startY, width, startY + iconSize);
                startY -= decrement;
            }
        } else {
            int iconSize = mCloseIcon.getMeasuredWidth();
            int padding = getPaddingLeft();
            // Layout from the last icon up.
            int offset = padding;
            int increment = iconSize + ICON_SPACING;
            for (int i = count - 1; i >= mNonIndicatorButtonCount; --i) {
                getChildAt(i).layout(offset, 0, offset + iconSize, height);
                offset += increment;
            }
            // And layout the divider line.
            offset = width - iconSize - 2 * padding;
            mDivider.layout(offset, padding,
                    (offset + mDivider.getMeasuredWidth()), (height - padding));
            offset = width - iconSize - padding;
            // The first icon is close button.
            mCloseIcon.layout(offset, 0, (offset + iconSize), height);
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
