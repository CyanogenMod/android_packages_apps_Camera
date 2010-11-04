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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.IconListPreference;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.ui.GLListView.OnItemSelectedListener;

public class BasicSettingPicker extends LinearLayout {
    private static final String TAG = "BasicSettingPicker";
    private IconListPreference mPreference;
    private final Context mContext;
    private Listener mListener;

    static public interface Listener {
        public void onSettingChanged();
    }

    public BasicSettingPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
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
            // Add the image.
            ImageView image;
            Drawable drawable = mContext.getResources().getDrawable(imageIds[i]);
            // Sacle the image if it is too small.
            if (drawable.getIntrinsicWidth() >= getLayoutParams().width) {
                image = (ImageView) inflater.inflate(
                        R.layout.setting_image_item, null);
            } else {
                image = (ImageView) inflater.inflate(
                        R.layout.setting_scale_image_item, null);
            }
            image.setImageDrawable(drawable);
            image.setClickable(false);
            addView(image, pos++);

            // Add the text.
            TextView text = (TextView) inflater.inflate(
                    R.layout.setting_text_item, null);
            text.setText(entries[i].toString());
            text.setClickable(false);
            if (index == i) text.setPressed(true);
            addView(text, pos++);
        }
        requestLayout();
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
            for (int i = 0; i < getChildCount() - 1; i++) {
                View v = getChildAt(i);
                if (y >= v.getTop() && y <= v.getBottom()) {
                    int index = i / 2;
                    CharSequence[] values = mPreference.getEntryValues();
                    int oldIndex = mPreference.findIndexOfValue(mPreference.getValue());
                    if (oldIndex != index) {
                        View oldText = getChildAt(oldIndex * 2 + 1);
                        oldText.setPressed(false);
                        View text = getChildAt(index * 2 + 1);
                        text.setPressed(true);
                        mPreference.setValueIndex(index);
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
