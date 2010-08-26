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
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.ui.GLListView.OnItemSelectedListener;

class BasicIndicator extends AbstractIndicator {
    private static final int COLOR_OPTION_ITEM_HIGHLIGHT = 0xFF181818;

    private final ResourceTexture mIcon[];
    private final IconListPreference mPreference;
    protected int mIndex;
    private GLListView mPopupContent;
    private PreferenceAdapter mModel;
    private String mOverride;

    public BasicIndicator(Context context, IconListPreference preference) {
        super(context);
        mPreference = preference;
        mIcon = new ResourceTexture[preference.getLargeIconIds().length];
        mIndex = preference.findIndexOfValue(preference.getValue());
    }

    // Set the override and/or reload the value from preferences.
    private void updateContent(String override, boolean reloadValue) {
        if (!reloadValue && Util.equals(mOverride, override)) return;
        IconListPreference pref = mPreference;
        mOverride = override;
        int index = pref.findIndexOfValue(
                override == null ? pref.getValue() : override);
        if (mIndex != index) {
            mIndex = index;
            invalidate();
        }
    }

    @Override
    public void overrideSettings(String key, String settings) {
        IconListPreference pref = mPreference;
        if (!pref.getKey().equals(key)) return;
        updateContent(settings, false);
    }

    @Override
    public void reloadPreferences() {
        if (mModel != null) mModel.reload();
        updateContent(null, true);
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
            mModel = new PreferenceAdapter(context, mPreference);
            mPopupContent.setOnItemSelectedListener(new MyListener(mModel));
            mPopupContent.setDataModel(mModel);
        }
        mModel.overrideSettings(mOverride);
        return mPopupContent;
    }

    protected void onPreferenceChanged(int newIndex) {
        if (newIndex == mIndex) return;
        mIndex = newIndex;
        invalidate();
    }

    private class MyListener implements OnItemSelectedListener {

        private final PreferenceAdapter mAdapter;

        public MyListener(PreferenceAdapter adapter) {
            mAdapter = adapter;
        }

        public void onItemSelected(GLView view, int position) {
            mAdapter.onItemSelected(view, position);
            onPreferenceChanged(position - 1);
        }
    }

    @Override
    protected ResourceTexture getIcon() {
        int index = mIndex;
        if (mIcon[index] == null) {
            Context context = getGLRootView().getContext();
            mIcon[index] = new ResourceTexture(
                    context, mPreference.getLargeIconIds()[index]);
        }
        return mIcon[index];
    }
}
