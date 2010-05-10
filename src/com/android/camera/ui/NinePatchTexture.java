package com.android.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;

import javax.microedition.khronos.opengles.GL11;

class NinePatchTexture extends FrameTexture {

    private MyTexture mDelegate;

    private NinePatchDrawable mNinePatch;

    private final Context mContext;
    private final int mResId;

    private int mLastWidth = -1;
    private int mLastHeight = -1;

    private final Rect mPaddings = new Rect();

    public NinePatchTexture(Context context, int resId) {
        this.mContext = context;
        this.mResId = resId;
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
    }

    @Override
    protected boolean bind(GLRootView root, GL11 gl) {
        if (mLastWidth != mWidth || mLastHeight != mHeight) {
            if (mDelegate != null) mDelegate.deleteFromGL();
            mDelegate = new MyTexture(mWidth, mHeight);
            mLastWidth = mWidth;
            mLastHeight = mHeight;
        }
        return mDelegate.bind(root, gl);
    }

    @Override
    public void getTextureCoords(float coord[], int offset) {
        mDelegate.getTextureCoords(coord, offset);
    }

    protected NinePatchDrawable getNinePatch() {
        if (mNinePatch == null) {
            mNinePatch = (NinePatchDrawable)
                    mContext.getResources().getDrawable(mResId);
            mNinePatch.getPadding(mPaddings);
        }
        return mNinePatch;
    }

    private class MyTexture extends CanvasTexture {

        public MyTexture(int width, int height) {
            super(width, height);
        }

        @Override
        protected void onDraw (Canvas canvas, Bitmap backing) {
            NinePatchDrawable npd = getNinePatch();
            npd.setBounds(0, 0, mWidth, mHeight);
            npd.draw(canvas);
        }
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        mDelegate.freeBitmap(bitmap);
    }

    @Override
    protected Bitmap getBitmap() {
        return mDelegate.getBitmap();
    }

    public int getIntrinsicWidth() {
        return getNinePatch().getIntrinsicWidth();
    }

    public int getIntrinsicHeight() {
        return getNinePatch().getIntrinsicHeight();
    }

    @Override
    public Rect getPaddings() {
        // get the paddings from nine patch
        if (mNinePatch == null) getNinePatch();
        return mPaddings;
    }
}
