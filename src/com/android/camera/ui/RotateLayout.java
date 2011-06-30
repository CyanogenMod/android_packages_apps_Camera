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

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

// A RotateLayout is designed to display a single item and provides the
// capabilities to rotate the item.
class RotateLayout extends ViewGroup {
    private int mOrientation;
    private View mChild;

    public RotateLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mChild = getChildAt(0);
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
    protected void onMeasure(int widthSpec, int heightSpec) {
        switch(mOrientation) {
            case 0:
            case 180:
                measureChild(mChild, widthSpec, heightSpec);
                setMeasuredDimension(mChild.getMeasuredWidth(), mChild.getMeasuredHeight());
                break;
            case 90:
            case 270:
                measureChild(mChild, heightSpec, widthSpec);
                setMeasuredDimension(mChild.getMeasuredHeight(), mChild.getMeasuredWidth());
                break;
        }
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        switch (mOrientation) {
            case 90:
                canvas.translate(0, h);
                canvas.rotate(-mOrientation);
                break;
            case 180:
                canvas.rotate(-mOrientation, w / 2, h / 2);
                break;
            case 270:
                canvas.translate(w, 0);
                canvas.rotate(-mOrientation);
                break;
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float width = getWidth();
        float height = getHeight();
        switch (mOrientation) {
            case 90: event.setLocation(height - y, x); break;
            case 180: event.setLocation(width - x, height - y); break;
            case 270: event.setLocation(y, width - x); break;
        }
        boolean result = mChild.dispatchTouchEvent(event);
        event.setLocation(x, y);
        return result;
    }

    // Rotate the view counter-clockwise
    public void setOrientation(int orientation) {
        orientation = orientation % 360;
        if (mOrientation == orientation) return;
        mOrientation = orientation;
        requestLayout();
    }
}
