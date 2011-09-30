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

import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public class OtherSettingIndicatorButton extends AbstractIndicatorButton {
    private final String TAG = "OtherSettingIndicatorButton";
    private PreferenceGroup mPreferenceGroup;
    private String[] mPrefKeys;
    private OtherSettingsPopup.Listener mListener;

    public void setSettingChangedListener(OtherSettingsPopup.Listener listener) {
        mListener = listener;
    }

    public OtherSettingIndicatorButton(Context context, int resId,
            PreferenceGroup preferenceGroup, String[] prefKeys) {
        super(context);
        setImageResource(resId);
        mPreferenceGroup = preferenceGroup;
        mPrefKeys = prefKeys;
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        if (mPopup == null) {
            initializePopup();
        }
        ((OtherSettingsPopup)mPopup).overrideSettings(keyvalues);
    }

    @Override
    protected void initializePopup() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup root = (ViewGroup) getRootView().findViewById(R.id.frame_layout);

        OtherSettingsPopup popup = (OtherSettingsPopup) inflater.inflate(
                R.layout.other_setting_popup, root, false);
        popup.setSettingChangedListener(mListener);
        popup.initialize(mPreferenceGroup, mPrefKeys);
        root.addView(popup);
        mPopup = popup;
    }
}
