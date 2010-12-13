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
import android.view.MotionEvent;
import android.util.AttributeSet;

/**
 * A (horizontal) widget which switches between the {@code Camera} and the
 * {@code VideoCamera} activities.
 */
public class HorizontalSwitcher extends Switcher {
    public HorizontalSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int getAvailableLength() {
        return getWidth() - getPaddingLeft() - getPaddingRight()
                - getDrawable().getIntrinsicWidth();
    }

    @Override
    protected int trackTouch(MotionEvent event) {
        return (int) event.getX() - getPaddingLeft()
                - (getDrawable().getIntrinsicWidth() / 2);
    }

    @Override
    protected int getOffsetTopToDraw() {
        return (getHeight() - getDrawable().getIntrinsicHeight()) / 2;
    }

    @Override
    protected int getOffsetLeftToDraw() {
        return getPaddingLeft() + getLogicalPosition();
    }
}
