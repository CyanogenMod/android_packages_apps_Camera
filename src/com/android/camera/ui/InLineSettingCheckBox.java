/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;


import com.android.camera.ListPreference;
import com.android.camera.R;

/* A check box setting control which turns on/off the setting. */
public class InLineSettingCheckBox extends InLineSettingItem {
    private CheckBox mCheckBox;

    OnCheckedChangeListener mCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean desiredState) {
            changeIndex(desiredState ? 1 : 0);
        }
    };

    public InLineSettingCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCheckBox = (CheckBox) findViewById(R.id.setting_check_box);
        mCheckBox.setOnCheckedChangeListener(mCheckedChangeListener);
    }

    @Override
    public void initialize(ListPreference preference) {
        super.initialize(preference);
        // Add content descriptions for the increment and decrement buttons.
        mCheckBox.setContentDescription(getContext().getResources().getString(
                R.string.accessibility_check_box, mPreference.getTitle()));
    }

    @Override
    protected void updateView() {
        mCheckBox.setOnCheckedChangeListener(null);
        if (mOverrideValue == null) {
            mCheckBox.setChecked(mIndex == 1);
        } else {
            int index = mPreference.findIndexOfValue(mOverrideValue);
            mCheckBox.setChecked(index == 1);
        }
        mCheckBox.setOnCheckedChangeListener(mCheckedChangeListener);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        event.getText().add(mPreference.getTitle());
        return true;
    }

    @Override
    public void setEnabled(boolean enable) {
        if (mTitle != null) mTitle.setEnabled(enable);
        if (mCheckBox != null) mCheckBox.setEnabled(enable);
    }
}
