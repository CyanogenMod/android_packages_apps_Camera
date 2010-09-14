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
import android.view.MotionEvent;

import com.android.camera.R;
import com.android.camera.Util;

import java.text.DecimalFormat;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL11;

public class SettingsController extends GLView {
    private static final int LABEL_COLOR = Color.WHITE;

    private static final int INVALID_POSITION = Integer.MAX_VALUE;

    private static final float LABEL_FONT_SIZE = 10;
    private static final int HORIZONTAL_PADDING = 1;
    private static final int VERTICAL_PADDING = 3;
    private static final int MINIMAL_HEIGHT = 150;
    private static final float TOLERANCE_RADIUS = 30;

    private static float sLabelSize;
    private static int sHorizontalPadding;
    private static int sVerticalPadding;
    private static int sMinimalHeight;
    private static float sToleranceRadius;

    private static NinePatchTexture sBackground;
    private static Texture sSlider;
    private static Texture sTickMark;
    private static Texture sFineTickMark;

    private StringTexture mTickLabels[];
    private String mValues[];
    private int mIndex;

    private int mFineTickStep;
    private int mLabelStep;

    private int mMaxLabelWidth;
    private int mMaxLabelHeight;

    private int mSliderTop;
    private int mSliderBottom;
    private int mSliderLeft;
    private int mSliderPosition = INVALID_POSITION;
    private float mValueGap;
    private ValueListener mValueListener;

    public interface ValueListener {
        public void onValueChanged(int index, String value, boolean isMoving);
    }

    public SettingsController(Context context) {
        initializeStaticVariable(context);
    }

    private void onSliderMoved(int position, boolean isMoving) {
        position = Util.clamp(position,
                mSliderTop, mSliderBottom - sSlider.getHeight());
        mSliderPosition = position;
        invalidate();

        int index = mValues.length - 1 - (int)
                ((float)(position - mSliderTop) /  mValueGap + .5f);
        if (index != mIndex || !isMoving) {
            mIndex = index;
            if (mValueListener != null) {
                mValueListener.onValueChanged(mIndex, mValues[mIndex], isMoving);
            }
        }
    }

    private static void initializeStaticVariable(Context context) {
        if (sBackground != null) return;

        sLabelSize = dpToPixel(context, LABEL_FONT_SIZE);
        sHorizontalPadding = dpToPixel(context, HORIZONTAL_PADDING);
        sVerticalPadding = dpToPixel(context, VERTICAL_PADDING);
        sMinimalHeight = dpToPixel(context, MINIMAL_HEIGHT);
        sToleranceRadius = dpToPixel(context, TOLERANCE_RADIUS);

        sBackground = new NinePatchTexture(context, R.drawable.settings_background);
        sSlider = new ResourceTexture(context, R.drawable.settings_slider);
        sTickMark = new ResourceTexture(context, R.drawable.zoom_tickmark);
        sFineTickMark = new ResourceTexture(
                context, R.drawable.zoom_finetickmark);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!changed) return;
        Rect p = mPaddings;
        int height = b - t - p.top - p.bottom;
        int margin = Math.max(sSlider.getHeight(), mMaxLabelHeight);
        mValueGap = (float) (height - margin) / (mValues.length - 1);

        mSliderLeft = p.left + mMaxLabelWidth + sHorizontalPadding
                + sTickMark.getWidth() + sHorizontalPadding;

