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

import android.graphics.Path;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Pie menu item
 */
public class PieItem {

    private View mView;
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

    // Gray out the view when disabled
    private static final float ENABLED_ALPHA = 1;
    private static final float DISABLED_ALPHA = (float) 0.3;
    private boolean mChangeAlphaWhenDisabled = true;

    public PieItem(View view, int level) {
        mView = view;
        this.level = level;
        mEnabled = true;
        setAnimationAngle(getAnimationAngle());
        setAlpha(getAlpha());
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
        if (mView != null) {
            if (mView instanceof ImageView) {
                // Here use deprecated ImageView.setAlpha(int alpha)
                // for backward compatibility
                ((ImageView) mView).setAlpha((int) (255.0 * alpha));
            } else {
                mView.setAlpha(alpha);
            }
        }
    }

    public float getAlpha() {
        if (mView != null) {
            return mView.getAlpha();
        }
        return 1;
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
        if (mView != null) {
            mView.setSelected(s);
        }
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

    public View getView() {
        return mView;
    }

}
