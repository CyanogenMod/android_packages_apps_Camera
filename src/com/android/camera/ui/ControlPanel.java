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

import com.android.camera.CameraSettings;
import com.android.camera.ComboPreferences;
import com.android.camera.IconListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import java.util.ArrayList;

public class ControlPanel extends RelativeLayout
        implements BasicSettingPopup.Listener, IndicatorWheel.Listener,
        OtherSettingsPopup.Listener,PopupWindow.OnDismissListener {
    private static final String TAG = "ControlPanel";
    private Context mContext;
    private ComboPreferences mSharedPrefs;
    private PreferenceGroup mPreferenceGroup;
    private ArrayList<String> mPreferenceKeys;
    private Listener mListener;
    private IndicatorWheel mIndicatorWheel;
    private BasicSettingPopup[] mBasicSettingPopups;
    private OtherSettingsPopup mOtherSettingsPopup;
    private int mActiveIndicator = -1;
    private boolean mEnabled = true;

    static public interface Listener {
        public void onSharedPreferenceChanged();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public ControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    protected boolean addIndicator(
            Context context, PreferenceGroup group, String key) {
        IconListPreference pref = (IconListPreference) group.findPreference(key);
        if (pref == null) return false;
        IndicatorButton b = new IndicatorButton(context, pref);
        mIndicatorWheel.addView(b);
        return true;
    }

    private void addOtherSettingIndicator(Context context) {
        Button b = new Button(context);
        b.setBackgroundResource(R.drawable.ic_viewfinder_settings);
        b.setClickable(false);
        mIndicatorWheel.addView(b);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIndicatorWheel = (IndicatorWheel) findViewById(R.id.indicator_wheel);
        mIndicatorWheel.setListener(this);
    }

    public void initialize(Context context, PreferenceGroup group,
            String[] keys, boolean enableOtherSettings) {
        // Reset the variables and states.
        dismissSettingPopup();
        mIndicatorWheel.removeIndicators();
        mOtherSettingsPopup = null;
        mActiveIndicator = -1;
        mPreferenceKeys = new ArrayList<String>();

        // Initialize all variables and icons.
        mPreferenceGroup = group;
        mSharedPrefs = ComboPreferences.get(context);
        for (int i = 0; i < keys.length; i++) {
            if (addIndicator(context, group, keys[i])) {
                mPreferenceKeys.add(keys[i]);
            }
        }
        mBasicSettingPopups = new BasicSettingPopup[mPreferenceKeys.size()];

        if (enableOtherSettings) {
            addOtherSettingIndicator(context);
        }
        requestLayout();
    }

    public void onOtherSettingChanged() {
        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    public void onSettingChanged() {
        mIndicatorWheel.updateIndicator(mActiveIndicator);
        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    public void onIndicatorClicked(int index) {
        if (!mEnabled) return;
        if (index < mBasicSettingPopups.length) {
            if (mBasicSettingPopups[index] == null) {
                initializeSettingPopup(index);
            }
        } else if (mOtherSettingsPopup == null) {
            initializeOtherSettingPopup();
        }
        showSettingPopup(index);
    }

    private void initializeSettingPopup(int index) {
        IconListPreference pref = (IconListPreference)
                mPreferenceGroup.findPreference(mPreferenceKeys.get(index));

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup root = (ViewGroup) getRootView().findViewById(R.id.app_root);
        BasicSettingPopup popup = (BasicSettingPopup) inflater.inflate(
                R.layout.basic_setting_popup, root, false);
        mBasicSettingPopups[index] = popup;
        popup.setSettingChangedListener(this);
        popup.initialize(pref);
        root.addView(popup);
    }

    private void initializeOtherSettingPopup() {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup root = (ViewGroup) getRootView().findViewById(R.id.app_root);
        mOtherSettingsPopup = (OtherSettingsPopup) inflater.inflate(
                R.layout.other_setting_popup, root, false);
        mOtherSettingsPopup.setOtherSettingChangedListener(this);
        mOtherSettingsPopup.initialize(mPreferenceGroup);
        root.addView(mOtherSettingsPopup);
    }

    private void showSettingPopup(int index) {
        if (mActiveIndicator == index) return;
        dismissSettingPopup();
        if (index == mBasicSettingPopups.length) {
            mOtherSettingsPopup.setVisibility(View.VISIBLE);
        } else {
            mBasicSettingPopups[index].setVisibility(View.VISIBLE);
        }
        mActiveIndicator = index;
    }

    public boolean dismissSettingPopup() {
        if (mActiveIndicator >= 0) {
            if (mActiveIndicator == mBasicSettingPopups.length) {
                mOtherSettingsPopup.setVisibility(View.INVISIBLE);
            } else {
                mBasicSettingPopups[mActiveIndicator].setVisibility(View.INVISIBLE);
            }
            mActiveIndicator = -1;
            return true;
        }
        return false;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
    }

    // Popup window is dismissed.
    public void onDismiss() {
        mActiveIndicator = -1;
    }

    public View getActivePopupWindow() {
        if (mActiveIndicator >= 0) {
            if (mActiveIndicator == mBasicSettingPopups.length) {
                return mOtherSettingsPopup;
            } else {
                return mBasicSettingPopups[mActiveIndicator];
            }
        } else {
            return null;
        }
    }

    // Scene mode may override other camera settings (ex: flash mode).
    public void overrideSettings(final String ... keyvalues) {
        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        if (mOtherSettingsPopup == null) {
            initializeOtherSettingPopup();
        }

        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            mIndicatorWheel.overrideSettings(key, value);
            mOtherSettingsPopup.overrideSettings(key, value);
        }
    }
}
