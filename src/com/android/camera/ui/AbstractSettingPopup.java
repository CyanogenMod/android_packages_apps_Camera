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
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.R;

// A popup window that shows one or more camera settings.
abstract public class AbstractSettingPopup extends LinearLayout {
    protected ViewGroup mContentPanel;
    protected TextView mTitle;

    public AbstractSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitle = (TextView) findViewById(R.id.title);
        View topPanel = findViewById(R.id.topPanel);
        mContentPanel = (ViewGroup) findViewById(R.id.contentPanel);

        // Use system holo background for now.
        // TODO: We need to add alpha to the background.
        Context context = getContext();
        Theme dialogTheme = context.getResources().newTheme();
        dialogTheme.applyStyle(android.R.style.Theme_Holo, true);
        TypedArray ta = dialogTheme.obtainStyledAttributes(new int[] {
                android.R.attr.alertDialogStyle });
        int resourceId = ta.getResourceId(0, 0);
        TypedArray ta2 = context.obtainStyledAttributes(resourceId, new int[] {
                android.R.attr.topDark,
                android.R.attr.bottomDark});

        topPanel.setBackgroundDrawable(ta2.getDrawable(0));
        mContentPanel.setBackgroundDrawable(ta2.getDrawable(1));

        ta.recycle();
        ta2.recycle();
    }
}
