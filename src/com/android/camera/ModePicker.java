/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.camera.ui.RotateImageView;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * A widget that includes three mode selections {@code RotateImageView}'s and
 * a current mode indicator.
 */
public class ModePicker extends RelativeLayout implements View.OnClickListener {
    public static final int MODE_PANORAMA = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_CAMERA = 2;

    // Total mode number
    private static final int MODE_NUM = 3;

    /** A callback to be called when the user wants to switch activity. */
    public interface OnModeChangeListener {
        // Returns true if the listener agrees that the mode can be changed.
        public boolean onModeChanged(int newMode);
    }

    private final int DISABLED_COLOR;
    private final int CURRENT_MODE_BACKGROUND;

    private OnModeChangeListener mListener;
    private View mModeIcons[] = new View[MODE_NUM];
    private View mCurrentModeIcon;
    private View mModeSelection;
    private int mCurrentMode = MODE_CAMERA;
    private Context mContext;

    public ModePicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        DISABLED_COLOR = context.getResources().getColor(R.color.icon_disabled_color);
        CURRENT_MODE_BACKGROUND = R.drawable.btn_mode_background;
    }

    protected void onFinishInflate() {
        super.onFinishInflate();

        mModeSelection = findViewById(R.id.mode_selection);

        mCurrentModeIcon = findViewById(R.id.current_mode);
        mCurrentModeIcon.setOnClickListener(this);
        mCurrentModeIcon.setBackgroundResource(CURRENT_MODE_BACKGROUND);

        mModeIcons[MODE_PANORAMA] = findViewById(R.id.mode_panorama);
        mModeIcons[MODE_VIDEO] = findViewById(R.id.mode_video);
        mModeIcons[MODE_CAMERA] = findViewById(R.id.mode_camera);
    }

    private void enableModeSelection(boolean enabled) {
        mCurrentModeIcon.setVisibility(enabled ? View.GONE : View.VISIBLE);
        mCurrentModeIcon.setOnClickListener(enabled ? null : this);
        mModeSelection.setVisibility(enabled ? View.VISIBLE : View.GONE);
        for (int i = 0; i < MODE_NUM; ++i) {
            mModeIcons[i].setOnClickListener(enabled ? this : null);
            highlightView(mModeIcons[i], (i == mCurrentMode));
        }
    }

    public void onClick(View view) {
        for (int i = 0; i < MODE_NUM; ++i) {
            if (view == mModeIcons[i]) {
                setCurrentMode(i);
                enableModeSelection(false);
                return;
            }
        }
        if (view == mCurrentModeIcon) enableModeSelection(true);
    }

    private void setMode(int mode) {
        for (int i = 0; i < MODE_NUM; ++i) mModeIcons[i].setSelected(mode == i);
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        mListener = listener;
    }

    public void setCurrentMode(int mode) {
        setMode(mode);
        tryToSetMode(mode);
    }

    private void tryToSetMode(int mode) {
        if (mListener != null) {
            if (!mListener.onModeChanged(mode)) {
                setMode(mCurrentMode);
                return;
            }
        }
        ((RotateImageView) mCurrentModeIcon).setImageDrawable(
                ((RotateImageView) mModeIcons[mode]).getDrawable());
        mCurrentMode = mode;
    }

    public boolean onModeChanged(int mode) {
        setCurrentMode(mode);
        return true;
    }

    public void setDegree(int degree) {
        for (int i = 0; i < MODE_NUM; ++i) {
            ((RotateImageView) mModeIcons[i]).setDegree(degree);
        }
        ((RotateImageView) mCurrentModeIcon).setDegree(degree);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        mCurrentModeIcon.setEnabled(enabled);
        highlightView(mCurrentModeIcon, enabled);
        if (enabled) {
            mCurrentModeIcon.setBackgroundResource(CURRENT_MODE_BACKGROUND);
        } else {
            mCurrentModeIcon.setBackgroundColor(Color.BLACK);
        }

        mModeSelection.setEnabled(enabled);
        for (int i = 0; i < MODE_NUM; ++i) {
            mModeIcons[i].setEnabled(enabled);
        }
    }

    private void highlightView(View view, boolean enabled) {
        Drawable drawable = ((ImageView) view).getDrawable();
        if (enabled) {
            drawable.clearColorFilter();
        } else {
            drawable.setColorFilter(DISABLED_COLOR, PorterDuff.Mode.SRC_ATOP);
        }
    }
}
