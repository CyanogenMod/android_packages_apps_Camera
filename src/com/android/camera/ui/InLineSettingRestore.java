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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.camera.ListPreference;
import com.android.camera.R;

/* A restore setting is simply showing the restore title. */
public class InLineSettingRestore extends InLineSettingItem {

    public InLineSettingRestore(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setTitle(ListPreference preference) {
        ((TextView) findViewById(R.id.title)).setText(
                getContext().getString(R.string.pref_restore_detail));
    }

    @Override
    protected void updateView() { }
}
