/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2012 The CyanogenMod Project
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
import android.widget.AbsListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;

// A popup window that shows one camera setting without modifying preference.
// This is used in InLineSettingKnob.
public class MiscSettingPopup extends AbstractSettingPopup {

    public MiscSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTitle(String title) {
        mTitle.setText(title);
    }

    public void setAdapter(ListAdapter adapter) {
        ((AbsListView)mSettingList).setAdapter(adapter);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        ((AbsListView)mSettingList).setOnItemClickListener(listener);
    }

    public void setSelection(int position) {
        ((AbsListView)mSettingList).setItemChecked(position, true);
    }

    @Override
    public void reloadPreference() {
        // Nothing, we hold no preference
    }

}
