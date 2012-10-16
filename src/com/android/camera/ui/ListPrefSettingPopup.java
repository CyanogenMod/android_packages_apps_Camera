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
import android.view.View;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A popup window that shows one camera setting. The title is the name of the
// setting (ex: white-balance). The entries are the supported values (ex:
// daylight, incandescent, etc). If initialized with an IconListPreference,
// the entries will contain both text and icons. Otherwise, entries will be
// shown in text.
public class ListPrefSettingPopup extends AbstractSettingPopup implements
        AdapterView.OnItemClickListener {
    private static final String TAG = "ListPrefSettingPopup";
    private ListPreference mPreference;
    private Listener mListener;

    static public interface Listener {
        public void onListPrefChanged(ListPreference pref);
    }

    public ListPrefSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private class ListPrefSettingAdapter extends SimpleAdapter {
        ListPrefSettingAdapter(Context context, List<? extends Map<String, ?>> data,
                int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
        }

        @Override
        public void setViewImage(ImageView v, String value) {
            if ("".equals(value)) {
                // Some settings have no icons. Ex: exposure compensation.
                v.setVisibility(View.GONE);
            } else {
                super.setViewImage(v, value);
            }
        }
    }

    public void initialize(ListPreference preference) {
        mPreference = preference;
        Context context = getContext();
        CharSequence[] entries = mPreference.getEntries();
        int[] iconIds = null;
        if (preference instanceof IconListPreference) {
            iconIds = ((IconListPreference) mPreference).getImageIds();
            if (iconIds == null) {
                iconIds = ((IconListPreference) mPreference).getLargeIconIds();
            }
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
        SimpleAdapter listItemAdapter = new ListPrefSettingAdapter(context, listItem,
                R.layout.setting_item,
                new String[] {"text", "image"},
                new int[] {R.id.text, R.id.image});
        ((ListView) mSettingList).setAdapter(listItemAdapter);
        ((ListView) mSettingList).setOnItemClickListener(this);
        reloadPreference();
    }

    // The value of the preference may have changed. Update the UI.
    @Override
    public void reloadPreference() {
        int index = mPreference.findIndexOfValue(mPreference.getValue());
        if (index != -1) {
            ((ListView) mSettingList).setItemChecked(index, true);
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
        if (mListener != null) mListener.onListPrefChanged(mPreference);
    }
}
