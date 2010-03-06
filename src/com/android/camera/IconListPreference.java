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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.google.android.camera.R;

/** A {@code ListPreference} where each entry has a corresponding icon. */
public class IconListPreference extends ListPreference {

    private final int mIconIds[];
    private final int mLargeIconIds[];

    private Drawable mIcons[];
    private final Resources mResources;

    public IconListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.IconListPreference, 0, 0);
        mResources = context.getResources();
        mIconIds = getIconIds(a.getResourceId(
                R.styleable.IconListPreference_icons, 0));
        mLargeIconIds = getIconIds(a.getResourceId(
                R.styleable.IconListPreference_largeIcons, 0));
        a.recycle();
    }

    public Drawable[] getIcons() {
        if (mIcons == null) {
            int n = mIconIds.length;
            Drawable[] drawable = new Drawable[n];
            int[] id = mIconIds;
            for (int i = 0; i < n; ++i) {
                drawable[i] = id[i] == 0 ? null : mResources.getDrawable(id[i]);
            }
            mIcons = drawable;
        }
        return mIcons;
    }

    public int[] getLargeIconIds() {
        return mLargeIconIds;
    }

    public int[] getIconIds() {
        return mIconIds;
    }

    private int[] getIconIds(int iconsRes) {
        if (iconsRes == 0) return null;
        TypedArray array = mResources.obtainTypedArray(iconsRes);
        int n = array.length();
        int ids[] = new int[n];
        for (int i = 0; i < n; ++i) {
            ids[i] = array.getResourceId(i, 0);
        }
        array.recycle();
        return ids;
    }

    public void setIcons(Drawable[] icons) {
        mIcons = icons;
    }
}
