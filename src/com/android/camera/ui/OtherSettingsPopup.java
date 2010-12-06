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

import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/* A popup window that contains several camera settings. */
public class OtherSettingsPopup extends AbstractSettingPopup
        implements InLineSettingPicker.Listener, View.OnClickListener {
    private static final String TAG = "OtherSettingsPopup";
    private Listener mListener;

    static public interface Listener {
        public void onOtherSettingChanged();
        public void onRestorePreferencesClicked();
    }

    public void setOtherSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public OtherSettingsPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(PreferenceGroup group) {
        // Initialize each camera setting.
        for (int i = mContentPanel.getChildCount() - 1; i >= 0; i--) {
            View v = mContentPanel.getChildAt(i);
            if (v instanceof InLineSettingPicker) {
                InLineSettingPicker picker = (InLineSettingPicker) v;
                ListPreference pref = group.findPreference(picker.getKey());
                if (pref != null) {
                    picker.setSettingChangedListener(this);
                    picker.initialize(pref);
                } else {  // remove the row if the preference is not supported
                    mContentPanel.removeViewAt(i);
                }
            } else {  // This row is restore defaults.
                v.setOnClickListener(this);
            }
       }
       requestLayout();
    }

    public void onSettingChanged() {
        if (mListener != null) {
            mListener.onOtherSettingChanged();
        }
    }

    // Scene mode can override other camera settings (ex: flash mode).
    public void overrideSettings(String key, String value) {
        int count = mContentPanel.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = mContentPanel.getChildAt(i);
            if (v instanceof InLineSettingPicker) {
                InLineSettingPicker picker = (InLineSettingPicker) v;
                if (key.equals(picker.getKey())) {
                    picker.overrideSettings(value);
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onRestorePreferencesClicked();
        }
    }
}
