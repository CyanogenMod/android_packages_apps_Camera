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
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.camera.IconListPreference;
import com.android.camera.R;
import com.android.camera.Util;

// A popup window that shows one camera setting. The title is the name of the
// setting (ex: white-balance). The entries are the supported values (ex:
// daylight, incandescent, etc).
public class BasicSettingPopup extends AbstractSettingPopup implements
        View.OnClickListener {
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
            row.setSelected(index == i);

            // Initialize the icon.
            if (iconIds != null) {
                Drawable drawable = context.getResources().getDrawable(iconIds[i]);
                ImageView image = (ImageView) row.findViewById(R.id.image);
                image.setImageDrawable(drawable);
                image.setClickable(false);
            }
            row.setOnClickListener(this);
            mContentPanel.addView(row);
        }
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void onClick(View view) {
        // If popup window is dismissed, ignore the event. This may happen when
        // users press home and then select a setting immediately.
        if (getVisibility() == View.INVISIBLE) return;

        int i = mContentPanel.indexOfChild(view);
        int oldIndex = mPreference.findIndexOfValue(mPreference.getValue());
        if ((i != -1) && (oldIndex != i)) {
            mContentPanel.getChildAt(oldIndex).setSelected(false);
            view.setSelected(true);
            mPreference.setValueIndex(i);
            if (mListener != null) mListener.onSettingChanged();
        }
    }
}
