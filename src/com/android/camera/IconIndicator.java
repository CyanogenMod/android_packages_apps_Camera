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
import android.widget.ImageView;

import com.android.camera.R;

/**
 * This class draws an icon which changes according to the mode. For example,
 * The flash icon can have on, off, and auto modes. The user can use
 * {@link #setMode(String)} to change the mode (and the icon).
 */
public class IconIndicator extends ImageView {

    private Drawable[] mIcons;
    private CharSequence[] mModes;

    public IconIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.IconIndicator, defStyle, 0);
        Drawable icons[] = loadIcons(context.getResources(),
                a.getResourceId(R.styleable.IconIndicator_icons, 0));
        CharSequence modes[] =
                a.getTextArray(R.styleable.IconIndicator_modes);
        a.recycle();

        setModesAndIcons(modes, icons);
        setImageDrawable(mIcons.length > 0 ? mIcons[0] : null);
    }

    public IconIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    private Drawable[] loadIcons(Resources resources, int iconsId) {
        TypedArray array = resources.obtainTypedArray(iconsId);
        int n = array.length();
        Drawable drawable[] = new Drawable[n];
        for (int i = 0; i < n; ++i) {
            int id = array.getResourceId(i, 0);
            drawable[i] = id == 0 ? null : resources.getDrawable(id);
        }
        array.recycle();
        return drawable;
    }

    private void setModesAndIcons(CharSequence[] modes, Drawable icons[]) {
        if (modes.length != icons.length || icons.length == 0) {
            throw new IllegalArgumentException();
        }
        mIcons = icons;
        mModes = modes;
    }

    public void setMode(String mode) {
        for (int i = 0, n = mModes.length; i < n; ++i) {
            if (mModes[i].equals(mode)) {
                setImageDrawable(mIcons[i]);
                return;
            }
        }
        throw new IllegalArgumentException("unknown mode: " + mode);
    }
}
