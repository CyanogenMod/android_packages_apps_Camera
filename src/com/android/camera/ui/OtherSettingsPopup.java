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
import android.widget.TableLayout;

/* A popup window that contains several camera settings. */
public class OtherSettingsPopup extends TableLayout
        implements InLineSettingPicker.Listener {
    private static final String TAG = "OtherSettingsPopup";
    private Listener mListener;

    static public interface Listener {
        public void onOtherSettingChanged();
    }

    public void setOtherSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public OtherSettingsPopup(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Use system holo background.
        Theme dialogTheme = getResources().newTheme();
        dialogTheme.applyStyle(android.R.style.Theme_Holo_Dialog, true);
        TypedArray ta = dialogTheme.obtainStyledAttributes(new int[] {
                android.R.attr.windowBackground });
        setBackgroundDrawable(ta.getDrawable(0));
        ta.recycle();
    }

    public void initialize(PreferenceGroup group) {
        // Initialize each camera setting.
        for (int i = getChildCount() - 1; i >= 0 ; i--) {
            ViewGroup row = (ViewGroup) getChildAt(i);
            InLineSettingPicker picker = (InLineSettingPicker) row.getChildAt(1);
            ListPreference pref = group.findPreference(picker.getKey());
            if (pref != null) {
                picker.setSettingChangedListener(this);
                picker.initialize(pref);
            } else {  // remove the row if the preference is not supported
                removeViewAt(i);
            }
       }
    }

    public void onSettingChanged() {
        if (mListener != null) {
            mListener.onOtherSettingChanged();
        }
    }
}
