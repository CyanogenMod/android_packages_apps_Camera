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
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;

public class ControlPanel extends RelativeLayout
        implements BasicSettingPicker.Listener, IndicatorWheel.Listener,
        View.OnClickListener {
    private static final String TAG = "ControlPanel";
    private Context mContext;
    private ComboPreferences mSharedPrefs;
    private PreferenceGroup mPreferenceGroup;
    private String[] mPreferenceKeys;
    private Listener mListener;
    private IndicatorWheel mIndicatorWheel;
    private BasicSettingPicker[] mSettingPickers;
    private int mActiveIndicator = -1;

    private ListView mThumbnailList;

    static public interface Listener {
        public void onSharedPreferencesChanged();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public ControlPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    protected void addIndicator(
            Context context, PreferenceGroup group, String key) {
        IconListPreference pref = (IconListPreference) group.findPreference(key);
        if (pref == null) return;
        IndicatorButton b = new IndicatorButton(context, pref);
        mIndicatorWheel.addView(b);
    }

    private void addOtherSettingIndicator(Context context) {
        Button b = new Button(context);
        b.setBackgroundResource(R.drawable.ic_viewfinder_settings);
        b.setClickable(false);
        mIndicatorWheel.addView(b);
    }

    public void initialize(Context context, PreferenceGroup group, String[] keys) {
        mPreferenceGroup = group;
        mPreferenceKeys = keys;
        // Add one more for other settings.
        mSettingPickers = new BasicSettingPicker[mPreferenceKeys.length + 1];
        mIndicatorWheel = (IndicatorWheel) findViewById(R.id.indicator_wheel);
        mThumbnailList = (ListView) findViewById(R.id.thumbnail_list);
        mSharedPrefs = ComboPreferences.get(context);
        for (int i = 0; i < mPreferenceKeys.length; i++) {
            addIndicator(context, group, mPreferenceKeys[i]);
        }
        addOtherSettingIndicator(context);
        requestLayout();
        mIndicatorWheel.setListener(this);
    }

    public void onSharedPreferenceChanged() {
        mIndicatorWheel.updateIndicator(mActiveIndicator);
        if (mListener != null) {
            mListener.onSharedPreferencesChanged();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.setting_exit:
                hideSettingPicker();
                break;
        }
    }

    public void onIndicatorClicked(int index) {
        if (mSettingPickers[index] == null) {
            initializeSettingPicker(index);
        }
        if (!showSettingPicker(index)) {
            hideSettingPicker();
        }
    }

    private void initializeSettingPicker(int index) {
        if (index >= mPreferenceKeys.length) return;
        IconListPreference pref = (IconListPreference)
                mPreferenceGroup.findPreference(mPreferenceKeys[index]);

        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.setting_picker, this);
        mSettingPickers[index] = (BasicSettingPicker) getChildAt(getChildCount() - 1);
        mSettingPickers[index].setSharedPreferenceChangedListener(this);
        mSettingPickers[index].initialize(pref);
        View v = mSettingPickers[index].findViewById(R.id.setting_exit);
        v.setOnClickListener(this);
    }

    private boolean showSettingPicker(int index) {
        if (mSettingPickers[index] == null) return false;

        mThumbnailList.setVisibility(View.INVISIBLE);
        for (int i = 0; i < mSettingPickers.length; i++) {
            if (i != index && mSettingPickers[i] != null) {
                mSettingPickers[i].setVisibility(View.INVISIBLE);
            }
        }
        mSettingPickers[index].setVisibility(View.VISIBLE);
        mActiveIndicator = index;
        return true;
    }

    public void hideSettingPicker() {
        if (mActiveIndicator >= 0) {
            mSettingPickers[mActiveIndicator].setVisibility(View.INVISIBLE);
            mThumbnailList.setVisibility(View.VISIBLE);
            mActiveIndicator = -1;
        }
    }
}
