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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.camera.IconListPreference;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.ui.GLListView.OnItemSelectedListener;

public class BasicSettingPopup extends LinearLayout {
    private static final String TAG = "BasicSettingPopup";
    private IconListPreference mPreference;
    private final Context mContext;
    private Listener mListener;

    static public interface Listener {
        public void onSettingChanged();
    }

    public BasicSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        // Use system holo background.
        Theme dialogTheme = getResources().newTheme();
        dialogTheme.applyStyle(android.R.style.Theme_Holo_Dialog, true);
        TypedArray ta = dialogTheme.obtainStyledAttributes(new int[] {
                android.R.attr.windowBackground });
        setBackgroundDrawable(ta.getDrawable(0));
        ta.recycle();
    }

    public void initialize(IconListPreference preference) {
        mPreference = preference;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        CharSequence[] entries = mPreference.getEntries();
        CharSequence[] values = mPreference.getEntryValues();
        int[] imageIds = mPreference.getImageIds();
        int index = preference.findIndexOfValue(preference.getValue());

        int pos = 0;
        for (int i = 0, n = entries.length; i < n; ++i) {
            LinearLayout row = (LinearLayout) inflater.inflate(
                    R.layout.setting_item, this, false);
            // Initialize the text.
            TextView text = (TextView) row.findViewById(R.id.text);
            text.setText(entries[i].toString());
            text.setClickable(false);
            if (index == i) text.setPressed(true);

            // Initialize the image.
            Drawable drawable = mContext.getResources().getDrawable(imageIds[i]);
            ImageView image = (ImageView) row.findViewById(R.id.image);
            image.setImageDrawable(drawable);
            image.setClickable(false);
            addView(row);
        }
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_DOWN) {
            int y = (int) event.getY();
            // Check which child is pressed.
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (y >= v.getTop() && y <= v.getBottom()) {
                    int oldIndex = mPreference.findIndexOfValue(mPreference.getValue());
                    if (oldIndex != i) {
                        View oldRow = getChildAt(oldIndex);
                        oldRow.findViewById(R.id.text).setPressed(false);
                        v.findViewById(R.id.text).setPressed(true);
                        mPreference.setValueIndex(i);
                        if (mListener != null) {
                            mListener.onSettingChanged();
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }
}
