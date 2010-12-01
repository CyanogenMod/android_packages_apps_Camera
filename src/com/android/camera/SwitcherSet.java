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

package com.android.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

/**
 * A widget that includes two {@code RadioButton}'s and a {@link Switcher}.
 */
public class SwitcherSet extends RadioGroup implements Switcher.OnSwitchListener,
        RadioGroup.OnCheckedChangeListener {
    private Switcher.OnSwitchListener mListener;
    private CompoundButton mOnView;
    private CompoundButton mOffView;
    private Switcher mSwitcher;

    public SwitcherSet(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        boolean onOff = checkedId == mOnView.getId();
        tryToSetSwitch(onOff);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        mSwitcher = (Switcher) findViewById(R.id.switcher);
        if (mSwitcher == null) {
            throw new NullPointerException("cannot find switcher in layout file");
        }
        mSwitcher.setOnSwitchListener(this);
        mSwitcher.addTouchView(this);
        setOnCheckedChangeListener(this);
        mOnView = (CompoundButton) findViewById(R.id.switch_on_button);
        mOffView = (CompoundButton) findViewById(R.id.switch_off_button);
    }

    public void setSwitch(boolean onOff) {
        if ((mOnView == null) && (mOffView == null)) {
            tryToSetSwitch(onOff);
        } else {
            // will trigger onCheckedChanged() and callback in tryToSetSwitch()
            CompoundButton button = onOff ? mOnView : mOffView;
            if (button != null) button.setChecked(true);
        }
    }

    public void setOnSwitchListener(Switcher.OnSwitchListener listener) {
        mListener = listener;
    }

    // Try to change the switch position. (The client can veto it.)
    private void tryToSetSwitch(boolean onOff) {
        mSwitcher.setSwitch(onOff);
        if (mListener != null) {
            if (!mListener.onSwitchChanged(mSwitcher, onOff)) {
                setSwitch(!onOff);
            }
        }
    }

    @Override
    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        setSwitch(onOff);
        return true;
    }
}
