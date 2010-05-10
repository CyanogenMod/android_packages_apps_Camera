package com.android.camera.ui;

import com.android.camera.Util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

class ResourceTexture extends Texture {

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
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        mBitmap = BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
        setSize(mBitmap.getWidth(), mBitmap.getHeight());
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
