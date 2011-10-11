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

import com.android.camera.ListPreference;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A one-line camera setting could be one of three types: knob, switch or restore
 * preference button. The setting includes a title for showing the preference
 * title which is initialized in the SimpleAdapter. A knob also includes
 * (ex: Picture size), a previous button, the current value (ex: 5MP),
 * and a next button. A switch, i.e. the preference RecordLocationPreference,
 * has only two values on and off which will be controlled in a switch button.
 * Other setting popup window includes several InLineSettingItem items with
 * different types if possible.
 */
public abstract class InLineSettingItem extends LinearLayout {
    private Listener mListener;
    protected ListPreference mPreference;
    protected int mIndex;
    // Scene mode can override the original preference value.
    protected String mOverrideValue;

    static public interface Listener {
        public void onSettingChanged();
    }

    public InLineSettingItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void setTitle(ListPreference preference) {
        ((TextView) findViewById(R.id.title)).setText(preference.getTitle());
    }

    public void initialize(ListPreference preference) {
        setTitle(preference);
        if (preference == null) return;
        mPreference = preference;
        reloadPreference();
    }

    protected abstract void updateView();

    protected boolean changeIndex(int index) {
        if (index >= mPreference.getEntryValues().length || index < 0) return false;
        mIndex = index;
        mPreference.setValueIndex(mIndex);
        if (mListener != null) {
            mListener.onSettingChanged();
        }
        updateView();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        return true;
    }

    // The value of the preference may have changed. Update the UI.
    public void reloadPreference() {
        mIndex = mPreference.findIndexOfValue(mPreference.getValue());
        updateView();
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public void overrideSettings(String value) {
        mOverrideValue = value;
        updateView();
    }
}
