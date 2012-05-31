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

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;

/**
 * The IndicatorControlBarContainer is a IndicatorControl containing
 * IndicatorControlBar, SecondIndicatorControlBar and ZoomControlBar for Phone UI.
 */
public class IndicatorControlBarContainer extends IndicatorControlContainer {
    private static final String TAG = "IndicatorControlBarContainer";

    private Animation mFadeIn, mFadeOut;
    private Animation mSecondLevelFadeIn, mSecondLevelFadeOut;
    private IndicatorControlBar mIndicatorControlBar;
    private SecondLevelIndicatorControlBar mSecondLevelIndicatorControlBar;

    public IndicatorControlBarContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFadeIn = AnimationUtils.loadAnimation(
                context, R.anim.first_level_fade_in);
        mFadeOut = AnimationUtils.loadAnimation(
                context, R.anim.first_level_fade_out);
        mFadeOut.setAnimationListener(mAnimationListener);
        mSecondLevelFadeIn = AnimationUtils.loadAnimation(
                context, R.anim.second_level_fade_in);
        mSecondLevelFadeOut = AnimationUtils.loadAnimation(
                context, R.anim.second_level_fade_out);
        mSecondLevelFadeOut.setAnimationListener(mAnimationListener);
    }

    @Override
    protected void onFinishInflate() {
        mIndicatorControlBar = (IndicatorControlBar)
                findViewById(R.id.indicator_bar);
        mIndicatorControlBar.setOnIndicatorEventListener(this);
        mSecondLevelIndicatorControlBar = (SecondLevelIndicatorControlBar)
                findViewById(R.id.second_level_indicator_bar);
        mSecondLevelIndicatorControlBar.setOnIndicatorEventListener(this);
    }

    @Override
    public void initialize(Context context, PreferenceGroup group,
            boolean isZoomSupported, String[] secondLevelKeys,
            String[] secondLevelOtherSettingKeys) {

        mIndicatorControlBar.initialize(context, group, isZoomSupported);

        mSecondLevelIndicatorControlBar.initialize(context, group,
                secondLevelKeys, secondLevelOtherSettingKeys);
    }

    public void setOrientation(int orientation) {
        mIndicatorControlBar.setOrientation(orientation);
        mSecondLevelIndicatorControlBar.setOrientation(orientation);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mIndicatorControlBar.getVisibility() == View.VISIBLE) {
            return mIndicatorControlBar.dispatchTouchEvent(event);
        } else if (mSecondLevelIndicatorControlBar.getVisibility() == View.VISIBLE) {
            return mSecondLevelIndicatorControlBar.dispatchTouchEvent(event);
        }
        return true;
    }

    private AnimationListener mAnimationListener = new AnimationListener() {
        public void onAnimationEnd(Animation animation) {
            if (animation == mSecondLevelFadeOut) {
                mSecondLevelIndicatorControlBar.setVisibility(View.GONE);
            } else if (animation == mFadeOut) {
                mIndicatorControlBar.setVisibility(View.GONE);
            }
        }

        public void onAnimationRepeat(Animation animation) {
        }

        public void onAnimationStart(Animation animation) {
        }
    };

    private void leaveSecondLevelIndicator() {
        mIndicatorControlBar.startAnimation(mFadeIn);
        mIndicatorControlBar.setVisibility(View.VISIBLE);
        mSecondLevelIndicatorControlBar.startAnimation(mSecondLevelFadeOut);
    }

    public void onIndicatorEvent(int event) {
        switch (event) {
            case OnIndicatorEventListener.EVENT_ENTER_SECOND_LEVEL_INDICATOR_BAR:
                mIndicatorControlBar.startAnimation(mFadeOut);
                mSecondLevelIndicatorControlBar.startAnimation(mSecondLevelFadeIn);
                mSecondLevelIndicatorControlBar.setVisibility(View.VISIBLE);
                break;

            case OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR:
                leaveSecondLevelIndicator();
                break;
        }
    }

    public void reloadPreferences() {
        mIndicatorControlBar.reloadPreferences();
        mSecondLevelIndicatorControlBar.reloadPreferences();
    }

    public void setListener(OnPreferenceChangedListener listener) {
        mIndicatorControlBar.setListener(listener);
        mSecondLevelIndicatorControlBar.setListener(listener);
    }

    @Override
    public View getActiveSettingPopup() {
        if (mIndicatorControlBar.getVisibility() == View.VISIBLE) {
            return mIndicatorControlBar.getActiveSettingPopup();
        } else if (mSecondLevelIndicatorControlBar.getVisibility() == View.VISIBLE) {
            return mSecondLevelIndicatorControlBar.getActiveSettingPopup();
        }
        return null;
    }

    public boolean dismissSettingPopup(boolean multiLevel) {
        if (mIndicatorControlBar.getVisibility() == View.VISIBLE) {
            return mIndicatorControlBar.dismissSettingPopup(multiLevel);
        } else if (mSecondLevelIndicatorControlBar.getVisibility() == View.VISIBLE) {
            return mSecondLevelIndicatorControlBar.dismissSettingPopup(multiLevel);
        }
        return false;
    }

    @Override
    public void overrideSettings(final String ... keyvalues) {
        mSecondLevelIndicatorControlBar.overrideSettings(keyvalues);
    }

    @Override
    public void setEnabled(boolean enabled) {
        mIndicatorControlBar.setEnabled(enabled);
        mSecondLevelIndicatorControlBar.setEnabled(enabled);
    }

    @Override
    public void enableZoom(boolean enabled) {
        mIndicatorControlBar.enableZoom(enabled);
    }

    @Override
    public void dismissSecondLevelIndicator() {
        if (mSecondLevelIndicatorControlBar.getVisibility() == View.VISIBLE) {
            leaveSecondLevelIndicator();
        }
    }
}
