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

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.camera.CameraSettings;
import com.android.camera.IconListPreference;
import com.android.camera.R;

// An indicator button that represents one camera setting. Ex: flash. Pressing it opens a popup
// window.
public class IndicatorButton extends AbstractIndicatorButton
        implements BasicSettingPopup.Listener, EffectSettingPopup.Listener {
    private static final String TAG = "IndicatorButton";
    private IconListPreference mPreference;
    // Scene mode can override the original preference value.
    private String mOverrideValue;
    private Listener mListener;

    static public interface Listener {
        public void onSettingChanged();
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public IndicatorButton(Context context, IconListPreference pref) {
        super(context);
        mPreference = pref;
        reloadPreference();
    }

    @Override
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
        super.reloadPreference();
    }

    public String getKey() {
        return mPreference.getKey();
    }

    @Override
    public boolean isOverridden() {
        return mOverrideValue != null;
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        mOverrideValue = null;
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            if (key.equals(getKey())) {
                mOverrideValue = value;
                setEnabled(value == null);
                break;
            }
        }
        reloadPreference();
    }

    @Override
    protected void initializePopup() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup root = (ViewGroup) getRootView().findViewById(R.id.frame_layout);

        AbstractSettingPopup popup;
        if (CameraSettings.KEY_VIDEO_EFFECT.equals(getKey())) {
            EffectSettingPopup effect = (EffectSettingPopup) inflater.inflate(
                    R.layout.effect_setting_popup, root, false);
            effect.initialize(mPreference);
            effect.setSettingChangedListener(this);
            mPopup = effect;
        } else {
            BasicSettingPopup basic = (BasicSettingPopup) inflater.inflate(
                    R.layout.basic_setting_popup, root, false);
            basic.initialize(mPreference);
            basic.setSettingChangedListener(this);
            mPopup = basic;
        }
        root.addView(mPopup);
    }

    @Override
    public void onSettingChanged() {
        reloadPreference();
        // Dismiss later so the activated state can be updated before dismiss.
        dismissPopupDelayed();
        if (mListener != null) {
            mListener.onSettingChanged();
        }
    }
}
