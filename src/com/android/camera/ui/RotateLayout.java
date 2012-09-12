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

package com.android.camera.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.MotionEventHelper;

// A RotateLayout is designed to display a single item and provides the
// capabilities to rotate the item.
public class RotateLayout extends ViewGroup implements Rotatable {
    @SuppressWarnings("unused")
    private static final String TAG = "RotateLayout";
    private int mOrientation;
    private Matrix mMatrix = new Matrix();
    protected View mChild;

    public RotateLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The transparent background here is a workaround of the render issue
        // happened when the view is rotated as the device's orientation
        // changed. The view looks fine in landscape. After rotation, the view
        // is invisible.
        setBackgroundResource(android.R.color.transparent);
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onFinishInflate() {
        mChild = getChildAt(0);
        if (ApiHelper.HAS_VIEW_TRANSFORM_PROPERTIES) {
            mChild.setPivotX(0);
            mChild.setPivotY(0);
        }
    }

    @Override
    protected void onLayout(
            boolean change, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        switch (mOrientation) {
            case 0:
            case 180:
                mChild.layout(0, 0, width, height);
                break;
            case 90:
            case 270:
                mChild.layout(0, 0, height, width);
                break;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!ApiHelper.HAS_VIEW_TRANSFORM_PROPERTIES) {
            final int w = getMeasuredWidth();
            final int h = getMeasuredHeight();
            switch (mOrientation) {
                case 0:
                    mMatrix.setTranslate(0, 0);
                    break;
                case 90:
                    mMatrix.setTranslate(0, -h);
                    break;
                case 180:
                    mMatrix.setTranslate(-w, -h);
                    break;
                case 270:
                    mMatrix.setTranslate(-w, 0);
                    break;
            }
            mMatrix.postRotate(mOrientation);
            event = MotionEventHelper.transformEvent(event, mMatrix);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (ApiHelper.HAS_VIEW_TRANSFORM_PROPERTIES) {
            super.dispatchDraw(canvas);
        } else {
            canvas.save();
            int w = getMeasuredWidth();
            int h = getMeasuredHeight();
            switch (mOrientation) {
                case 0:
                    canvas.translate(0, 0);
                    break;
                case 90:
                    canvas.translate(0, h);
                    break;
                case 180:
                    canvas.translate(w, h);
                    break;
                case 270:
                    canvas.translate(w, 0);
                    break;
            }
            canvas.rotate(-mOrientation, 0, 0);
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = 0, h = 0;
        switch(mOrientation) {
            case 0:
            case 180:
                measureChild(mChild, widthSpec, heightSpec);
                w = mChild.getMeasuredWidth();
                h = mChild.getMeasuredHeight();
                break;
            case 90:
            case 270:
                measureChild(mChild, heightSpec, widthSpec);
                w = mChild.getMeasuredHeight();
                h = mChild.getMeasuredWidth();
                break;
        }
        setMeasuredDimension(w, h);

        if (ApiHelper.HAS_VIEW_TRANSFORM_PROPERTIES) {
            switch (mOrientation) {
                case 0:
                    mChild.setTranslationX(0);
                    mChild.setTranslationY(0);
                    break;
                case 90:
                    mChild.setTranslationX(0);
                    mChild.setTranslationY(h);
                    break;
                case 180:
                    mChild.setTranslationX(w);
                    mChild.setTranslationY(h);
                    break;
                case 270:
                    mChild.setTranslationX(w);
                    mChild.setTranslationY(0);
                    break;
            }
            mChild.setRotation(-mOrientation);
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    // Rotate the view counter-clockwise
    @Override
    public void setOrientation(int orientation, boolean animation) {
        orientation = orientation % 360;
        if (mOrientation == orientation) return;
        mOrientation = orientation;
        requestLayout();
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect r) {
        if (!ApiHelper.HAS_VIEW_TRANSFORM_PROPERTIES && mOrientation != 0) {
            // The workaround invalidates the entire rotate layout. After
            // rotation, the correct area to invalidate may be larger than the
            // size of the child. Ex: ListView. There is no way to invalidate
            // only the necessary area.
            r.set(0, 0, getWidth(), getHeight());
        }
        return super.invalidateChildInParent(location, r);
    }
}
