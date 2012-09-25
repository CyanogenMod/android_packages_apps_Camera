/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.graphics.Canvas;
import android.view.MotionEvent;

public abstract class OverlayRenderer implements RenderOverlay.Renderer {

    private static final String TAG = "CAM OverlayRenderer";
    protected RenderOverlay mOverlay;

    protected int mLeft, mTop, mRight, mBottom;

    protected boolean mVisible;

    public void setVisible(boolean vis) {
        mVisible = vis;
        update();
    }

    public boolean isVisible() {
        return mVisible;
    }

    // default does not handle touch
    @Override
    public boolean handlesTouch() {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        return false;
    }

    public abstract void onDraw(Canvas canvas);

    public void draw(Canvas canvas) {
        if (mVisible) {
            onDraw(canvas);
        }
    }

    @Override
    public void setOverlay(RenderOverlay overlay) {
        mOverlay = overlay;
    }

    @Override
    public void layout(int left, int top, int right, int bottom) {
        mLeft = left;
        mRight = right;
        mTop = top;
        mBottom = bottom;
    }

    protected Context getContext() {
        if (mOverlay != null) {
            return mOverlay.getContext();
        } else {
            return null;
        }
    }

    public int getWidth() {
        return mRight - mLeft;
    }

    public int getHeight() {
        return mBottom - mTop;
    }

    protected void update() {
        if (mOverlay != null) {
            mOverlay.update();
        }
    }

}
