/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.camera.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * A layout which handles the preview aspect ratio and the position of
 * the gripper.
 */
public class PreviewFrameLayout extends ViewGroup {
    private static final int MIN_HORIZONTAL_MARGIN = 10; // 10dp

    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged(Rect newRect);
    }

    private double mAspectRatio = 4.0 / 3.0;
    private FrameLayout mFrame;
    private FocusRectangle mFocus;
    private OnSizeChangedListener mSizeListener;
    private final DisplayMetrics mMetrics = new DisplayMetrics();
    private int actualWidth;
    private int actualHeight;

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ((Activity) context).getWindowManager()
                .getDefaultDisplay().getMetrics(mMetrics);
    }

    public void setOnSizeChangedListener(OnSizeChangedListener listener) {
        mSizeListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        mFrame = (FrameLayout) findViewById(R.id.frame);
        if (mFrame == null) {
            throw new IllegalStateException(
                    "must provide child with id as \"frame\"");
        }
        mFocus = (FocusRectangle) findViewById(R.id.focus_rectangle);
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int frameWidth = getWidth();
        int frameHeight = getHeight();

        FrameLayout f = mFrame;
        int horizontalPadding = f.getPaddingLeft() + f.getPaddingRight();
        int verticalPadding = f.getPaddingBottom() + f.getPaddingTop();
        int previewHeight = frameHeight - verticalPadding;
        int previewWidth = frameWidth - horizontalPadding;

        // resize frame and preview for aspect ratio
        if (previewWidth > previewHeight * mAspectRatio) {
            previewWidth = (int) (previewHeight * mAspectRatio + .5);
        } else {
            previewHeight = (int) (previewWidth / mAspectRatio + .5);
        }

        frameWidth = previewWidth + horizontalPadding;
        frameHeight = previewHeight + verticalPadding;

        actualWidth = frameWidth;
        actualHeight = frameHeight;

        int hSpace = ((r - l) - frameWidth);
        int vSpace = ((b - t) - frameHeight) / 2;
        Rect rect = new Rect(l, t + vSpace, r - hSpace, b - vSpace);

        mFrame.measure(
                MeasureSpec.makeMeasureSpec(frameWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
        mFrame.layout(rect.left, rect.top, rect.right, rect.bottom);

        if (mFocus != null) {
            mFocus.redraw();
        }

        if (mSizeListener != null) {
            mSizeListener.onSizeChanged(rect);
        }
    }

    public int getActualWidth() {
        return actualWidth;
    }

    public int getActualHeight() {
        return actualHeight;
    }

}

