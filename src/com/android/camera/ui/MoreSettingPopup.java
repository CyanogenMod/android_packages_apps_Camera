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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import java.util.ArrayList;

/* A popup window that contains several camera settings. */
public class MoreSettingPopup extends AbstractSettingPopup
        implements InLineSettingItem.Listener,
        AdapterView.OnItemClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = "MoreSettingPopup";

    private Listener mListener;
    private ArrayList<ListPreference> mListItem = new ArrayList<ListPreference>();

    // Keep track of which setting items are disabled
    // e.g. White balance will be disabled when scene mode is set to non-auto
    private boolean[] mEnabled;

    static public interface Listener {
        public void onSettingChanged(ListPreference pref);
        public void onPreferenceClicked(ListPreference pref);
    }

    private class MoreSettingAdapter extends ArrayAdapter<ListPreference> {
        LayoutInflater mInflater;
        String mOnString;
        String mOffString;
        MoreSettingAdapter() {
            super(MoreSettingPopup.this.getContext(), 0, mListItem);
            Context context = getContext();
            mInflater = LayoutInflater.from(context);
            mOnString = context.getString(R.string.setting_on);
            mOffString = context.getString(R.string.setting_off);
        }

        private int getSettingLayoutId(ListPreference pref) {

            if (isOnOffPreference(pref)) {
                return R.layout.in_line_setting_switch;
            }
            return R.layout.in_line_setting_menu;
        }

        private boolean isOnOffPreference(ListPreference pref) {
            CharSequence[] entries = pref.getEntries();
            if (entries.length != 2) return false;
            String str1 = entries[0].toString();
            String str2 = entries[1].toString();
            return ((str1.equals(mOnString) && str2.equals(mOffString)) ||
                    (str1.equals(mOffString) && str2.equals(mOnString)));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView != null) return convertView;

            ListPreference pref = mListItem.get(position);

            int viewLayoutId = getSettingLayoutId(pref);
            InLineSettingItem view = (InLineSettingItem)
                    mInflater.inflate(viewLayoutId, parent, false);

            view.initialize(pref); // no init for restore one
            view.setSettingChangedListener(MoreSettingPopup.this);
            if (position >= 0 && position < mEnabled.length) {
                view.setEnabled(mEnabled[position]);
            } else {
                Log.w(TAG, "Invalid input: enabled list length, " + mEnabled.length
                        + " position " + position);
            }
            return view;
        }

        @Override
        public boolean isEnabled(int position) {
            if (position >= 0 && position < mEnabled.length) {
                return mEnabled[position];
            }
            return true;
        }
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public MoreSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(PreferenceGroup group, String[] keys) {
        // Prepare the setting items.
        for (int i = 0; i < keys.length; ++i) {
            ListPreference pref = group.findPreference(keys[i]);
            if (pref != null) mListItem.add(pref);
        }

        ArrayAdapter<ListPreference> mListItemAdapter = new MoreSettingAdapter();
        ((ListView) mSettingList).setAdapter(mListItemAdapter);
        ((ListView) mSettingList).setOnItemClickListener(this);
        ((ListView) mSettingList).setSelector(android.R.color.transparent);
        // Initialize mEnabled
        mEnabled = new boolean[mListItem.size()];
        for (int i = 0; i < mEnabled.length; i++) {
            mEnabled[i] = true;
        }
    }

    // When preferences are disabled, we will display them grayed out. Users
    // will not be able to change the disabled preferences, but they can still see
    // the current value of the preferences
    public void setPreferenceEnabled(String key, boolean enable) {
        int count = mEnabled == null ? 0 : mEnabled.length;
        for (int j = 0; j < count; j++) {
            ListPreference pref = mListItem.get(j);
            if (pref != null && key.equals(pref.getKey())) {
                mEnabled[j] = enable;
                break;
            }
        }
    }

    public void onSettingChanged(ListPreference pref) {
        if (mListener != null) {
            mListener.onSettingChanged(pref);
        }
    }

    // Scene mode can override other camera settings (ex: flash mode).
    public void overrideSettings(final String ... keyvalues) {
        int count = mEnabled == null ? 0 : mEnabled.length;
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            for (int j = 0; j < count; j++) {
                ListPreference pref = mListItem.get(j);
                if (pref != null && key.equals(pref.getKey())) {
                    // Change preference
                    if (value != null) pref.setValue(value);
                    // If the preference is overridden, disable the preference
                    boolean enable = value == null;
                    mEnabled[j] = enable;
                    if (mSettingList.getChildCount() > j) {
                        mSettingList.getChildAt(j).setEnabled(enable);
                    }
                }
            }
        }
        reloadPreference();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        if (mListener != null) {
            ListPreference pref = mListItem.get(position);
            mListener.onPreferenceClicked(pref);
        }
    }

    @Override
    public void reloadPreference() {
        int count = mSettingList.getChildCount();
        for (int i = 0; i < count; i++) {
            ListPreference pref = mListItem.get(i);
            if (pref != null) {
                InLineSettingItem settingItem =
                        (InLineSettingItem) mSettingList.getChildAt(i);
                settingItem.reloadPreference();
            }
        }
    }
}
