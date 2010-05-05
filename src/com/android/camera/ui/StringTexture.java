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
