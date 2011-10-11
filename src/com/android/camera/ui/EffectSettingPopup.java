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

import com.android.camera.EffectsRecorder;
import com.android.camera.IconListPreference;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;

// A popup window that shows video effect setting. It has two grid view.
// One shows the goofy face effects. The other shows the background replacer
// effects.
public class EffectSettingPopup extends AbstractSettingPopup implements
        AdapterView.OnItemClickListener, View.OnClickListener {
    private final String TAG = "EffectSettingPopup";
    private IconListPreference mPreference;
    private Listener mListener;
    private View mClearEffects;
    private GridView mSillyFacesGrid;
    private GridView mBackgroundGrid;

    static public interface Listener {
        public void onSettingChanged();
    }

    public EffectSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mClearEffects = findViewById(R.id.clear_effects);
        mClearEffects.setOnClickListener(this);
        mSillyFacesGrid = (GridView) findViewById(R.id.effect_silly_faces);
        mBackgroundGrid = (GridView) findViewById(R.id.effect_background);
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

        // Prepare goofy face GridView.
        ArrayList<HashMap<String, Object>> sillyFacesItem =
                new ArrayList<HashMap<String, Object>>();
        // The first is clear effect. Skip it.
        for(int i = 1; i < EffectsRecorder.NUM_OF_GF_EFFECTS + 1; ++i) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("text", entries[i].toString());
            if (iconIds != null) map.put("image", iconIds[i]);
            sillyFacesItem.add(map);
        }
        SimpleAdapter sillyFacesItemAdapter = new SimpleAdapter(context,
                sillyFacesItem, R.layout.effect_setting_item,
                new String[] {"text", "image"},
                new int[] {R.id.text, R.id.image});
        mSillyFacesGrid.setAdapter(sillyFacesItemAdapter);
        mSillyFacesGrid.setOnItemClickListener(this);

        // Prepare background replacer GridView.
        ArrayList<HashMap<String, Object>> backgroundItem =
                new ArrayList<HashMap<String, Object>>();
        for(int i = EffectsRecorder.NUM_OF_GF_EFFECTS + 1; i < entries.length; ++i) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("text", entries[i].toString());
            if (iconIds != null) map.put("image", iconIds[i]);
            backgroundItem.add(map);
        }
        // Initialize background replacer if it is supported.
        if (backgroundItem.size() > 0) {
            findViewById(R.id.effect_background_separator).setVisibility(View.VISIBLE);
            findViewById(R.id.effect_background_title).setVisibility(View.VISIBLE);
            mBackgroundGrid.setVisibility(View.VISIBLE);
            SimpleAdapter backgroundItemAdapter = new SimpleAdapter(context,
                    backgroundItem, R.layout.effect_setting_item,
                    new String[] {"text", "image"},
                    new int[] {R.id.text, R.id.image});
            mBackgroundGrid.setAdapter(backgroundItemAdapter);
            mBackgroundGrid.setOnItemClickListener(this);
        }

        reloadPreference();
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            if (getVisibility() != View.VISIBLE) {
                // Do not show or hide "Clear effects" button when the popup
                // is already visible. Otherwise it looks strange.
                int index = mPreference.findIndexOfValue(mPreference.getValue());
                mClearEffects.setVisibility((index <= 0) ? View.GONE : View.VISIBLE);
            }
            reloadPreference();
        }
        super.setVisibility(visibility);
    }

    // The value of the preference may have changed. Update the UI.
    @Override
    public void reloadPreference() {
        int index = mPreference.findIndexOfValue(mPreference.getValue());
        if (index >= 0) {
            mBackgroundGrid.setItemChecked(mBackgroundGrid.getCheckedItemPosition(), false);
            mSillyFacesGrid.setItemChecked(mSillyFacesGrid.getCheckedItemPosition(), false);
            if (index >= 1 && index < EffectsRecorder.NUM_OF_GF_EFFECTS + 1) {
                mSillyFacesGrid.setItemChecked(index - 1, true);
            } else if (index >= EffectsRecorder.NUM_OF_GF_EFFECTS + 1) {
                mBackgroundGrid.setItemChecked(index - EffectsRecorder.NUM_OF_GF_EFFECTS - 1, true);
            }
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
        if (parent == mSillyFacesGrid) {
            // The first one is clear effect.
            mPreference.setValueIndex(index + 1);
        } else { // Background replace grid.
            mPreference.setValueIndex(index + EffectsRecorder.NUM_OF_GF_EFFECTS + 1);
        }
        reloadPreference();
        if (mListener != null) mListener.onSettingChanged();
    }

    @Override
    public void onClick(View v) {
        // Clear the effect.
        mPreference.setValueIndex(0);
        reloadPreference();
        if (mListener != null) mListener.onSettingChanged();
    }
}
