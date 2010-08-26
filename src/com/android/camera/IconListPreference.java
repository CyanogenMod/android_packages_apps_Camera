/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.camera.R;

import java.util.List;

/** A {@code ListPreference} where each entry has a corresponding icon. */
public class IconListPreference extends ListPreference {

    private int mIconIds[];
    private int mLargeIconIds[];

    public IconListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.IconListPreference, 0, 0);
        Resources res = context.getResources();
        mIconIds = getIconIds(res, a.getResourceId(
                R.styleable.IconListPreference_icons, 0));
        mLargeIconIds = getIconIds(res, a.getResourceId(
                R.styleable.IconListPreference_largeIcons, 0));
        a.recycle();
    }

    public int[] getLargeIconIds() {
        return mLargeIconIds;
    }

    public int[] getIconIds() {
        return mIconIds;
    }

    public void setLargeIconIds(int[] largeIconIds) {
        mLargeIconIds = largeIconIds;
    }

    public void setIconIds(int[] iconIds) {
        mIconIds = iconIds;
    }

    private int[] getIconIds(Resources res, int iconsRes) {
        if (iconsRes == 0) return null;
        TypedArray array = res.obtainTypedArray(iconsRes);
        int n = array.length();
        int ids[] = new int[n];
        for (int i = 0; i < n; ++i) {
            ids[i] = array.getResourceId(i, 0);
        }
        array.recycle();
        return ids;
    }

    @Override
    public void filterUnsupported(List<String> supported) {
        CharSequence entryValues[] = getEntryValues();
        IntArray iconIds = new IntArray();
        IntArray largeIconIds = new IntArray();

        for (int i = 0, len = entryValues.length; i < len; i++) {
            if (supported.indexOf(entryValues[i].toString()) >= 0) {
                iconIds.add(mIconIds[i]);
                largeIconIds.add(mLargeIconIds[i]);
            }
        }
        int size = iconIds.size();
        mIconIds = iconIds.toArray(new int[size]);
        mLargeIconIds = iconIds.toArray(new int[size]);
        super.filterUnsupported(supported);
    }
}
