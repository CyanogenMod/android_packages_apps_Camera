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
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* A popup window that contains several camera settings. */
public class OtherSettingsPopup extends AbstractSettingPopup
        implements InLineSettingPicker.Listener,
        AdapterView.OnItemClickListener {
    private static final String TAG = "OtherSettingsPopup";
    private static final String[] OTHER_SETTING_KEYS = {
            CameraSettings.KEY_RECORD_LOCATION,
            CameraSettings.KEY_FOCUS_MODE,
            CameraSettings.KEY_EXPOSURE,
            CameraSettings.KEY_PICTURE_SIZE,
            CameraSettings.KEY_JPEG_QUALITY};
    private static final String ITEM_KEY = "key";
    private static final String ITEM_TITLE = "text";
    private static final String ITEM_VALUE = "value";
    private static final String ITEM_RESTORE = "reset";

    private Context mContext;
    private Listener mListener;
    private PreferenceGroup mPreferenceGroup;
    private ArrayList<HashMap<String, Object>> mListItem =
            new ArrayList<HashMap<String, Object>>();

    static public interface Listener {
        public void onOtherSettingChanged();
        public void onRestorePreferencesClicked();
    }

    private class OtherSettingsAdapter extends SimpleAdapter {

        OtherSettingsAdapter(Context context,
                List<? extends Map<String, ?>> data,
                int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView != null) return convertView;

            InLineSettingPicker view = (InLineSettingPicker)
                    super.getView(position, convertView, parent);
            TextView restoreSettings =
                    (TextView) view.findViewById(R.id.restore);
            View settingItem = view.findViewById(R.id.setting_item);

            // We apply the same View(InLineSettingPicker) as the listview's
            // components. To show the restore setting line, we control the
            // visibilities of components in InLineSettingPicker.
            boolean isRestoreItem = (position == mListItem.size() - 1);
            settingItem.setVisibility(
                    isRestoreItem ? View.GONE : View.VISIBLE);
            restoreSettings.setVisibility(
                    isRestoreItem ? View.VISIBLE : View.GONE);

            if (!isRestoreItem) {
                HashMap map = (HashMap) mListItem.get(position);
                ListPreference pref = (ListPreference) map.get(ITEM_KEY);
                view.initialize(pref);
                view.setSettingChangedListener(OtherSettingsPopup.this);
            }
            return view;
        }
    }

    public void setOtherSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    public OtherSettingsPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public void initialize(PreferenceGroup group) {
        mPreferenceGroup = group;
        // Prepare the setting items.
        for (int i = 0; i < OTHER_SETTING_KEYS.length; ++i) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            ListPreference pref = group.findPreference(OTHER_SETTING_KEYS[i]);
            if (pref != null) {
                map.put(ITEM_KEY, pref);
                map.put(ITEM_TITLE, pref.getTitle());
                map.put(ITEM_VALUE, pref.getEntry());
                mListItem.add(map);
            }
        }

        // Prepare the restore setting line.
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(ITEM_RESTORE, mContext.getString(R.string.pref_restore_detail));
        mListItem.add(map);

        SimpleAdapter mListItemAdapter = new OtherSettingsAdapter(mContext,
                mListItem,
                R.layout.in_line_setting_picker,
                new String[] {ITEM_TITLE, ITEM_VALUE, ITEM_RESTORE},
                new int[] {R.id.title, R.id.current_setting, R.id.restore});
        ((ListView) mSettingList).setAdapter(mListItemAdapter);
        ((ListView) mSettingList).setOnItemClickListener(this);
        ((ListView) mSettingList).setSelector(android.R.color.transparent);
    }

    public void onSettingChanged() {
        if (mListener != null) {
            mListener.onOtherSettingChanged();
        }
    }

    // Scene mode can override other camera settings (ex: flash mode).
    public void overrideSettings(String key, String value) {
        int count = mSettingList.getChildCount();
        for (int i = 0; i < count; i++) {
            ListPreference pref = (ListPreference) mListItem.get(i).get(ITEM_KEY);
            if (pref != null && key.equals(pref.getKey())) {
                InLineSettingPicker picker =
                        (InLineSettingPicker) mSettingList.getChildAt(i);
                picker.overrideSettings(value);
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

    public void reloadPreference() {
        int count = mSettingList.getChildCount();
        for (int i = 0; i < count; i++) {
            ListPreference pref = (ListPreference) mListItem.get(i).get(ITEM_KEY);
            if (pref != null) {
                InLineSettingPicker picker =
                        (InLineSettingPicker) mSettingList.getChildAt(i);
                picker.reloadPreference();
            }
        }
    }
}
