/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import com.android.camera.ListPreference;
import com.android.camera.R;

/**
 * This is a popup window that allows users to turn on/off time lapse feature,
 * and to select a time interval for taking a time lapse video.
 */

public class TimerSettingPopup extends AbstractSettingPopup {
    private static final String TAG = "TimerSettingPopup";
    private NumberPicker mNumberSpinner;
    private Switch mTimerSwitch;
    private String[] mDurations;
    private ListPreference mPreference;
    private Listener mListener;
    private Button mConfirmButton;
    private TextView mHelpText;
    private View mTimePicker;

    static public interface Listener {
        public void onListPrefChanged(ListPreference pref);
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public TimerSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(ListPreference preference) {
        mPreference = preference;

        // Set title.
        mTitle.setText(mPreference.getTitle());

        // Duration
        CharSequence[] entries = mPreference.getEntryValues();
        mDurations = new String[entries.length - 1];
        Locale locale = getResources().getConfiguration().locale;
        for (int i = 1; i < entries.length; i++)
            mDurations[i-1] = String.format(locale, "%d",
                    Integer.parseInt(entries[i].toString()));
        int durationCount = mDurations.length;
        mNumberSpinner = (NumberPicker) findViewById(R.id.duration);
        mNumberSpinner.setMinValue(0);
        mNumberSpinner.setMaxValue(durationCount - 1);
        mNumberSpinner.setDisplayedValues(mDurations);
        mNumberSpinner.setWrapSelectorWheel(false);

        mTimerSwitch = (Switch) findViewById(R.id.timer_setting_switch);
        mHelpText = (TextView) findViewById(R.id.set_timer_help_text);
        mConfirmButton = (Button) findViewById(R.id.timer_set_button);
        mTimePicker = findViewById(R.id.time_duration_picker);

        // Disable focus on the spinners to prevent keyboard from coming up
        mNumberSpinner.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        mTimerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setTimeSelectionEnabled(isChecked);
            }
        });
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateInputState();
            }
        });
    }

    private void restoreSetting() {
        int index = mPreference.findIndexOfValue(mPreference.getValue());
        if (index == -1) {
            Log.e(TAG, "Invalid preference value.");
            mPreference.print();
            throw new IllegalArgumentException();
        } else if (index == 0) {
            // default choice: time lapse off
            mTimerSwitch.setChecked(false);
            setTimeSelectionEnabled(false);
        } else {
            mTimerSwitch.setChecked(true);
            setTimeSelectionEnabled(true);
            mNumberSpinner.setValue(index - 1);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            if (getVisibility() != View.VISIBLE) {
                // Set the number pickers and on/off switch to be consistent
                // with the preference
                restoreSetting();
            }
        }
        super.setVisibility(visibility);
    }

    protected void setTimeSelectionEnabled(boolean enabled) {
        mHelpText.setVisibility(enabled ? GONE : VISIBLE);
        mTimePicker.setVisibility(enabled ? VISIBLE : GONE);
    }

    @Override
    public void reloadPreference() {
    }

    private void updateInputState() {
        if (mTimerSwitch.isChecked()) {
            int newId = mNumberSpinner.getValue() + 1;
            mPreference.setValueIndex(newId);
        } else {
            mPreference.setValueIndex(0);
        }

        if (mListener != null) {
            mListener.onListPrefChanged(mPreference);
        }
    }
}
