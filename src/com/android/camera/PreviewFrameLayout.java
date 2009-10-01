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

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;

public class PreviewFrameLayout extends ViewGroup {
    private static final int MIN_HORIZONTAL_MARGIN = 10; // 10dp

    public interface OnSizeChangedListener {
        public void onSizeChanged();
    }

    private double mAspectRatio = 4.0 / 3.0;
    private ImageView mGripper;
    private FrameLayout mFrame;
    private OnSizeChangedListener mSizeListener;
    private DisplayMetrics mMetrics = new DisplayMetrics();

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
        mGripper = (ImageView) findViewById(R.id.btn_gripper);
        mFrame = (FrameLayout) findViewById(R.id.frame);
        if (mFrame == null) {
            throw new IllegalStateException(
                    "must provide child with id as \"frame\"");
        }
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int gripperWidth = 0;
        int gripperHeight = 0;

        if (mGripper != null) {
            measureChild(mGripper, widthMeasureSpec, heightMeasureSpec);
            gripperWidth = mGripper.getMeasuredWidth();
            gripperHeight = mGripper.getMeasuredHeight();
        }

        int frameWidth = getMeasuredWidth() - (int) Math.max(
                gripperWidth, MIN_HORIZONTAL_MARGIN * mMetrics.density);
        int frameHeight = getMeasuredHeight();

        FrameLayout f = mFrame;

        int horizontalPadding = f.getPaddingLeft() + f.getPaddingRight();
        int verticalPadding = f.getPaddingBottom() + f.getPaddingTop();

        int previewWidth = frameWidth - horizontalPadding;
        int previewHeight = frameHeight - verticalPadding;

        // resize frame and preview for aspect ratio
        if (previewWidth > previewHeight * mAspectRatio) {
            previewWidth = (int) (previewHeight * mAspectRatio + .5);
        } else {
            previewHeight = (int) (previewWidth / mAspectRatio + .5);
        }
        frameWidth = previewWidth + horizontalPadding;
        frameHeight = previewHeight + verticalPadding;

        measureChild(mFrame,
                MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY, frameWidth),
                MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY, frameHeight));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Try to layout the "frame" in the center of the area, and put
        // "gripper" just to the left of it. If there is no enough space for
        // the gripper, the "frame" will be moved a little right so that
        // they won't overlap with each other.

        int frameWidth = mFrame.getMeasuredWidth();
        int frameHeight = mFrame.getMeasuredHeight();

        int leftSpace = ((r - l) - frameWidth) / 2;
        int topSpace = ((b - t) - frameHeight) / 2;

        int gripperWidth = 0;
        int gripperHeight = 0;
        if (mGripper != null) {
            gripperWidth = mGripper.getMeasuredWidth();
            gripperHeight = mGripper.getMeasuredHeight();
            myLayoutChild(mGripper,
                    Math.max(l, l + (leftSpace - gripperWidth)),
                    t + ((b - t) - gripperHeight) / 2,
                    gripperWidth, gripperHeight);
        }
        myLayoutChild(mFrame, Math.max(l + leftSpace, l + gripperWidth),
                t + topSpace, frameWidth, frameHeight);
        if (mSizeListener != null) {
            mSizeListener.onSizeChanged();
        }
    }

    private static void myLayoutChild(View child, int l, int t, int w, int h) {
        child.layout(l, t, l + w, t + h);
    }
}

