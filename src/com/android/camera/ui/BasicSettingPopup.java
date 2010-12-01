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
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.camera.IconListPreference;
import com.android.camera.R;
import com.android.camera.Util;

// A popup window that shows one camera setting. The title is the name of the
// setting (ex: white-balance). The entries are the supported values (ex:
// daylight, incandescent, etc).
public class BasicSettingPopup extends AbstractSettingPopup implements
        View.OnTouchListener {
    private static final String TAG = "BasicSettingPopup";
    private IconListPreference mPreference;
    private Listener mListener;

    static public interface Listener {
        public void onSettingChanged();
    }

    public BasicSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(IconListPreference preference) {
        mPreference = preference;
        Context context = getContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        CharSequence[] entries = mPreference.getEntries();
        CharSequence[] values = mPreference.getEntryValues();
        int[] iconIds = mPreference.getImageIds();
        if (iconIds == null) {
            iconIds = mPreference.getLargeIconIds();
        }
        int index = mPreference.findIndexOfValue(mPreference.getValue());

        // Set title.
        mTitle.setText(mPreference.getTitle());

        int pos = 0;
        for (int i = 0, n = entries.length; i < n; ++i) {
            LinearLayout row = (LinearLayout) inflater.inflate(
                    R.layout.setting_item, this, false);
            // Initialize the text.
            TextView text = (TextView) row.findViewById(R.id.text);
            text.setText(entries[i].toString());
            text.setClickable(false);
            row.setPressed(index == i);

            // Initialize the icon.
            if (iconIds != null) {
                Drawable drawable = context.getResources().getDrawable(iconIds[i]);
                ImageView image = (ImageView) row.findViewById(R.id.image);
                image.setImageDrawable(drawable);
                image.setClickable(false);
            }
            mContentPanel.addView(row);
        }
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContentPanel.setOnTouchListener(this);
    }

    public boolean onTouch(View view, MotionEvent event) {
        int action = event.getAction();
        ViewGroup group = (ViewGroup) view;
        if (action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_DOWN) {
            int y = (int) event.getY();
            int childCount = group.getChildCount();
            // Check which child is pressed.
            for (int i = 0; i < childCount; i++) {
                View child = group.getChildAt(i);
                if (y >= child.getTop() && y <= child.getBottom()) {
                    int oldIndex = mPreference.findIndexOfValue(mPreference.getValue());
                    if (oldIndex != i) {
                        View oldRow = group.getChildAt(oldIndex);
                        oldRow.setPressed(false);
                        child.setPressed(true);
                        mPreference.setValueIndex(i);
                        if (mListener != null) {
                            mListener.onSettingChanged();
                        }
                    }
                    break;
                }
            }
            return true;
        }
        return false;
    }
}
