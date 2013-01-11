/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.util.AttributeSet;

import java.util.List;

/* CountDownTimerPreference generates entries (i.e. what users see in the UI),
 * and entry values (the actual value recorded in preference) in
 * initCountDownTimeChoices(Context context), rather than reading the entries
 * from a predefined list. When the entry values are a continuous list of numbers,
 * (e.g. 0-60), it is more efficient to auto generate the list than to predefine it.*/
public class CountDownTimerPreference extends ListPreference {
    private final static int MAX_DURATION = 60;
    public CountDownTimerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initCountDownDurationChoices(context);
    }

    private void initCountDownDurationChoices(Context context) {
        CharSequence[] entryValues = new CharSequence[MAX_DURATION + 1];
        CharSequence[] entries = new CharSequence[MAX_DURATION + 1];
        for (int i = 0; i <= MAX_DURATION; i++) {
            entryValues[i] = Integer.toString(i);
            if (i == 0) {
                entries[0] = context.getString(R.string.setting_off); // Off
            } else {
                entries[i] = context.getResources()
                        .getQuantityString(R.plurals.pref_camera_timer_entry, i, i);
            }
        }
        setEntries(entries);
        setEntryValues(entryValues);
    }
}
