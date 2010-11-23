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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.R;
import com.android.camera.ListPreference;

import java.util.Formatter;

/* A one-line camera setting that includes a title (ex: Picture size), a
   previous button, the current value (ex: 5MP), and a next button. Other
   setting popup window includes several InLineSettingPicker. */
public class InLineSettingPicker extends LinearLayout {
    private final String TAG = "InLineSettingPicker";
    // The view that shows the name of the setting. Ex: Picture size
    private TextView mTitle;
    // The view that shows the current selected setting. Ex: 5MP
    private TextView mEntry;
    private ListPreference mPreference;
    private boolean mNext, mPrevious;
    private int mIndex;
    private String mKey;
    private Listener mListener;

    static public interface Listener {
        public void onSettingChanged();
    }

    private Handler mHandler;
    private final Runnable mRunnable = new Runnable() {
        public void run() {
            if (mNext) {
                if (changeIndex(mIndex - 1)) {
                    mHandler.postDelayed(this, 100);
                }
            } else if (mPrevious) {
                if (changeIndex(mIndex + 1)) {
                    mHandler.postDelayed(this, 100);
                }
            }
        }
    };

    public InLineSettingPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.in_line_setting_picker, this, true);
        mHandler = new Handler();

        OnTouchListener nextTouchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!mNext && changeIndex(mIndex - 1)) {
                        mNext = true;
                        // Give bigger delay so users can change only one step.
                        mHandler.postDelayed(mRunnable, 300);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mNext = false;
                }
                return false;
            }
        };

        OnTouchListener previousTouchListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!mPrevious && changeIndex(mIndex + 1)) {
                        mPrevious = true;
                        // Give bigger delay so users can change only one step.
                        mHandler.postDelayed(mRunnable, 300);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mPrevious = false;
                }
                return false;
            }
        };

        Button nextButton = (Button) findViewById(R.id.increment);
        nextButton.setOnTouchListener(nextTouchListener);
        Button previousButton = (Button) findViewById(R.id.decrement);
        previousButton.setOnTouchListener(previousTouchListener);
        mEntry = (TextView) findViewById(R.id.current_setting);
        mTitle = (TextView) findViewById(R.id.title);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.InLineSettingPicker, 0, 0);
        mKey = a.getString(R.styleable.InLineSettingPicker_prefKey);
    }

    public String getKey() {
        return mKey;
    }

    public void initialize(ListPreference preference) {
        mPreference = preference;
        mIndex = mPreference.findIndexOfValue(mPreference.getValue());
        mTitle.setText(mPreference.getTitle());
        updateView();
    }

    private boolean changeIndex(int index) {
        if (index >= mPreference.getEntryValues().length || index < 0) return false;
        mIndex = index;
        mPreference.setValueIndex(mIndex);
        if (mListener != null) {
            mListener.onSettingChanged();
        }
        updateView();
        return true;
    }

    private void updateView() {
        mEntry.setText(mPreference.getEntry());
    }

    public void setSettingChangedListener(Listener listener) {
        mListener = listener;
    }
}
