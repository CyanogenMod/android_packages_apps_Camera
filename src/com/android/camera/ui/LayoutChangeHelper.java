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

import android.view.View;

public class LayoutChangeHelper implements LayoutChangeNotifier {
    private LayoutChangeNotifier.Listener mListener;
    private boolean mFirstTimeLayout;
    private View mView;

    public LayoutChangeHelper(View v) {
        mView = v;
        mFirstTimeLayout = true;
    }

    @Override
    public void setOnLayoutChangeListener(LayoutChangeNotifier.Listener listener) {
        mListener = listener;
    }

    public void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mListener == null) return;
        if (mFirstTimeLayout || changed) {
            mFirstTimeLayout = false;
            mListener.onLayoutChange(mView, l, t, r, b);
        }
    }
}
