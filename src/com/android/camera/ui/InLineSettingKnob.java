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

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.TextView;

import com.android.camera.ListPreference;
import com.android.camera.R;

/* A knob setting control. */
public class InLineSettingKnob extends InLineSettingItem {
    private static final String TAG = "InLineSettingKnob";
    private boolean mNext, mPrevious;
    private Button mPrevButton, mNextButton;
    private Handler mHandler;
    // The view that shows the current selected setting. Ex: 5MP
    private TextView mEntry;

    private final Runnable mRunnable = new Runnable() {
        @Override
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

    public InLineSettingKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHandler = new Handler();
    }

    OnTouchListener mNextTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mOverrideValue != null) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!mNext && changeIndex(mIndex - 1)) {
                    mNext = true;
                    // Give bigger delay so users can change only one step.
                    mHandler.postDelayed(mRunnable, 300);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                mNext = false;
            }
            return false;
        }
    };

    OnTouchListener mPreviousTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mOverrideValue != null) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!mPrevious && changeIndex(mIndex + 1)) {
                    mPrevious = true;
                    // Give bigger delay so users can change only one step.
                    mHandler.postDelayed(mRunnable, 300);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                mPrevious = false;
            }
            return false;
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNextButton = (Button) findViewById(R.id.increment);
        mNextButton.setOnTouchListener(mNextTouchListener);
        mPrevButton = (Button) findViewById(R.id.decrement);
        mPrevButton.setOnTouchListener(mPreviousTouchListener);
        mEntry = (TextView) findViewById(R.id.current_setting);
    }

    @Override
    public void initialize(ListPreference preference) {
        super.initialize(preference);
        // Add content descriptions for the increment and decrement buttons.
        mNextButton.setContentDescription(getResources().getString(
                R.string.accessibility_increment, mPreference.getTitle()));
        mPrevButton.setContentDescription(getResources().getString(
                R.string.accessibility_decrement, mPreference.getTitle()));
    }

    @Override
    protected void updateView() {
        if (mOverrideValue == null) {
            mEntry.setText(mPreference.getEntry());
            mNextButton.setVisibility(mIndex == 0 ? View.INVISIBLE : View.VISIBLE);
            mPrevButton.setVisibility(mIndex == mPreference.getEntryValues().length - 1
                    ? View.INVISIBLE : View.VISIBLE);
        } else {
            int index = mPreference.findIndexOfValue(mOverrideValue);
            if (index != -1) {
                mEntry.setText(mPreference.getEntries()[index]);
            } else {
                // Avoid the crash if camera driver has bugs.
                Log.e(TAG, "Fail to find override value=" + mOverrideValue);
                mPreference.print();
            }
            mNextButton.setVisibility(View.INVISIBLE);
            mPrevButton.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        event.getText().add(mPreference.getTitle() + mPreference.getEntry());
    }
}
