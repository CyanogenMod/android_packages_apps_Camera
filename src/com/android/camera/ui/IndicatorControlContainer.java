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
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

/**
 * A view contains indicator control bar, second-level indicator bar and
 * zoom control.
 */
public class IndicatorControlContainer extends IndicatorControl implements
        OnIndicatorEventListener {
    private static final String TAG = "IndicatorControlContainer";

    private Animation mFadeIn, mFadeOut;
    private IndicatorBar mIndicatorBar;
    private ZoomControlBar mZoomControlBar;
    private SecondLevelIndicatorBar mSecondLevelIndicatorBar;

    public IndicatorControlContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void initialize(Context context, PreferenceGroup group,
            String flashSetting, String[] secondLevelKeys,
            String[] secondLevelOtherSettingKeys) {
        mIndicatorBar = (IndicatorBar)
                findViewById(R.id.indicator_bar);

        mZoomControlBar = (ZoomControlBar)
                findViewById(R.id.zoom_control);
        mZoomControlBar.setOnIndicatorEventListener(this);

        // We need to show/hide the zoom slider icon accordingly.
        // From UI spec, we have camera_flash setting on the first level.
        mIndicatorBar.initialize(context, group, flashSetting,
                mZoomControlBar.isZoomSupported());
        mIndicatorBar.setOnIndicatorEventListener(this);

        mSecondLevelIndicatorBar = (SecondLevelIndicatorBar)
                findViewById(R.id.second_level_indicator_bar);
        mSecondLevelIndicatorBar.initialize(context, group, secondLevelKeys,
                secondLevelOtherSettingKeys);
        mSecondLevelIndicatorBar.setOnIndicatorEventListener(this);

        mFadeIn = AnimationUtils.loadAnimation(
                context, R.anim.grow_fade_in_from_bottom);
        mFadeOut = AnimationUtils.loadAnimation(
                context, R.anim.shrink_fade_out_from_top);
    }

    public void setDegree(int degree) {
        mIndicatorBar.setDegree(degree);
        mSecondLevelIndicatorBar.setDegree(degree);
        mZoomControlBar.setDegree(degree);
    }

    public void onIndicatorEvent(int event) {
        switch (event) {
            case OnIndicatorEventListener.EVENT_ENTER_SECOND_LEVEL_INDICATOR_BAR:
                mIndicatorBar.setVisibility(View.GONE);
                mSecondLevelIndicatorBar.startAnimation(mFadeIn);
                mSecondLevelIndicatorBar.setVisibility(View.VISIBLE);
                break;

            case OnIndicatorEventListener.EVENT_LEAVE_SECOND_LEVEL_INDICATOR_BAR:
                mSecondLevelIndicatorBar.startAnimation(mFadeOut);
                mSecondLevelIndicatorBar.setVisibility(View.GONE);
                mIndicatorBar.setVisibility(View.VISIBLE);
                break;

            case OnIndicatorEventListener.EVENT_ENTER_ZOOM_CONTROL_BAR:
                mIndicatorBar.setVisibility(View.GONE);
                mZoomControlBar.setVisibility(View.VISIBLE);
                break;

            case OnIndicatorEventListener.EVENT_LEAVE_ZOOM_CONTROL_BAR:
                mZoomControlBar.setVisibility(View.GONE);
                mIndicatorBar.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void reloadPreferences() {
        mIndicatorBar.reloadPreferences();
        mSecondLevelIndicatorBar.reloadPreferences();
    }

    public void setListener(OnPreferenceChangedListener listener) {
        mIndicatorBar.setListener(listener);
        mSecondLevelIndicatorBar.setListener(listener);
    }

    @Override
    public View getActiveSettingPopup() {
        if (mIndicatorBar.getVisibility() == View.VISIBLE) {
            return mIndicatorBar.getActiveSettingPopup();
        } else if (mSecondLevelIndicatorBar.getVisibility() == View.VISIBLE) {
            return mSecondLevelIndicatorBar.getActiveSettingPopup();
        }
        return null;
    }

    public boolean dismissSettingPopup() {
        if (mIndicatorBar.getVisibility() == View.VISIBLE) {
            return mIndicatorBar.dismissSettingPopup();
        } else if (mSecondLevelIndicatorBar.getVisibility() == View.VISIBLE) {
            return mSecondLevelIndicatorBar.dismissSettingPopup();
        }
        return false;
    }
}
