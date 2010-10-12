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
import android.widget.Button;

public class IndicatorButton extends Button {
    private IconListPreference mPreference;

    public IndicatorButton(Context context, IconListPreference pref) {
        super(context);
        mPreference = pref;
        setClickable(false);
        reloadPreference();
    }

    public void reloadPreference() {
        int index = mPreference.findIndexOfValue(mPreference.getValue());
        setBackgroundResource(mPreference.getLargeIconIds()[index]);
    }
}
