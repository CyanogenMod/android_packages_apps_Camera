/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PieController {

    private static String TAG = "CAM_piecontrol";

    protected static final int MODE_PHOTO = 0;
    protected static final int MODE_VIDEO = 1;

    private static int sPickerResId;

    private CameraActivity mActivity;
    private PreferenceGroup mPreferenceGroup;
    private OnPreferenceChangedListener mListener;
    private PieRenderer mRenderer;
    private List<IconListPreference> mPreferences;
    private Map<IconListPreference, PieItem> mPreferenceMap;
    private Map<IconListPreference, String> mOverrides;
    private int mItemSize;

    public void setListener(OnPreferenceChangedListener listener) {
        mListener = listener;
    }

    public PieController(CameraActivity activity, PieRenderer pie) {
        mActivity = activity;
        mRenderer = pie;
        mPreferences = new ArrayList<IconListPreference>();
        mPreferenceMap = new HashMap<IconListPreference, PieItem>();
        mOverrides = new HashMap<IconListPreference, String>();
        mItemSize = activity.getResources().getDimensionPixelSize(R.dimen.pie_view_size);
    }

    public static void setCameraPickerResourceId(int id) {
        sPickerResId = id;
    }

    public void initialize(PreferenceGroup group,
            boolean isZoomSupported, String[] secondLevelKeys,
            String[] secondLevelOtherSettingKeys) {
        mRenderer.clearItems();
        setPreferenceGroup(group);
        addControls(secondLevelKeys, secondLevelOtherSettingKeys);
    }

    public void onSettingChanged() {
        if (mListener != null) {
            mListener.onSharedPreferenceChanged();
        }
    }

    protected void addControls(String[] keys, String[] otherSettingKeys) {
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                if (i == keys.length / 2) {
                    // sneak in camera picker in the middle
                    initializeCameraPicker();
                }
                IconListPreference pref =
                        (IconListPreference) mPreferenceGroup.findPreference(keys[i]);
                if (pref != null) {
                    addItem(pref);
                }
            }
        }
    }

    protected void initializeCameraPicker() {
        PieItem item = makeItem(sPickerResId);
        item.getView().setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // Find the index of next camera.
                ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
                int index = pref.findIndexOfValue(pref.getValue());
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                int newCameraId = Integer.parseInt((String) values[index]);
                mListener.onCameraPickerClicked(newCameraId);
            }
        });
        mRenderer.addItem(item);
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue("" + cameraId);
    }

    protected PieItem makeItem(int resId) {
        ImageView view = new ImageView(mActivity);
        view.setImageResource(resId);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return new PieItem(view, 0);
    }

    protected PieItem makeItem(CharSequence value) {
        TextView view = new TextView(mActivity);
        view.setText(value);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return new PieItem(view, 0);
    }

    public void addItem(final IconListPreference pref) {
        int[] iconIds = pref.getLargeIconIds();
        int resid = -1;
        if (iconIds != null) {
            // Each entry has a corresponding icon.
            int index = pref.findIndexOfValue(pref.getValue());
            resid = iconIds[index];
        } else {
            // The preference only has a single icon to represent it.
            resid = pref.getSingleIcon();
        }
        PieItem item = makeItem(resid);
        mRenderer.addItem(item);
        mPreferences.add(pref);
        mPreferenceMap.put(pref, item);
        int nOfEntries = pref.getEntries().length;
        if (nOfEntries > 1) {
            for (int i = 0; i < nOfEntries; i++) {
                PieItem inner = null;
                if (iconIds != null) {
                    inner = makeItem(iconIds[i]);
                } else {
                    inner = makeItem(pref.getEntries()[i]);
                }
                item.addItem(inner);
                final int index = i;
                inner.getView().setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pref.setValueIndex(index);
                        reloadPreference(pref);
                        onSettingChanged();
                    }
                });
            }
        }
    }

    public void setPreferenceGroup(PreferenceGroup group) {
        mPreferenceGroup = group;
        // Preset the current mode from the title of preference group.
        String title = group.getTitle();
        if (title.equals(mActivity.getString(
                R.string.pref_camcorder_settings_category))) {
//            mCurrentMode = MODE_VIDEO;
        }
    }

    public void reloadPreferences() {
        mPreferenceGroup.reloadValue();
        for (IconListPreference pref : mPreferenceMap.keySet()) {
            reloadPreference(pref);
        }
    }

    private void reloadPreference(IconListPreference pref) {
        PieItem item = mPreferenceMap.get(pref);
        String overrideValue = mOverrides.get(pref);
        int[] iconIds = pref.getLargeIconIds();
        if (iconIds != null) {
            // Each entry has a corresponding icon.
            int index;
            if (overrideValue == null) {
                index = pref.findIndexOfValue(pref.getValue());
            } else {
                index = pref.findIndexOfValue(overrideValue);
                if (index == -1) {
                    // Avoid the crash if camera driver has bugs.
                    Log.e(TAG, "Fail to find override value=" + overrideValue);
                    pref.print();
                    return;
                }
            }
            ((ImageView) item.getView()).setImageResource(iconIds[index]);
        } else {
            // The preference only has a single icon to represent it.
            ((ImageView) item.getView()).setImageResource(pref.getSingleIcon());
        }
    }

    // Scene mode may override other camera settings (ex: flash mode).
    public void overrideSettings(final String ... keyvalues) {
        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        for (IconListPreference pref : mPreferenceMap.keySet()) {
            override(pref, keyvalues);
        }
    }

    private void override(IconListPreference pref, final String ... keyvalues) {
        mOverrides.remove(pref);
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            if (key.equals(pref.getKey())) {
                mOverrides.put(pref, value);
                PieItem item = mPreferenceMap.get(pref);
                item.setEnabled(value == null);
                break;
            }
        }
        reloadPreference(pref);
    }


}
