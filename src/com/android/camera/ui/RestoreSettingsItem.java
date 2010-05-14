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

import static com.android.camera.ui.GLRootView.dpToPixel;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;

import javax.microedition.khronos.opengles.GL11;

class RestoreSettingsItem extends GLView {
    private static final int FONT_COLOR = Color.WHITE;
    private static final float FONT_SIZE = 18;

    private static final int LEFT_PADDING = 20;
    private static final int RIGHT_PADDING = 4;
    private static final int TOP_PADDING = 2;
    private static final int BOTTOM_PADDING = 2;

    private static int sLeftPadding = -1;
    private static int sRightPadding;
    private static int sTopPadding;
    private static int sBottomPadding;
    private static float sFontSize;

    private final StringTexture mText;

    private static void initializeStaticVariables(Context context) {
        if (sLeftPadding >= 0) return;

        sLeftPadding = dpToPixel(context, LEFT_PADDING);
        sRightPadding = dpToPixel(context, RIGHT_PADDING);
        sTopPadding = dpToPixel(context, TOP_PADDING);
        sBottomPadding = dpToPixel(context, BOTTOM_PADDING);
        sFontSize = dpToPixel(context, FONT_SIZE);
    }

    public RestoreSettingsItem(Context context, String title) {
        initializeStaticVariables(context);
        mText = StringTexture.newInstance(title, sFontSize, FONT_COLOR);
        setPaddings(sLeftPadding, sTopPadding, sRightPadding, sBottomPadding);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        new MeasureHelper(this)
                .setPreferredContentSize(mText.getWidth(), mText.getHeight())
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        Rect p = mPaddings;
        int height = getHeight() - p.top - p.bottom;

        StringTexture title = mText;
        //TODO: cut the text if it is too long
        title.draw(root, p.left, p.top + (height - title.getHeight()) / 2);
    }
}
