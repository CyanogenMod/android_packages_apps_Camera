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

import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.Util;

import java.util.ArrayList;

class PreferenceAdapter
        implements GLListView.Model, GLListView.OnItemSelectedListener {

    private static final int ICON_NONE = 0;

    private final ArrayList<GLView> mContent = new ArrayList<GLView>();
    private final ListPreference mPreference;
    private String mOverride;

    public PreferenceAdapter(Context context, ListPreference preference) {
        mPreference = preference;
        generateContent(context, preference);
    }

    public void reload() {
        updateContent(null, true);
    }

    public void overrideSettings(String settings) {
        updateContent(settings, false);
    }

    private void updateContent(String settings, boolean reloadValues) {
        if (!reloadValues && Util.equals(settings, mOverride)) return;
        mOverride = settings;

        CharSequence[] values = mPreference.getEntryValues();
        String value = mPreference.getValue();
        if (settings == null) {
            for (int i = 1, n = mContent.size(); i < n; ++i) {
                GLOptionItem item = (GLOptionItem) mContent.get(i);
                item.setChecked(values[i - 1].equals(value));
                item.setEnabled(true);
            }
        } else {
            for (int i = 1, n = mContent.size(); i < n; ++i) {
                GLOptionItem item = (GLOptionItem) mContent.get(i);
                boolean checked = values[i - 1].equals(settings);
                item.setChecked(checked);
                item.setEnabled(checked);
            }
        }
    }

    private void generateContent(Context context, ListPreference preference) {
        GLOptionHeader header =
                new GLOptionHeader(context, preference.getTitle());
        mContent.add(header);
        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        String value = preference.getValue();
        int [] icons = null;
        if (preference instanceof IconListPreference) {
            IconListPreference iPref = (IconListPreference) preference;
            icons = iPref.getIconIds();
        }
        for (int i = 0, n = entries.length; i < n; ++i) {
            GLOptionItem item = new GLOptionItem(
                    context, icons == null ? ICON_NONE : icons[i],
                    entries[i].toString());
            item.setChecked(values[i].equals(value));
            mContent.add(item);
        }
    }

    public void onItemSelected(GLView view, int position) {
        if (mOverride != null) return;
        ListPreference pref = mPreference;
        CharSequence[] values = pref.getEntryValues();
        if (position < values.length + 1) {
            int index = position - 1;
            int oldIndex = pref.findIndexOfValue(pref.getValue());
            if (oldIndex != index) {
                synchronized (pref.getSharedPreferences()) {
                    pref.setValueIndex(index);
                }
                ((GLOptionItem) mContent.get(1 + oldIndex)).setChecked(false);
                ((GLOptionItem) view).setChecked(true);
            }
            return;
        }
    }

    public GLView getView(int index) {
        return mContent.get(index);
    }

    public boolean isSelectable(int index) {
        return mContent.get(index) instanceof GLOptionItem;
    }

    public int size() {
        return mContent.size();
    }
}
