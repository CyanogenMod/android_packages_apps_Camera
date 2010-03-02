package com.android.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;

import com.android.camera.Util;

public class ResourceTexture extends Texture {

    private final Context mContext;
    private final int mResId;
    private Bitmap mBitmap;

    public ResourceTexture(Context context, int resId) {
        mContext = Util.checkNotNull(context);
        mResId = resId;
    }

    @Override
    protected Bitmap getBitmap() {
        if (mBitmap != null) return mBitmap;
        mBitmap = BitmapFactory.decodeResource(mContext.getResources(), mResId);
        setSize(mBitmap.getWidth(), mBitmap.getHeight());

        if (Util.isPowerOf2(mWidth) && Util.isPowerOf2(mHeight)) return mBitmap;

        Bitmap oldBitmap = mBitmap;
        mBitmap = generateGLCompatibleBitmap(mWidth, mHeight);
        Canvas canvas = new Canvas(mBitmap);
        canvas.drawBitmap(oldBitmap, new Matrix(), null);
        oldBitmap.recycle();

        return mBitmap;
    }

    @Override
    public int getHeight() {
        if (mHeight == UNSPECIFIED) {
            getBitmap();
        }
        return mHeight;
    }

    @Override
    public int getWidth() {
        if (mHeight == UNSPECIFIED) {
            getBitmap();
        }
        return mWidth;
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        Util.Assert(bitmap == mBitmap);
        bitmap.recycle();
        mBitmap = null;
    }

}
