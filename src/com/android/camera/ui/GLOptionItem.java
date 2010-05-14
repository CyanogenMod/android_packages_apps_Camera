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
import android.view.animation.Transformation;

import com.android.camera.R;

import javax.microedition.khronos.opengles.GL11;

class GLOptionItem extends GLView {
    private static final int FONT_COLOR = Color.WHITE;
    private static final float FONT_SIZE = 18;

    private static final int MINIMAL_WIDTH = 120;
    private static final int MINIMAL_HEIGHT = 32;

    private static final int NO_ICON_LEADING_SPACE = 10;
    private static final int TEXT_LEFT_PADDING = 6;
    private static final int TEXT_RIGHT_PADDING = 10;

    private static final float ENABLED_ALPHA = 1f;
    private static final float DISABLED_ALPHA = 0.3f;

    private static final int HORIZONTAL_PADDINGS = 4;
    private static final int VERTICAL_PADDINGS = 2;

    private static ResourceTexture sCheckOn;
    private static ResourceTexture sCheckOff;

    private static int sNoIconLeadingSpace;
    private static int sTextLeftPadding;
    private static int sTextRightPadding;
    private static int sMinimalWidth;
    private static int sMinimalHeight;
    private static float sFontSize;
    private static int sHorizontalPaddings = -1;
    private static int sVerticalPaddings;

    private final ResourceTexture mIcon;
    private final StringTexture mText;
    private boolean mEnabled = true;

    private ResourceTexture mCheckBox;


    private static void initializeStaticVariables(Context context) {
        if (sCheckOn != null) return;

        sCheckOn = new ResourceTexture(context, R.drawable.ic_menuselect_on);
        sCheckOff = new ResourceTexture(context, R.drawable.ic_menuselect_off);

        sNoIconLeadingSpace = dpToPixel(context, NO_ICON_LEADING_SPACE);
        sTextLeftPadding = dpToPixel(context, TEXT_LEFT_PADDING);
        sTextRightPadding = dpToPixel(context, TEXT_RIGHT_PADDING);
        sMinimalWidth = dpToPixel(context, MINIMAL_WIDTH);
        sMinimalHeight = dpToPixel(context, MINIMAL_HEIGHT);
        sHorizontalPaddings = dpToPixel(context, HORIZONTAL_PADDINGS);
        sVerticalPaddings = dpToPixel(context, VERTICAL_PADDINGS);

        sFontSize = dpToPixel(context, FONT_SIZE);
    }

    public GLOptionItem(Context context, int iconId, String title) {
        initializeStaticVariables(context);
        mIcon = iconId == 0 ? null : new ResourceTexture(context, iconId);
        mText = StringTexture.newInstance(title, sFontSize, FONT_COLOR);
        mCheckBox = sCheckOff;
        setPaddings(sHorizontalPaddings,
                sVerticalPaddings, sHorizontalPaddings, sVerticalPaddings);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = mIcon == null ? sNoIconLeadingSpace : mIcon.getWidth();
        width += mText.getWidth() + mCheckBox.getWidth();
        width += sTextRightPadding + sTextLeftPadding;

        int height = Math.max(Math.max(mIcon == null ? 0 : mIcon.getHeight(),
                mText.getHeight()), mCheckBox.getHeight());

        width = Math.max(sMinimalWidth, width);
        height = Math.max(sMinimalHeight, height);

        new MeasureHelper(this)
                .setPreferredContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        Rect p = mPaddings;

        int width = getWidth() - p.left - p.right;
        int height = getHeight() - p.top - p.bottom;

        int xoffset = p.left;

        Transformation trans = root.getTransformation();
        float oldAlpha = trans.getAlpha();
        trans.setAlpha(oldAlpha * (mEnabled ? ENABLED_ALPHA : DISABLED_ALPHA));

        ResourceTexture icon = mIcon;
        if (icon != null) {
            icon.draw(root, xoffset,
                    p.top + (height - icon.getHeight()) / 2);
            xoffset += icon.getWidth();
        } else {
            xoffset += sNoIconLeadingSpace;
        }

        StringTexture title = mText;
        xoffset += sTextLeftPadding;
        int yoffset = p.top + (height - title.getHeight()) / 2;
        //TODO: cut the text if it is too long
        title.draw(root, xoffset, yoffset);

        ResourceTexture checkbox = mCheckBox;
        yoffset = p.top + (height - checkbox.getHeight()) / 2;
        checkbox.draw(root, width - checkbox.getWidth(), yoffset);
        trans.setAlpha(oldAlpha);
    }

    public void setChecked(boolean checked) {
        mCheckBox = checked ? sCheckOn : sCheckOff;
        invalidate();
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        invalidate();
    }
}
