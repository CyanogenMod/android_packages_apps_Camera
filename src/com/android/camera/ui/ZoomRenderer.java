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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.ScaleGestureDetector;

import com.android.camera.R;

public class ZoomRenderer extends OverlayRenderer
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "CAM_Zoom";

    private int mMaxZoom;
    private int mZoom;
    private OnZoomChangedListener mListener;

    private ScaleGestureDetector mDetector;
    private Paint mPaint;
    private int mCircleSize;
    private int mCenterX;
    private int mCenterY;
    private float mMaxCircle;
    private float mMinCircle;
    private float mScale = 1f;
    private float mMinScale = 1f;
    private float mMaxScale = 3f;

    public interface OnZoomChangedListener {
        void onZoomStart();
        void onZoomEnd();
        void onZoomValueChanged(int index);  // only for immediate zoom
    }

    public ZoomRenderer(Context ctx) {
        Resources res = ctx.getResources();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.focus_outer_stroke));
        mDetector = new ScaleGestureDetector(ctx, this);
        mMinCircle = res.getDimensionPixelSize(R.dimen.zoom_ring_min);
        setVisible(false);
    }

    // set from module
    public void setZoomMax(int zoomMax) {
        mMaxZoom = zoomMax;
    }

    // set from module
    public void setZoomIndex(int index) {
        mScale = (mMaxScale - mMinScale) * index / mMaxZoom + mMinScale;
        mZoom = index;
    }

    public void setOnZoomChangeListener(OnZoomChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        mCenterX = (r - l) / 2;
        mCenterY = (b - t) / 2;
        mMaxCircle = Math.min(getWidth(), getHeight());
        mMaxCircle = (mMaxCircle - mMinCircle) / 2;
    }

    public boolean isScaling() {
        return mDetector.isInProgress();
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawCircle((float) mCenterX, (float) mCenterY,
                (float) mCircleSize, mPaint);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        mScale = mScale * detector.getScaleFactor();
        if (mScale < 1) mScale = 1;
        if (mScale > mMaxScale) mScale = mMaxScale;
        int newzoom = (int) ((mScale - mMinScale) * mMaxZoom / (mMaxScale - mMinScale));
        if (newzoom > mMaxZoom) newzoom = mMaxZoom;
        if (newzoom < 0) newzoom = 0;
        if (mListener != null && newzoom != mZoom) {
            mListener.onZoomValueChanged(newzoom);
            mZoom = newzoom;
        }
        mCircleSize = (int) (mMinCircle + (mScale - mMinScale) * (mMaxCircle - mMinCircle)
                / (mMaxScale - mMinScale));
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        setVisible(true);
        if (mListener != null) {
            mListener.onZoomStart();
        }
        update();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        setVisible(false);
        if (mListener != null) {
            mListener.onZoomEnd();
        }
    }

}
