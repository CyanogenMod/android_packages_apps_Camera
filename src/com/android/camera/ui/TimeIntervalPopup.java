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

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.R;

/**
 * This is a popup window that allows users to turn on/off time lapse feature,
 * and to select a time interval for taking a time lapse video.
 */
public class TimeIntervalPopup extends AbstractSettingPopup {
    private static final String TAG = "TimeIntervalPopup";
    private NumberPicker mNumberSpinner;
    private NumberPicker mUnitSpinner;
    private Switch mTimeLapseSwitch;
    private final String[] mUnits;
    private final String[] mDurations;
    private IconListPreference mPreference;
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

    public TimeIntervalPopup(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = context.getResources();
        mUnits = res.getStringArray(R.array.pref_video_time_lapse_frame_interval_units);
        mDurations = res
                .getStringArray(R.array.pref_video_time_lapse_frame_interval_duration_values);
    }

    public void initialize(IconListPreference preference) {
        mPreference = preference;

        // Set title.
        mTitle.setText(mPreference.getTitle());

        // Duration
        int durationCount = mDurations.length;
        mNumberSpinner = (NumberPicker) findViewById(R.id.duration);
        mNumberSpinner.setMinValue(0);
        mNumberSpinner.setMaxValue(durationCount - 1);
        mNumberSpinner.setDisplayedValues(mDurations);
        mNumberSpinner.setWrapSelectorWheel(false);

        // Units for duration (i.e. seconds, minutes, etc)
        mUnitSpinner = (NumberPicker) findViewById(R.id.duration_unit);
        mUnitSpinner.setMinValue(0);
        mUnitSpinner.setMaxValue(mUnits.length - 1);
        mUnitSpinner.setDisplayedValues(mUnits);
        mUnitSpinner.setWrapSelectorWheel(false);

        mTimePicker = findViewById(R.id.time_interval_picker);
        mTimeLapseSwitch = (Switch) findViewById(R.id.time_lapse_switch);
        mHelpText = (TextView) findViewById(R.id.set_time_interval_help_text);
        mConfirmButton = (Button) findViewById(R.id.time_lapse_interval_set_button);

        // Disable focus on the spinners to prevent keyboard from coming up
        mNumberSpinner.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        mUnitSpinner.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        mTimeLapseSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
            mTimeLapseSwitch.setChecked(false);
            setTimeSelectionEnabled(false);
        } else {
            mTimeLapseSwitch.setChecked(true);
            setTimeSelectionEnabled(true);
            int durationCount = mNumberSpinner.getMaxValue() + 1;
            int unit = (index - 1) / durationCount;
            int number = (index - 1) % durationCount;
            mUnitSpinner.setValue(unit);
            mNumberSpinner.setValue(number);
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
        if (mTimeLapseSwitch.isChecked()) {
            int newId = mUnitSpinner.getValue() * (mNumberSpinner.getMaxValue() + 1)
                    + mNumberSpinner.getValue() + 1;
            mPreference.setValueIndex(newId);
        } else {
            mPreference.setValueIndex(0);
        }

        if (mListener != null) {
            mListener.onListPrefChanged(mPreference);
        }
    }
}
