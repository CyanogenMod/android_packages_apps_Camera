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

import android.graphics.Rect;
import android.view.View.MeasureSpec;

class LinearLayout extends GLView {

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = 0;
        int height = 0;
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView view = getComponent(i);
            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            width = Math.max(width, view.getMeasuredWidth());
            height += view.getMeasuredHeight();
        }
        new MeasureHelper(this)
                .setPreferredContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Rect p = mPaddings;
        int offsetX = p.left;
        int width = (r - l) - p.left - p.right;
        int offsetY = p.top;
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView view = getComponent(i);
            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            int nextOffsetY = offsetY + view.getMeasuredHeight();
            view.layout(offsetX, offsetY, offsetX + width, nextOffsetY);
            offsetY = nextOffsetY;
        }
    }

}
