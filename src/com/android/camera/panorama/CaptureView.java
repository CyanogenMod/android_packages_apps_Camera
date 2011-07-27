/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.camera.panorama;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

class CaptureView extends View {
    private static final String TAG = "CaptureView";

    private Canvas mCanvas;
    private Bitmap mCanvasBitmap;
    private String mStatusText = "";
    private int mStartAngle = 0;
    private int mSweepAngle = 0;
    private int mWidth;
    private int mHeight;
    private Bitmap mBitmap = null;
    private Matrix mM = null;
    private Matrix mMLast = null;
    private final Paint mPaint = new Paint();
    // Origin of the coordinate for appending a new alpha bitmap.
    // mCanvasBitmap is 2000x2000, but the origin is set to (800, 800).
    // All the alpha bitmaps grow from this origin.
    float mAlphaOriginX;
    float mAlphaOriginY;


    public CaptureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mM = new Matrix();
        mMLast = new Matrix();
        mMLast.reset();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.RED);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(40);
        mPaint.setTypeface(Typeface.create((Typeface) null, Typeface.BOLD));
        mPaint.setTextAlign(Align.CENTER);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mCanvasBitmap != null) {
            mCanvasBitmap.recycle();
        }
        Log.v(TAG, "onSizeChanged: W = " + w + ", H = " + h);
        // TODO: 2000x1000 is a temporary setting from SRI's code. Should be fixed once the code is
        // refactored.
        mCanvasBitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas();
        mCanvas.setBitmap(mCanvasBitmap);
        mAlphaOriginX = mCanvasBitmap.getWidth() * 0.4f;
        mAlphaOriginY = mCanvasBitmap.getHeight() * 0.4f;
    }

    public void onResume() {
        if (mCanvasBitmap == null) {
            mCanvasBitmap = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas();
            mCanvas.setBitmap(mCanvasBitmap);
            mAlphaOriginX = mCanvasBitmap.getWidth() * 0.4f;
            mAlphaOriginY = mCanvasBitmap.getHeight() * 0.4f;
        }
    }

    public void onPause() {
        if (mCanvasBitmap != null) {
            mCanvasBitmap.recycle();
            mCanvasBitmap = null;
        }
    }

    public void setStartAngle(int angle) {
        mStartAngle = angle;
    }

    public void setSweepAngle(int angle) {
        mSweepAngle = angle;
    }

    public void setStatusText(String text) {
        mStatusText = text;
    }

    public void setBitmap(Bitmap bitmap, Matrix m) {
        mBitmap = bitmap;
        mM = m;
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mWidth = getWidth();
        mHeight = getHeight();

        // Draw bitmaps according to the calculated panorama transformation.
        if (mBitmap != null) {
            mM.postTranslate(mAlphaOriginX, mAlphaOriginY);
            mCanvas.drawBitmap(mBitmap, mM, mPaint);

            Matrix mInverse = mM;
            mM.invert(mInverse);
            mInverse.postTranslate(mWidth / 2 - mBitmap.getWidth() / 2,
                    mHeight / 2 - mBitmap.getHeight() / 2);

            canvas.drawBitmap(mCanvasBitmap, mInverse, mPaint);

            RectF rect = new RectF(mWidth / 2 - 100, 3 * mHeight / 4,
                    mWidth / 2 + 100, 3 * mHeight / 4 + 200);
            canvas.drawText(mStatusText, mWidth / 2, mHeight / 2, mPaint);
            canvas.drawArc(rect, -90 + mStartAngle, mSweepAngle, true, mPaint);
            canvas.drawArc(rect, -90 - mStartAngle, mSweepAngle > 0 ? 2 : 0, true, mPaint);
        }
    }
}
