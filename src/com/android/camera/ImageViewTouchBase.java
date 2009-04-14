/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

abstract class ImageViewTouchBase extends ImageView {

    @SuppressWarnings("unused")
    private static final String TAG = "ImageViewTouchBase";

    // This is the base transformation which is used to show the image
    // initially.  The current computation for this shows the image in
    // it's entirety, letterboxing as needed.  One could choose to
    // show the image as cropped instead.
    //
    // This matrix is recomputed when we go from the thumbnail image to
    // the full size image.
    protected Matrix mBaseMatrix = new Matrix();

    // This is the supplementary transformation which reflects what
    // the user has done in terms of zooming and panning.
    //
    // This matrix remains the same when we go from the thumbnail image
    // to the full size image.
    protected Matrix mSuppMatrix = new Matrix();

    // This is the final matrix which is computed as the concatentation
    // of the base matrix and the supplementary matrix.
    private Matrix mDisplayMatrix = new Matrix();

    // Temporary buffer used for getting the values out of a matrix.
    private float[] mMatrixValues = new float[9];

    // The current bitmap being displayed.
    protected Bitmap mBitmapDisplayed;

    int mThisWidth = -1, mThisHeight = -1;

    float mMaxZoom;

    // ImageViewTouchBase will pass a Bitmap to the Recycler if it has finished
    // its use of that Bitmap.
    public interface Recycler {
        public void recycle(Bitmap b);
    }

    public void setRecycler(Recycler r) {
        mRecycler = r;
    }

    private Recycler mRecycler;

