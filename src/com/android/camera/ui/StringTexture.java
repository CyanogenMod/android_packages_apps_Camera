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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;

class StringTexture extends CanvasTexture {
    private static int DEFAULT_PADDING = 1;

    private final String mText;
    private final Paint mPaint;
    private final FontMetricsInt mMetrics;

    public StringTexture(String text, Paint paint,
            FontMetricsInt metrics, int width, int height) {
        super(width, height);
        mText = text;
        mPaint = paint;
        mMetrics = metrics;
    }


    public static StringTexture newInstance(String text, Paint paint) {
        FontMetricsInt metrics = paint.getFontMetricsInt();
        int width = (int) (.5f + paint.measureText(text)) + DEFAULT_PADDING * 2;
        int height = metrics.bottom - metrics.top + DEFAULT_PADDING * 2;
        return new StringTexture(text, paint, metrics, width, height);
    }

    public static StringTexture newInstance(
            String text, float textSize, int color) {
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);

        return newInstance(text, paint);
    }

    @Override
    protected void onDraw(Canvas canvas, Bitmap backing) {
        canvas.translate(DEFAULT_PADDING, DEFAULT_PADDING - mMetrics.ascent);
        canvas.drawText(mText, 0, 0, mPaint);
    }
}
