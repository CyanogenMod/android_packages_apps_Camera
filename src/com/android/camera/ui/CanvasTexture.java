package com.android.camera.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/** Using a canvas to draw the texture */
public abstract class CanvasTexture extends Texture {
    protected Canvas mCanvas;

    public CanvasTexture(int width, int height) {
        setSize(width, height);
    }

    @Override
    protected Bitmap getBitmap() {
        Bitmap bitmap = generateGLCompatibleBitmap(mWidth, mHeight);
        mCanvas = new Canvas(bitmap);
        onDraw(mCanvas, bitmap);
        return bitmap;
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        bitmap.recycle();
    }

    abstract protected void onDraw(Canvas canvas, Bitmap backing);
}
