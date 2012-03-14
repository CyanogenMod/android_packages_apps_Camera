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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.camera.CameraSettings;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import java.util.ArrayList;

/* A popup window that contains several camera settings. */
public class OtherSettingsPopup extends AbstractSettingPopup
        implements InLineSettingItem.Listener,
        AdapterView.OnItemClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = "OtherSettingsPopup";

    private Listener mListener;
    private ArrayList<ListPreference> mListItem = new ArrayList<ListPreference>();

    static public interface Listener {
        public void onSettingChanged();
        public void onRestorePreferencesClicked();
    }

    private class OtherSettingsAdapter extends ArrayAdapter<ListPreference> {
        LayoutInflater mInflater;

        OtherSettingsAdapter() {
            super(OtherSettingsPopup.this.getContext(), 0, mListItem);
            mInflater = LayoutInflater.from(getContext());
        }

        private int getSettingLayoutId(ListPreference pref) {
            // If the preference is null, it will be the only item , i.e.
            // 'Restore setting' in the popup window.
            if (pref == null) return R.layout.in_line_setting_restore;

            // Currently, the RecordLocationPreference is the only setting
            // which applies the on/off switch.
            if (CameraSettings.KEY_RECORD_LOCATION.equals(pref.getKey())) {
                return R.layout.in_line_setting_switch;
            }
            return R.layout.in_line_setting_knob;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView != null) return convertView;

            ListPreference pref = mListItem.get(position);

            int viewLayoutId = getSettingLayoutId(pref);
            InLineSettingItem view = (InLineSettingItem)
                    mInflater.inflate(viewLayoutId, parent, false);
            if (viewLayoutId == R.layout.in_line_setting_restore) {
                view.setId(R.id.restore_default);
            }

            view.initialize(pref); // no init for restore one
            view.setSettingChangedListener(OtherSettingsPopup.this);
            return view;
        }
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public OtherSettingsPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(PreferenceGroup group, String[] keys) {
        // Prepare the setting items.
        for (int i = 0; i < keys.length; ++i) {
            ListPreference pref = group.findPreference(keys[i]);
            if (pref != null) mListItem.add(pref);
        }

        // Prepare the restore setting line.
        mListItem.add(null);

        ArrayAdapter<ListPreference> mListItemAdapter = new OtherSettingsAdapter();
        ((ListView) mSettingList).setAdapter(mListItemAdapter);
        ((ListView) mSettingList).setOnItemClickListener(this);
        ((ListView) mSettingList).setSelector(android.R.color.transparent);
    }

    @Override
    public void onSettingChanged() {
        if (mListener != null) {
            mListener.onSettingChanged();
        }
    }

    // Scene mode can override other camera settings (ex: flash mode).
    public void overrideSettings(final String ... keyvalues) {
        int count = mSettingList.getChildCount();
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            for (int j = 0; j < count; j++) {
                ListPreference pref = mListItem.get(j);
                if (pref != null && key.equals(pref.getKey())) {
                    InLineSettingItem settingItem =
                            (InLineSettingItem) mSettingList.getChildAt(j);
                    settingItem.overrideSettings(value);
                }
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        if ((position == mListItem.size() - 1) && (mListener != null)) {
            mListener.onRestorePreferencesClicked();
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
