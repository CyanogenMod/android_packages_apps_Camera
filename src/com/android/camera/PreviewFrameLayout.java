/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2013, Linux Foundation. All rights reserved.
 *
 * Not a Contribution. Apache license notifications and license are
 * retained for attribution purposes only
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
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewStub;
import android.widget.RelativeLayout;

import com.android.camera.ui.LayoutChangeHelper;
import com.android.camera.ui.LayoutChangeNotifier;
import com.android.gallery3d.common.ApiHelper;

/**
 * A layout which handles the preview aspect ratio.
 */
public class PreviewFrameLayout extends RelativeLayout implements LayoutChangeNotifier {

    private static final String TAG = "CAM_preview";

    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged(int width, int height);
    }

    private double mAspectRatio;
    private View mBorder;
    private OnSizeChangedListener mListener;
    private LayoutChangeHelper mLayoutChangeHelper;
    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAspectRatio(4.0 / 3.0);
        mLayoutChangeHelper = new LayoutChangeHelper(this);
        mOrientationResize = false;
        mPrevOrientationResize = false;
    }

    @Override
    protected void onFinishInflate() {
        mBorder = findViewById(R.id.preview_border);
        if (ApiHelper.HAS_FACE_DETECTION) {
            ViewStub faceViewStub = (ViewStub) findViewById(R.id.face_view_stub);
            /* preview_frame_video.xml does not have face view stub, so we need to
             * check that.
             */
            if (faceViewStub != null) {
                faceViewStub.inflate();
            }
        }
    }

    public void cameraOrientationPreviewResize(boolean orientation){
         mPrevOrientationResize = mOrientationResize;
         mOrientationResize = orientation;
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();

        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
        if(mOrientationResize != mPrevOrientationResize) {
            requestLayout();
        }
    }

    public void showBorder(boolean enabled) {
        mBorder.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    public void fadeOutBorder() {
        Util.fadeOut(mBorder);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);
        int originalWidth = previewWidth;
        int originalHeight = previewHeight;

        // Get the padding of the border background.
        int hPadding = getPaddingLeft() + getPaddingRight();
        int vPadding = getPaddingTop() + getPaddingBottom();

        // Resize the preview frame with correct aspect ratio.
        previewWidth -= hPadding;
        previewHeight -= vPadding;

        if (mOrientationResize) {
            previewHeight = (int) (previewWidth * mAspectRatio);

            if (previewHeight > originalHeight) {
                previewWidth = (int)(((double)originalHeight / (double)previewHeight) * previewWidth);
                previewHeight = originalHeight;
            }
        }

        // Add the padding of the border.
        previewWidth += hPadding;
        previewHeight += vPadding;

        // Ask children to follow the new preview dimension.
        super.onMeasure(MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY));
    }

    public void setOnSizeChangedListener(OnSizeChangedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mListener != null) mListener.onSizeChanged(w, h);
    }

    @Override
    public void setOnLayoutChangeListener(
            LayoutChangeNotifier.Listener listener) {
        mLayoutChangeHelper.setOnLayoutChangeListener(listener);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mLayoutChangeHelper.onLayout(changed, l, t, r, b);
    }
}
