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
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;

// A popup window that shows one camera setting. The title is the name of the
// setting (ex: white-balance). The entries are the supported values (ex:
// daylight, incandescent, etc).
public class BasicSettingPopup extends AbstractSettingPopup implements
        AdapterView.OnItemClickListener {
    private final String TAG = "BasicSettingPopup";
    private IconListPreference mPreference;
    private Listener mListener;

    static public interface Listener {
        public void onSettingChanged();
    }

    public BasicSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void initialize(IconListPreference preference) {
        mPreference = preference;
        Context context = getContext();
        CharSequence[] entries = mPreference.getEntries();
        int[] iconIds = mPreference.getImageIds();
        if (iconIds == null) {
            iconIds = mPreference.getLargeIconIds();
        }

        // Set title.
        mTitle.setText(mPreference.getTitle());

        // Prepare the ListView.
        ArrayList<HashMap<String, Object>> listItem =
                new ArrayList<HashMap<String, Object>>();
        for(int i = 0; i < entries.length; ++i) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("text", entries[i].toString());
            if (iconIds != null) map.put("image", iconIds[i]);
            listItem.add(map);
        }
        SimpleAdapter listItemAdapter = new SimpleAdapter(context, listItem,
                R.layout.setting_item,
                new String[] {"text", "image"},
                new int[] {R.id.text, R.id.image});
        ((AbsListView) mSettingList).setAdapter(listItemAdapter);
        ((AbsListView) mSettingList).setOnItemClickListener(this);
        reloadPreference();
    }

    // The value of the preference may have changed. Update the UI.
    @Override
    public void reloadPreference() {
        int index = mPreference.findIndexOfValue(mPreference.getValue());
        if (index != -1) {
            ((AbsListView) mSettingList).setItemChecked(index, true);
        } else {
            Log.e(TAG, "Invalid preference value.");
            mPreference.print();
        }
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view,
            int index, long id) {
        mPreference.setValueIndex(index);
        if (mListener != null) mListener.onSettingChanged();
    }
}
