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

import com.android.camera.ListPreference;
import com.android.camera.R;

import java.util.HashMap;

class OtherSettingsIndicator extends AbstractIndicator {
    private static final int COLOR_OPTION_ITEM_HIGHLIGHT = 0xFF181818;

    private final ListPreference mPreference[];
    private final GLListView.Model mAdapters[];
    private ResourceTexture mIcon;
    private GLListView mPopupContent;
    private Runnable mOnRestorePrefsClickedRunner;
    private final HashMap<String, String> mOverrides = new HashMap<String, String>();

    public OtherSettingsIndicator(
            Context context, ListPreference preference[]) {
        super(context);
        mPreference = preference;
        // One extra for the restore settings
        mAdapters = new GLListView.Model[preference.length + 1];
    }

    @Override
    protected ResourceTexture getIcon() {
        if (mIcon == null) {
            Context context = getGLRootView().getContext();
            mIcon = new ResourceTexture(
                    context, R.drawable.ic_viewfinder_settings);
        }
        return mIcon;
    }

    @Override
    public void reloadPreferences() {
        if (mPopupContent != null) {
            ListPreference prefs[] = mPreference;
            for (int i = 0, n = prefs.length; i < n; ++i) {
                ((PreferenceAdapter) mAdapters[i]).reload();
            }
        }
    }

    @Override
    public void overrideSettings(String key, String value) {
        if (value == null) {
            mOverrides.remove(key);
        } else {
            mOverrides.put(key, value);
        }
        if (mPopupContent != null) {
            ListPreference prefs[] = mPreference;
            for (int i = 0, n = prefs.length; i < n; ++i) {
                if (!prefs[i].getKey().equals(key)) continue;
                ((PreferenceAdapter) mAdapters[i]).overrideSettings(value);
                break;
            }
        }
    }

    private UberAdapter buildUberAdapter() {
        ListPreference prefs[] = mPreference;
        GLListView.Model adapters[] = mAdapters;
        Context context = getGLRootView().getContext();
        for (int i = 0, n = prefs.length; i < n; ++i) {
            adapters[i] = new PreferenceAdapter(context, prefs[i]);
            String override = mOverrides.get(prefs[i].getKey());
            if (override != null) {
                ((PreferenceAdapter) adapters[i]).overrideSettings(override);
            }
        }
        adapters[prefs.length] = new RestoreSettingsModel(context);
        return new UberAdapter();
    }

    @Override
    public GLView getPopupContent() {
        if (mPopupContent == null) {
            Context context = getGLRootView().getContext();
            mPopupContent = new GLListView(context);
            mPopupContent.setHighLight(
                    new ColorTexture(COLOR_OPTION_ITEM_HIGHLIGHT));
            mPopupContent.setScroller(new NinePatchTexture(
                    context, R.drawable.scrollbar_handle_vertical));
            UberAdapter adapter = buildUberAdapter();
            mPopupContent.setOnItemSelectedListener(adapter);
            mPopupContent.setDataModel(adapter);
        }
        return mPopupContent;
    }

    private class UberAdapter implements
            GLListView.Model, GLListView.OnItemSelectedListener {

        public GLView getView(int index) {
            for (GLListView.Model adapter : mAdapters) {
                if (index < adapter.size()) {
                    return adapter.getView(index);
                }
                index -= adapter.size();
            }
            return null;
        }

        public boolean isSelectable(int index) {
            for (GLListView.Model adapter : mAdapters) {
                if (index < adapter.size()) {
                    return adapter.isSelectable(index);
                }
                index -= adapter.size();
            }
            return true;
        }

        public int size() {
            int size = 0;
            for (GLListView.Model adapter : mAdapters) {
                size += adapter.size();
            }
            return size;
        }

        public void onItemSelected(GLView view, int position) {
            for (GLListView.Model adapter : mAdapters) {
                if (position < adapter.size()) {
                    ((GLListView.OnItemSelectedListener)
                            adapter).onItemSelected(view, position);
                    return;
                }
                position -= adapter.size();
            }
        }
    }

    private class RestoreSettingsModel
            implements GLListView.Model, GLListView.OnItemSelectedListener {
        private final GLView mHeader;
        private final GLView mItem;

        public RestoreSettingsModel(Context context) {
            mHeader = new GLOptionHeader(context,
                    context.getString(R.string.pref_restore_title));
            mItem = new RestoreSettingsItem(
                    context, context.getString(R.string.pref_restore_detail));
        }

        public GLView getView(int index) {
            return index == 0 ? mHeader : mItem;
        }

        public boolean isSelectable(int index) {
            return index != 0;
        }

        public int size() {
            return 2;
        }

        public void onItemSelected(GLView view, int position) {
            if (mOnRestorePrefsClickedRunner != null) {
                mOnRestorePrefsClickedRunner.run();
            }
        }
    }

    public void setOnRestorePreferencesClickedRunner(Runnable l) {
        mOnRestorePrefsClickedRunner = l;
    }
}
