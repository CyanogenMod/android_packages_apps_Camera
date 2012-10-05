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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import com.android.camera.ListPreference;
import com.android.camera.R;

/* Setting menu item that will bring up a menu when you click on it. */
public class InLineSettingMenu extends InLineSettingItem {
    private static final String TAG = "InLineSettingMenu";
    // The view that shows the current selected setting. Ex: 5MP
    private TextView mEntry;

    public InLineSettingMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEntry = (TextView) findViewById(R.id.current_setting);
    }

    @Override
    public void initialize(ListPreference preference) {
        super.initialize(preference);
        //TODO: add contentDescription
    }

    @Override
    protected void updateView() {
        if (mOverrideValue == null) {
            mEntry.setText(mPreference.getEntry());
        } else {
            int index = mPreference.findIndexOfValue(mOverrideValue);
            if (index != -1) {
                mEntry.setText(mPreference.getEntries()[index]);
            } else {
                // Avoid the crash if camera driver has bugs.
                Log.e(TAG, "Fail to find override value=" + mOverrideValue);
                mPreference.print();
            }
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        event.getText().add(mPreference.getTitle() + mPreference.getEntry());
        return true;
    }

    @Override
    public void setEnabled(boolean enable) {
        super.setEnabled(enable);
        if (mTitle != null) mTitle.setEnabled(enable);
        if (mEntry != null) mEntry.setEnabled(enable);
    }
}
