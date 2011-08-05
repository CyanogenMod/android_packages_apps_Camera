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

package com.android.camera.ui;

import com.android.camera.R;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

// This is an indicator button and pressing it opens a popup window. Ex: flash or other settings.
public abstract class AbstractIndicatorButton extends RotateImageView {
    private final String TAG = "AbstractIndicatorButton";
    protected Context mContext;
    protected Animation mFadeIn, mFadeOut;
    protected final int HIGHLIGHT_COLOR;
    protected final int DISABLED_COLOR;
    protected AbstractSettingPopup mPopup;

    public AbstractIndicatorButton(Context context) {
        super(context);
        mContext = context;
        mFadeIn = AnimationUtils.loadAnimation(mContext, R.anim.grow_fade_in_from_right);
        mFadeOut = AnimationUtils.loadAnimation(mContext, R.anim.shrink_fade_out_from_right);
        HIGHLIGHT_COLOR = mContext.getResources().getColor(R.color.review_control_pressed_color);
        DISABLED_COLOR = mContext.getResources().getColor(R.color.icon_disabled_color);
        setScaleType(ImageView.ScaleType.CENTER);
    }

    // Whether scene mode affects this indicator and it cannot be changed.
    public boolean isOverridden() {
        return false;
    }

    // Scene mode may override other settings like flash, white-balance, and focus.
    abstract public void overrideSettings(final String ... keyvalues);

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) return false;

        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN && !isOverridden()) {
            if (mPopup == null || mPopup.getVisibility() != View.VISIBLE) {
                showPopup();
            } else {
                dismissPopup();
            }
            return true;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            dismissPopup();
            return true;
        }
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Do not enable the button if it is overridden by scene mode.
        if (isOverridden()) {
            enabled = false;
        }

        // Don't do anything if state is not changed so not to interfere with
        // the "highlight" state.
        if (isEnabled() ^ enabled) {
            super.setEnabled(enabled);
            if (enabled) {
                clearColorFilter();
            } else {
                setColorFilter(DISABLED_COLOR);
            }
        }
    }

    @Override
    public void setDegree(int degree) {
        super.setDegree(degree);
        if (mPopup != null) {
            mPopup.setOrientation(degree);
        }
    }

    abstract protected void initializePopup();

    private void showPopup() {
        if (mPopup == null) initializePopup();

        mPopup.setVisibility(View.VISIBLE);
        mPopup.setOrientation(getDegree());
        mPopup.clearAnimation();
        mPopup.startAnimation(mFadeIn);
        setColorFilter(HIGHLIGHT_COLOR);
    }

    public boolean dismissPopup() {
        if (mPopup != null && mPopup.getVisibility() == View.VISIBLE) {
            mPopup.clearAnimation();
            mPopup.startAnimation(mFadeOut);
            mPopup.setVisibility(View.GONE);
            clearColorFilter();
            invalidate();
            return true;
        }
        return false;
    }

    public AbstractSettingPopup getPopupWindow() {
        if (mPopup != null && mPopup.getVisibility() == View.VISIBLE) {
            return mPopup;
        } else {
            return null;
        }
    }

    public void reloadPreferences() {
        if (mPopup != null) mPopup.reloadPreference();
    }
}
