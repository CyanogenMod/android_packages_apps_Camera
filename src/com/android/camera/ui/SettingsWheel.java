/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.camera.CameraSettings;
import com.android.camera.ComboPreferences;
import com.android.camera.IconListPreference;
import com.android.camera.PreferenceGroup;
import com.android.camera.R;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;

import java.lang.Math;

/**
 * A view that contains camera settings and shutter buttons. The settings are
 * spreaded around the shutter button.
 */
public class SettingsWheel extends ViewGroup {
    private static final String TAG = "SettingsWheel";
    private ComboPreferences mSharedPrefs;
    private PreferenceGroup mPreferenceGroup;
    private Listener mListener;

    static public interface Listener {
        public void onSettingClicked();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mListener != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mListener.onSettingClicked();
                return true;
            }
        }
        return false;
    }

    public SettingsWheel(Context context) {
        super(context);
    }

    public SettingsWheel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SettingsWheel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Measure all children.
        int freeSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(freeSpec, freeSpec);
        }

        // Measure myself.
        View shutterButton = getChildAt(0);
        int desiredWidth = (int)(shutterButton.getMeasuredWidth() * 2.5);
        int desiredHeight = (int)(shutterButton.getMeasuredHeight() * 3);
        int widthMode = MeasureSpec.getMode(widthSpec);
        int heightMode = MeasureSpec.getMode(heightSpec);
        int measuredWidth, measuredHeight;
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            measuredWidth = desiredWidth;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            measuredWidth = Math.min(desiredWidth, MeasureSpec.getSize(widthSpec));
        } else {  // MeasureSpec.EXACTLY
            measuredWidth = MeasureSpec.getSize(widthSpec);
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            measuredHeight = desiredHeight;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            measuredHeight = Math.min(desiredHeight, MeasureSpec.getSize(heightSpec));
        } else {  // MeasureSpec.EXACTLY
            measuredHeight = MeasureSpec.getSize(heightSpec);
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) return;

        // Layout the shutter button.
        View shutterButton = findViewById(R.id.shutter_button);
        int width = shutterButton.getMeasuredWidth();
        int height = shutterButton.getMeasuredHeight();
        int xCenter = (right - left) - width / 2;
        int yCenter = (bottom - top) / 2;
        shutterButton.layout(xCenter - width / 2, yCenter - height / 2,
                xCenter + width / 2, yCenter + height / 2);

        // Layout the settings. The icons are spreaded on the left side of the
        // shutter button. So the angle starts from 90 to 270 degrees.
        if (count == 1) return;
        double radius = shutterButton.getMeasuredWidth();
        double intervalDegrees = 180.0 / (count - 2);
        double initialDegrees = 90.0;
        int index = 0;
        for (int i = 0; i < count; ++i) {
            View view = getChildAt(i);
            if (view == shutterButton) continue;
            double degree = initialDegrees + intervalDegrees * index;
            double radian = degree * Math.PI / 180.0;
            int x = xCenter + (int)(radius * Math.cos(radian));
            int y = yCenter - (int)(radius * Math.sin(radian));
            width = view.getMeasuredWidth();
            height = view.getMeasuredHeight();
            view.layout(x - width / 2, y - height / 2, x + width / 2,
                    y + height / 2);
            index++;
        }
    }

    protected void addIndicator(
            Context context, PreferenceGroup group, String key) {
        IconListPreference pref = (IconListPreference) group.findPreference(key);
        if (pref == null) return;
        int index = pref.findIndexOfValue(pref.getValue());
        Button b = new Button(context);
        b.setBackgroundResource(pref.getLargeIconIds()[index]);
        b.setClickable(false);
        addView(b);
    }

    public void initialize(Context context, PreferenceGroup group) {
        mPreferenceGroup = group;
        mSharedPrefs = ComboPreferences.get(context);
        addIndicator(context, group, CameraSettings.KEY_FLASH_MODE);
        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_RECORD_LOCATION);
        addIndicator(context, group, CameraSettings.KEY_CAMERA_ID);
        requestLayout();
    }
}
