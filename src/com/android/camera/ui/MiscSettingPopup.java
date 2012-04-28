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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;

import com.android.camera.R;

// A popup window that shows one camera setting without the title. This
// is used in InLineSettingKnob.
public class MiscSettingPopup extends RotateLayout {
    private static final String TAG = "MiscSettingPopup";

    protected AbsListView mSettingList;

    public MiscSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSettingList = (AbsListView) findViewById(R.id.settingList);
    }

    public void setAdapter(ListAdapter adapter) {
        mSettingList.setAdapter(adapter);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mSettingList.setOnItemClickListener(listener);
    }

    public void setSelection(int position) {
        mSettingList.setItemChecked(position, true);
    }

}
