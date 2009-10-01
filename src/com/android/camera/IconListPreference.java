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
import android.preference.ListPreference;
import android.util.AttributeSet;

public class IconListPreference extends ListPreference {
    private Drawable mIcons[];
    private Resources mResources;

    public IconListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.IconListPreference, 0, 0);
        mResources = context.getResources();
        setIcons(a.getResourceId(R.styleable.IconListPreference_icons, 0));
        a.recycle();
    }

    public Drawable[] getIcons() {
        return mIcons;
    }

    private void setIcons(int iconsRes) {
        TypedArray array = mResources.obtainTypedArray(iconsRes);
        int n = array.length();
        Drawable drawable[] = new Drawable[n];
        for (int i = 0; i < n; ++i) {
            int id = array.getResourceId(i, 0);
            drawable[i] = id == 0 ? null : mResources.getDrawable(id);
        }
        array.recycle();
        mIcons = drawable;
    }

    public void setIcons(Drawable[] icons) {
        mIcons = icons;
    }
}
