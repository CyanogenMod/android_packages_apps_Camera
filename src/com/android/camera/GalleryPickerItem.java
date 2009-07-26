/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

class GalleryPickerItem extends ImageView {
    private Drawable mFrame;
    private Rect mFrameBounds = new Rect();
    private Drawable mOverlay;

    public GalleryPickerItem(Context context) {
        this(context, null);
    }

    public GalleryPickerItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GalleryPickerItem(Context context,
                             AttributeSet attrs,
                             int defStyle) {
        super(context, attrs, defStyle);

        mFrame = getResources().getDrawable(R.drawable.frame_gallery_preview);
        mFrame.setCallback(this);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (who == mFrame)
                || (who == mOverlay);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mFrame != null) {
            int[] drawableState = getDrawableState();
            mFrame.setState(drawableState);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final Rect frameBounds = mFrameBounds;
        if (frameBounds.isEmpty()) {
            final int w = getWidth();
            final int h = getHeight();

            frameBounds.set(0, 0, w, h);
            mFrame.setBounds(frameBounds);
            if (mOverlay != null) {
                mOverlay.setBounds(w - mOverlay.getIntrinsicWidth(),
                        h - mOverlay.getIntrinsicHeight(), w, h);
            }
        }

        mFrame.draw(canvas);
        if (mOverlay != null) {
            mOverlay.draw(canvas);
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mFrameBounds.setEmpty();
    }

    public void setOverlay(int overlayId) {
        if (overlayId >= 0) {
            mOverlay = getResources().getDrawable(overlayId);
            mFrameBounds.setEmpty();
        } else {
            mOverlay = null;
        }
    }
}
