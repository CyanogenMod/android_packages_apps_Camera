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
import android.util.Log;

import com.android.camera.R;
import com.android.camera.ui.ZoomControllerListener;

import java.text.DecimalFormat;

class ZoomIndicator extends AbstractIndicator {
    private static final DecimalFormat sZoomFormat = new DecimalFormat("#.#x");
    private static final float FONT_SIZE = 18;
    private static final int FONT_COLOR = 0xA8FFFFFF;
    private static final int COLOR_OPTION_HEADER = 0xFF2B2B2B;

    protected static final String TAG = "ZoomIndicator";

    private final float mFontSize;

    private ZoomController mZoomController;
    private LinearLayout mPopupContent;
    private ZoomControllerListener mZoomListener;
    private int mZoomIndex = 0;
    private int mDrawIndex = -1;
    private float mZoomRatios[];

    private StringTexture mTitle;

    public ZoomIndicator(Context context) {
        super(context);
        mFontSize = GLRootView.dpToPixel(context, FONT_SIZE);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxWidth = 0;
        int maxHeight = 0;
        int n = mZoomRatios == null ? 0: mZoomRatios.length;
        for (int i = 0; i < n; ++i) {
            float value = mZoomRatios[i];
            BitmapTexture tex = StringTexture.newInstance(
                    sZoomFormat.format(value), mFontSize, FONT_COLOR);
            if (maxWidth < tex.getWidth()) maxWidth = tex.getWidth();
            if (maxHeight < tex.getHeight()) maxHeight = tex.getHeight();
        }
        new MeasureHelper(this)
                .setPreferredContentSize(maxWidth, maxHeight)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected BitmapTexture getIcon() {
        if (mDrawIndex != mZoomIndex) {
            mDrawIndex = mZoomIndex;
            if (mTitle != null) mTitle.deleteFromGL();
            float value = mZoomRatios[mZoomIndex];
            mTitle = StringTexture.newInstance(
                    sZoomFormat.format(value), mFontSize, FONT_COLOR);
        }
        return mTitle;
    }

    @Override
    public GLView getPopupContent() {
        if (mZoomController == null) {
            Context context = getGLRootView().getContext();
            mZoomController = new ZoomController(context);
            mZoomController.setAvailableZoomRatios(mZoomRatios);
            mZoomController.setPaddings(15, 6, 15, 6);

            mPopupContent = new LinearLayout();
            GLOptionHeader header = new GLOptionHeader(context,
                    context.getString(R.string.zoom_control_title));
            header.setBackground(new ColorTexture(COLOR_OPTION_HEADER));
            header.setPaddings(6, 3, 6, 3);
            mPopupContent.addComponent(header);
            mPopupContent.addComponent(mZoomController);

            mZoomController.setZoomListener(new MyZoomListener());
            mZoomController.setZoomIndex(mZoomIndex);
        }
        return mPopupContent;
    }

    @Override
    public void overrideSettings(String key, String settings) {
        // do nothing
    }

    @Override
    public void reloadPreferences() {
        // do nothing
    }

    public void setZoomRatios(float[] ratios) {
        mZoomRatios = ratios;
        mDrawIndex = -1;
        invalidate();
    }

    private class MyZoomListener implements ZoomControllerListener {
        public void onZoomChanged(int index, float value, boolean isMoving) {
            if (mZoomListener != null) {
                mZoomListener.onZoomChanged(index, value, isMoving);
            }
            if (mZoomIndex != index) onZoomIndexChanged(index);
        }
    }

    private void onZoomIndexChanged(int index) {
        if (mZoomIndex == index) return;
        mZoomIndex = index;
        invalidate();
    }

    public void setZoomListener(ZoomControllerListener listener) {
        mZoomListener = listener;
    }

    public void setZoomIndex(int index) {
        if (mZoomIndex == index) return;
        if (mZoomController != null) {
            mZoomController.setZoomIndex(index);
        } else {
            onZoomIndexChanged(index);
        }
    }
}
