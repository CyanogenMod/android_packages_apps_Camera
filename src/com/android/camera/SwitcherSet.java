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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * A widget that includes two {@code RotateImageView}'s and a {@link Switcher}.
 */
public class SwitcherSet extends LinearLayout implements Switcher.OnSwitchListener {
    private final int DISABLED_COLOR;

    private Switcher.OnSwitchListener mListener;
    private View mOnView;
    private View mOffView;
    private Switcher mSwitcher;
    private Context mContext;

    public SwitcherSet(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        DISABLED_COLOR = context.getResources().getColor(R.color.icon_disabled_color);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        mSwitcher = (Switcher) findViewById(R.id.switcher);
        if (mSwitcher == null) {
            throw new NullPointerException("cannot find switcher in layout file");
        }
        mSwitcher.setOnSwitchListener(this);
        mSwitcher.addTouchView(this);
        mOnView = findViewById(R.id.camera_switch_icon);
        mOffView = findViewById(R.id.video_switch_icon);
    }

    public void setSwitch(boolean onOff) {
        mOnView.setSelected(onOff);
        mOffView.setSelected(!onOff);
        mSwitcher.setSwitch(onOff);
    }

    public void setOnSwitchListener(Switcher.OnSwitchListener listener) {
        mListener = listener;
    }

    // Try to change the switch position. (The client can veto it.)
    private void tryToSetSwitch(boolean onOff) {
        if (mListener != null) {
            if (!mListener.onSwitchChanged(mSwitcher, onOff)) {
                setSwitch(!onOff);
            }
        }
    }

    @Override
    public boolean onSwitchChanged(Switcher source, boolean onOff) {
        setSwitch(onOff);
        tryToSetSwitch(onOff);
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        setEnabled(mSwitcher, enabled);
        setEnabled(mOnView, enabled);
        setEnabled(mOffView, enabled);
    }

    public void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);

        // render disabled effect for tablet only.
        if (mContext.getResources().getConfiguration().screenWidthDp < 1024) return;

        Drawable drawable = ((ImageView) view).getDrawable();
        if (enabled) {
            drawable.clearColorFilter();
        } else {
            drawable.setColorFilter(DISABLED_COLOR, PorterDuff.Mode.SRC_ATOP);
        }
    }
}
