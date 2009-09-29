/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.preference.ListPreference;
import android.util.AttributeSet;

/** {@code BooleanListPreference} is used for those {@code SharedPreference}
 * which stores value in the type of {@code Boolean} but would like to be shown
 * as two radio buttons instead of a checkbox.
 */
public class BooleanListPreference extends ListPreference {

    public BooleanListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        CharSequence values[] = getEntryValues();
        if (values.length == 2) {
            boolean x = Boolean.valueOf(values[0].toString());
            boolean y = Boolean.valueOf(values[1].toString());
            if (x != y) return;
        }
        throw new IllegalStateException(
                "EntryValues should be boolean strings");
    }

    @Override
    protected boolean persistString(String value) {
        return persistBoolean(Boolean.valueOf(value));
    }

    @Override
    protected String getPersistedString(String defaultValue) {
        return Boolean.toString(
                getPersistedBoolean(Boolean.valueOf(defaultValue)));
    }
}
