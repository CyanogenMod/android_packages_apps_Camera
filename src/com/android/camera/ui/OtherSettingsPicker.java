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

import android.util.Log;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;

/* A popup window that contains several camera settings. */
public class OtherSettingsPicker extends PopupWindow
        implements InLineSettingPicker.Listener {
    private static final String TAG = "OtherSettingsPicker";
    private Listener mListener;

    static public interface Listener {
        public void onOtherSettingChanged();
    }

    public void setOtherSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public OtherSettingsPicker(View contentView, int width, int height,
            boolean focusable) {
        super(contentView, width, height, focusable);
    }

    public void initialize(PreferenceGroup group) {
        TableLayout table = (TableLayout) getContentView();
        // Initialize each camera setting.
        for (int i = 0; i < table.getChildCount(); i++) {
            TableRow row = (TableRow) table.getChildAt(i);
            InLineSettingPicker picker = (InLineSettingPicker) row.getChildAt(1);
            ListPreference pref = group.findPreference(picker.getKey());
            picker.setSharedPreferenceChangedListener(this);
            picker.initialize(pref);
       }
    }

    public void onSharedPreferenceChanged() {
        if (mListener != null) {
            mListener.onOtherSettingChanged();
        }
    }
}
