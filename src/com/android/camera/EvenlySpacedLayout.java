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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

//
// This is a layout which makes the children even spaced.
// Currently it does not consider the padding parameters.
//
public class EvenlySpacedLayout extends ViewGroup {
    private boolean mHorizontal;

    // Wheather we keep the space in both ends of the layout
    private boolean mKeepEndSpace;

    public EvenlySpacedLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.EvenlySpacedLayout, 0, 0);
        mHorizontal = (0 == a.getInt(
                R.styleable.EvenlySpacedLayout_orientation, 0));
        mKeepEndSpace = a.getBoolean(
                R.styleable.EvenlySpacedLayout_keepEndSpace, true);
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int width = 0;
        int height = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            if (mHorizontal) {
                width += child.getMeasuredWidth();
                height = Math.max(height, child.getMeasuredHeight());
            } else {
                height += child.getMeasuredHeight();
                width = Math.max(width, child.getMeasuredWidth());
            }
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    private void layoutHorizontal(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();

        int usedWidth = 0;
        int usedChildren = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            usedWidth += child.getMeasuredWidth();
            ++usedChildren;
        }

        int spacing = (r - l - usedWidth) /
                (mKeepEndSpace ? (usedChildren + 1) : (usedChildren - 1));
        int left = mKeepEndSpace ? spacing : 0;
        int top = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            child.layout(left, top, left + w, top + h);
            left += w;
            left += spacing;
        }
    }

    private void layoutVertical(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();

        int usedHeight = 0;
        int usedChildren = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            usedHeight += child.getMeasuredHeight();
            ++usedChildren;
        }

        int spacing = (b - t - usedHeight) /
                (mKeepEndSpace ? (usedChildren + 1) : (usedChildren - 1));
        int top = mKeepEndSpace ? spacing : 0;
        int left = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            child.layout(left, top, left + w, top + h);
            top += h;
            top += spacing;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mHorizontal) {
            layoutHorizontal(changed, l, t, r, b);
        } else {
            layoutVertical(changed, l, t, r, b);
        }
    }
}
