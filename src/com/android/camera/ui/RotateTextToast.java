/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.camera.R;
import com.android.camera.Util;

import android.app.Activity;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class RotateTextToast {
    private static final int TOAST_DURATION = 5000; // milliseconds
    ViewGroup mLayoutRoot;
    RotateLayout mToast;
    Handler mHandler;

    public RotateTextToast(Activity activity, int textResourceId, int orientation) {
        mLayoutRoot = (ViewGroup) activity.getWindow().getDecorView();
        LayoutInflater inflater = activity.getLayoutInflater();
        View v = inflater.inflate(R.layout.rotate_text_toast, mLayoutRoot);
        mToast = (RotateLayout) v.findViewById(R.id.rotate_toast);
        TextView tv = (TextView) mToast.findViewById(R.id.message);
        tv.setText(textResourceId);
        mToast.setOrientation(orientation);
        mHandler = new Handler();
    }

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            Util.fadeOut(mToast);
            mLayoutRoot.removeView(mToast);
            mToast = null;
        }
    };

    public void show() {
        mToast.setVisibility(View.VISIBLE);
        mHandler.postDelayed(mRunnable, TOAST_DURATION);
    }
}