        mSliderTop = p.top + margin / 2 - sSlider.getHeight() / 2;
        mSliderBottom = mSliderTop + height - margin + sSlider.getHeight();
    }

    private boolean withInToleranceRange(float x, float y) {
        float sx = mSliderLeft + sSlider.getWidth() / 2;
        float sy = mSliderTop + (mValues.length - 1 - mIndex) * mValueGap
                + sSlider.getHeight() / 2;
        float dist = Util.distance(x, y, sx, sy);
        return dist <= sToleranceRadius;
    }

    @Override
    protected boolean onTouch(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (withInToleranceRange(x, y)) {
                    onSliderMoved((int) (y - sSlider.getHeight()), true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mSliderPosition != INVALID_POSITION) {
                    onSliderMoved((int) (y - sSlider.getHeight()), true);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mSliderPosition != INVALID_POSITION) {
                    onSliderMoved((int) (y - sSlider.getHeight()), false);
                    mSliderPosition = INVALID_POSITION;
                }
                return true;
        }
        return true;
    }

    public void setAvailableValues(String values[]) {
        if (Arrays.equals(values, mValues)) return;
        mValues = values;
        mLabelStep = getLabelStep(values.length);
        mTickLabels = new StringTexture[
                (values.length + mLabelStep - 1) / mLabelStep];
        for (int i = 0, n = mTickLabels.length; i < n; ++i) {
            mTickLabels[i] = StringTexture.newInstance(
                    String.valueOf(values[i * mLabelStep]),
                    sLabelSize, LABEL_COLOR);
        }
        mFineTickStep = mLabelStep % 3 == 0
                ? mLabelStep / 3
                : mLabelStep %2 == 0 ? mLabelStep / 2 : 0;

        int maxHeight = 0;
        int maxWidth = 0;
        int labelCount = mTickLabels.length;
        for (int i = 0; i < labelCount; ++i) {
            maxWidth = Math.max(maxWidth, mTickLabels[i].getWidth());
            maxHeight = Math.max(maxHeight, mTickLabels[i].getHeight());
        }

        mMaxLabelHeight = maxHeight;
        mMaxLabelWidth = maxWidth;
        invalidate();
    }

    private int getLabelStep(final int valueCount) {
        if (valueCount < 5) return 1;
        for (int step = valueCount / 5;; ++step) {
            if (valueCount / step <= 5) return step;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int labelCount = mTickLabels.length;
        int ratioCount = mValues.length;

        int height = (mMaxLabelHeight + sVerticalPadding)
                * (labelCount - 1) * ratioCount / (mLabelStep * labelCount)
                + Math.max(sSlider.getHeight(), mMaxLabelHeight);

        int width = mMaxLabelWidth + sHorizontalPadding + sTickMark.getWidth()
                + sHorizontalPadding + sBackground.getIntrinsicWidth();
        height = Math.max(sMinimalHeight, height);

        new MeasureHelper(this)
                .setPreferredContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        renderTicks(root, gl);
        renderSlider(root, gl);
    }

    private void renderTicks(GLRootView root, GL11 gl) {
        float gap = mValueGap;
        int labelStep = mLabelStep;

        // render the tick labels
        int xoffset = mPaddings.left + mMaxLabelWidth;
        float yoffset = mSliderBottom - sSlider.getHeight() / 2;
        for (int i = 0, n = mTickLabels.length; i < n; ++i) {
            Texture t = mTickLabels[i];
            t.draw(root, xoffset - t.getWidth(),
                    (int) (yoffset - t.getHeight() / 2));
            yoffset -= labelStep * gap;
        }

        // render the main tick marks
        Texture tickMark = sTickMark;
        xoffset += sHorizontalPadding;
        yoffset = mSliderBottom - sSlider.getHeight() / 2;
        int halfHeight = tickMark.getHeight() / 2;
        for (int i = 0, n = mTickLabels.length; i < n; ++i) {
            tickMark.draw(root, xoffset, (int) (yoffset - halfHeight));
            yoffset -= labelStep * gap;
        }

        if (mFineTickStep > 0) {
            // render the fine tick marks
            tickMark = sFineTickMark;
            xoffset += sTickMark.getWidth() - tickMark.getWidth();
            yoffset = mSliderBottom - sSlider.getHeight() / 2;
            halfHeight = tickMark.getHeight() / 2;
            for (int i = 0, n = mValues.length; i < n; ++i) {
                if (i % mLabelStep != 0) {
                    tickMark.draw(root, xoffset, (int) (yoffset - halfHeight));
                }
                yoffset -= gap;
            }
        }
    }

    private void renderSlider(GLRootView root, GL11 gl) {
        int left = mSliderLeft;
        int bottom = mSliderBottom;
        int top = mSliderTop;
        sBackground.setSize(sBackground.getIntrinsicWidth(), bottom - top);
        sBackground.draw(root, left, top);

        if (mSliderPosition == INVALID_POSITION) {
            sSlider.draw(root, left, (int)
                    (top + mValueGap * (mValues.length - 1 - mIndex)));
        } else {
            sSlider.draw(root, left, mSliderPosition);
        }
    }

    public void setValueListener(ValueListener listener) {
        mValueListener = listener;
    }

    public void setValueIndex(int index) {
        index = Util.clamp(index, 0, mValues.length - 1);
        if (mIndex == index) return;
        mIndex = index;
        if (mValueListener != null) {
            mValueListener.onValueChanged(mIndex, mValues[mIndex], false);
        }
    }
}
