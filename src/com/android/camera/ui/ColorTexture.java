package com.android.camera.ui;

import android.graphics.Bitmap;
import android.graphics.Rect;

class ColorTexture extends FrameTexture {

    private static final Rect EMPTY_PADDINGS = new Rect();
    private int mColor;

    public ColorTexture(int color) {
        mColor = color;
    }

    @Override
    public Rect getPaddings() {
        return EMPTY_PADDINGS;
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Bitmap getBitmap() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void draw(GLRootView root, int x, int y) {
        root.drawColor(x, y, mWidth, mHeight, mColor);
    }
}
