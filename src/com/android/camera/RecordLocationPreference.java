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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

/**
 * {@code RecordLocationPreference} is used to keep the "store locaiton"
 * option in {@code SharedPreference}.
 */
public class RecordLocationPreference extends IconListPreference {

    public static final String KEY = "pref_camera_recordlocation_key";

    public static final String VALUE_NONE = "none";

    private final ContentResolver mResolver;

    public RecordLocationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResolver = context.getContentResolver();
    }

    @Override
    public String getValue() {
        return get(getSharedPreferences(), mResolver) ? CameraSettings.VALUE_ON : CameraSettings.VALUE_OFF;
    }

    public static boolean get(
            SharedPreferences pref, ContentResolver resolver) {
        String value = pref.getString(KEY, VALUE_NONE);
        return CameraSettings.VALUE_ON.equals(value);
    }

    public static boolean isSet(SharedPreferences pref) {
        String value = pref.getString(KEY, VALUE_NONE);
        return !VALUE_NONE.equals(value);
    }
}
