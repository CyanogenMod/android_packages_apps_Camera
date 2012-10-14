/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pie menu item
 */
public class PieItem {

    public static interface OnClickListener {
        void onClick(PieItem item);
    }

    private Drawable mDrawable;
    private int level;
    private float mCenter;
    private float start;
    private float sweep;
    private float animate;
    private int inner;
    private int outer;
    private boolean mSelected;
    private boolean mEnabled;
    private List<PieItem> mItems;
    private Path mPath;
    private OnClickListener mOnClickListener;
    private float mAlpha;

    // Gray out the view when disabled
    private static final float ENABLED_ALPHA = 1;
    private static final float DISABLED_ALPHA = (float) 0.3;
    private boolean mChangeAlphaWhenDisabled = true;

    public PieItem(Drawable drawable, int level) {
        mDrawable = drawable;
        this.level = level;
        setAlpha(1f);
        mEnabled = true;
        setAnimationAngle(getAnimationAngle());
        start = -1;
        mCenter = -1;
    }

    public boolean hasItems() {
        return mItems != null;
    }

    public List<PieItem> getItems() {
        return mItems;
    }

    public void addItem(PieItem item) {
        if (mItems == null) {
            mItems = new ArrayList<PieItem>();
        }
        mItems.add(item);
    }

    public void setPath(Path p) {
        mPath = p;
    }

    public Path getPath() {
        return mPath;
    }

    public void setChangeAlphaWhenDisabled (boolean enable) {
        mChangeAlphaWhenDisabled = enable;
    }

    public void setAlpha(float alpha) {
        mAlpha = alpha;
        mDrawable.setAlpha((int) (255 * alpha));
    }

    public void setAnimationAngle(float a) {
        animate = a;
    }

    public float getAnimationAngle() {
        return animate;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (mChangeAlphaWhenDisabled) {
            if (mEnabled) {
                setAlpha(ENABLED_ALPHA);
            } else {
                setAlpha(DISABLED_ALPHA);
            }
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setSelected(boolean s) {
        mSelected = s;
    }

    public boolean isSelected() {
        return mSelected;
    }

    public int getLevel() {
        return level;
    }

    public void setGeometry(float st, float sw, int inside, int outside) {
        start = st;
        sweep = sw;
        inner = inside;
        outer = outside;
    }

    public void setFixedSlice(float center, float sweep) {
        mCenter = center;
        this.sweep = sweep;
    }

    public float getCenter() {
        return mCenter;
    }

    public float getStart() {
        return start;
    }

    public float getStartAngle() {
        return start + animate;
    }

    public float getSweep() {
        return sweep;
    }

    public int getInnerRadius() {
        return inner;
    }

    public int getOuterRadius() {
        return outer;
    }

    public void setOnClickListener(OnClickListener listener) {
        mOnClickListener = listener;
    }

    public void performClick() {
        if (mOnClickListener != null) {
            mOnClickListener.onClick(this);
        }
    }

    public int getIntrinsicWidth() {
        return mDrawable.getIntrinsicWidth();
    }

    public int getIntrinsicHeight() {
        return mDrawable.getIntrinsicHeight();
    }

    public void setBounds(int left, int top, int right, int bottom) {
        mDrawable.setBounds(left, top, right, bottom);
    }

    public void draw(Canvas canvas) {
        mDrawable.draw(canvas);
    }

    public void setImageResource(Context context, int resId) {
        Drawable d = context.getResources().getDrawable(resId).mutate();
        d.setBounds(mDrawable.getBounds());
        mDrawable = d;
        setAlpha(mAlpha);
    }

}
