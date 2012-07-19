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
import android.util.AttributeSet;
import android.view.View;

/*
 * Customized view to support onLayoutChange() at or before API 10.
 */
public class LayoutNotifyView extends View implements LayoutChangeNotifier {
    private LayoutChangeHelper mLayoutChangeHelper = new LayoutChangeHelper(this);

    public LayoutNotifyView(Context context) {
        super(context);
    }

    public LayoutNotifyView(Context context, AttributeSet attrs) {
        super(context, attrs);
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