    @Override
    protected void onLayout(boolean changed, int left, int top,
                            int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mThisWidth = right - left;
        mThisHeight = bottom - top;
        Runnable r = mOnLayoutRunnable;
        if (r != null) {
            mOnLayoutRunnable = null;
            r.run();
        }
        if (mBitmapDisplayed != null) {
            getProperBaseMatrix(mBitmapDisplayed, mBaseMatrix);
            setImageMatrix(getImageViewMatrix());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && getScale() > 1.0f) {
            // If we're zoomed in, pressing Back jumps out to show the entire
            // image, otherwise Back returns the user to the gallery.
            zoomTo(1.0f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    protected Handler mHandler = new Handler();

    protected int mLastXTouchPos;
    protected int mLastYTouchPos;

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        Drawable d = getDrawable();
        if (d != null) {
            d.setDither(true);
        }

        Bitmap old = mBitmapDisplayed;
        mBitmapDisplayed = bitmap;
        if (old != null && old != bitmap && mRecycler != null) {
            mRecycler.recycle(old);
        }
    }

    public void clear() {
        setImageBitmapResetBase(null, true);
    }

    private Runnable mOnLayoutRunnable = null;

    // This function changes bitmap, reset base matrix according to the size
    // of the bitmap, and optionally reset the supplementary matrix.
    public void setImageBitmapResetBase(final Bitmap bitmap,
            final boolean resetSupp) {
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();

        if (viewWidth <= 0)  {
            mOnLayoutRunnable = new Runnable() {
                public void run() {
                    setImageBitmapResetBase(bitmap, resetSupp);
                }
            };
            return;
        }

        if (bitmap != null) {
            getProperBaseMatrix(bitmap, mBaseMatrix);
            setImageBitmap(bitmap);
        } else {
            mBaseMatrix.reset();
            setImageBitmap(null);
        }

        if (resetSupp) {
            mSuppMatrix.reset();
        }
        setImageMatrix(getImageViewMatrix());
        mMaxZoom = maxZoom();
    }

    // Center as much as possible in one or both axis.  Centering is
    // defined as follows:  if the image is scaled down below the
    // view's dimensions then center it (literally).  If the image
    // is scaled larger than the view and is translated out of view
    // then translate it back into view (i.e. eliminate black bars).
    protected void center(boolean vertical, boolean horizontal,
            boolean animate) {
        if (mBitmapDisplayed == null) {
            return;
        }

        Matrix m = getImageViewMatrix();

        float [] topLeft  = new float[] { 0, 0 };
        float [] botRight = new float[] { mBitmapDisplayed.getWidth(),
                                          mBitmapDisplayed.getHeight() };

        m.mapPoints(topLeft);
        m.mapPoints(botRight);

        float height = botRight[1] - topLeft[1];
        float width  = botRight[0] - topLeft[0];

        float deltaX = 0, deltaY = 0;

        if (vertical) {
            int viewHeight = getHeight();
            if (height < viewHeight) {
                deltaY = (viewHeight - height) / 2 - topLeft[1];
            } else if (topLeft[1] > 0) {
                deltaY = -topLeft[1];
            } else if (botRight[1] < viewHeight) {
                deltaY = getHeight() - botRight[1];
            }
        }

        if (horizontal) {
            int viewWidth = getWidth();
            if (width < viewWidth) {
                deltaX = (viewWidth - width) / 2 - topLeft[0];
            } else if (topLeft[0] > 0) {
                deltaX = -topLeft[0];
            } else if (botRight[0] < viewWidth) {
                deltaX = viewWidth - botRight[0];
            }
        }

        postTranslate(deltaX, deltaY);
        if (animate) {
            Animation a = new TranslateAnimation(-deltaX, 0, -deltaY, 0);
            a.setStartTime(SystemClock.elapsedRealtime());
            a.setDuration(250);
            setAnimation(a);
        }
        setImageMatrix(getImageViewMatrix());
    }

    public ImageViewTouchBase(Context context) {
        super(context);
        init();
    }

    public ImageViewTouchBase(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setScaleType(ImageView.ScaleType.MATRIX);
    }

    protected float getValue(Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    // Get the scale factor out of the matrix.
    protected float getScale(Matrix matrix) {
        return getValue(matrix, Matrix.MSCALE_X);
    }

    protected float getScale() {
        return getScale(mSuppMatrix);
    }

    // Setup the base matrix so that the image is centered and scaled properly.
    private void getProperBaseMatrix(Bitmap bitmap, Matrix matrix) {
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        matrix.reset();
        float widthScale = Math.min(viewWidth / bitmap.getWidth(),
                1.0f);
        float heightScale = Math.min(viewHeight / bitmap.getHeight(),
                1.0f);
        float scale;
        if (widthScale > heightScale) {
            scale = heightScale;
        } else {
            scale = widthScale;
        }
        matrix.setScale(scale, scale);
        matrix.postTranslate(
                (viewWidth  - (bitmap.getWidth()  * scale)) / 2F,
                (viewHeight - (bitmap.getHeight() * scale)) / 2F);
    }

    // Combine the base matrix and the supp matrix to make the final matrix.
    protected Matrix getImageViewMatrix() {
        // The final matrix is computed as the concatentation of the base matrix
        // and the supplementary matrix.
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(mSuppMatrix);
        return mDisplayMatrix;
    }

    static final float SCALE_RATE = 1.25F;

    // Sets the maximum zoom, which is a scale relative to the base matrix. It
    // is calculated to show the image at 400% zoom regardless of screen or
    // image orientation. If in the future we decode the full 3 megapixel image,
    // rather than the current 1024x768, this should be changed down to 200%.
    protected float maxZoom() {
        if (mBitmapDisplayed == null) {
            return 1F;
        }

        float fw = (float) mBitmapDisplayed.getWidth()  / (float) mThisWidth;
        float fh = (float) mBitmapDisplayed.getHeight() / (float) mThisHeight;
        float max = Math.max(fw, fh) * 4;
        return max;
    }

    protected void zoomTo(float scale, float centerX, float centerY) {
        if (scale > mMaxZoom) {
            scale = mMaxZoom;
        }

        float oldScale = getScale();
        float deltaScale = scale / oldScale;

        mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center(true, true, false);
    }

    protected void zoomTo(final float scale, final float centerX,
                          final float centerY, final float durationMs) {
        final float incrementPerMs = (scale - getScale()) / durationMs;
        final float oldScale = getScale();
        final long startTime = System.currentTimeMillis();

        mHandler.post(new Runnable() {
            public void run() {
                long now = System.currentTimeMillis();
                float currentMs = Math.min(durationMs, now - startTime);
                float target = oldScale + (incrementPerMs * currentMs);
                zoomTo(target, centerX, centerY);

                if (currentMs < durationMs) {
                    mHandler.post(this);
                }
            }
        });
    }

    protected void zoomTo(float scale) {
        float cx = getWidth() / 2F;
        float cy = getHeight() / 2F;

        zoomTo(scale, cx, cy);
    }

    protected void zoomIn() {
        zoomIn(SCALE_RATE);
    }

    protected void zoomOut() {
        zoomOut(SCALE_RATE);
    }

    protected void zoomIn(float rate) {
        if (getScale() >= mMaxZoom) {
            return;     // Don't let the user zoom into the molecular level.
        }
        if (mBitmapDisplayed == null) {
            return;
        }

        float cx = getWidth() / 2F;
        float cy = getHeight() / 2F;

        mSuppMatrix.postScale(rate, rate, cx, cy);
        setImageMatrix(getImageViewMatrix());
    }

    protected void zoomOut(float rate) {
        if (mBitmapDisplayed == null) {
            return;
        }

        float cx = getWidth() / 2F;
        float cy = getHeight() / 2F;

        // Zoom out to at most 1x.
        Matrix tmp = new Matrix(mSuppMatrix);
        tmp.postScale(1F / rate, 1F / rate, cx, cy);

        if (getScale(tmp) < 1F) {
            mSuppMatrix.setScale(1F, 1F, cx, cy);
        } else {
            mSuppMatrix.postScale(1F / rate, 1F / rate, cx, cy);
        }
        setImageMatrix(getImageViewMatrix());
        center(true, true, false);
    }

    protected void postTranslate(float dx, float dy) {
        mSuppMatrix.postTranslate(dx, dy);
    }

    protected void panBy(float dx, float dy) {
        postTranslate(dx, dy);
        setImageMatrix(getImageViewMatrix());
    }
}
