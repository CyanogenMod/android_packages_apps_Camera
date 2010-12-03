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

import com.android.camera.IconListPreference;

import android.content.Context;
import android.widget.ImageView;
import android.util.Log;

public class IndicatorButton extends ImageView {
    private final String TAG = "IndicatorButton";
    private IconListPreference mPreference;
    // Scene mode can override the original preference value.
    private String mOverrideValue;

    public IndicatorButton(Context context, IconListPreference pref) {
        super(context);
        mPreference = pref;
        setClickable(false);
        reloadPreference();
    }

    public void reloadPreference() {
        int[] iconIds = mPreference.getLargeIconIds();
        if (iconIds != null) {
            // Each entry has a corresponding icon.
            int index;
            if (mOverrideValue == null) {
                index = mPreference.findIndexOfValue(mPreference.getValue());
            } else {
                index = mPreference.findIndexOfValue(mOverrideValue);
                if (index == -1) {
                    // Avoid the crash if camera driver has bugs.
                    Log.e(TAG, "Fail to find override value=" + mOverrideValue);
                    mPreference.print();
                    return;
                }
            }
            setImageResource(iconIds[index]);
        } else {
            // The preference only has a single icon to represent it.
            setImageResource(mPreference.getSingleIcon());
        }
    }

    public String getKey() {
        return mPreference.getKey();
    }

    public boolean isOverridden() {
        return mOverrideValue != null;
    }

    public void overrideSettings(String value) {
        mOverrideValue = value;
        reloadPreference();
    }
}
