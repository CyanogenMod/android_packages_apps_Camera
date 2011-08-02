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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

class CaptureView extends View {
    private static final String TAG = "CaptureView";
    private String mStatusText = "";
    private int mStartAngle = 0;
    private int mSweepAngle = 0;
    private int mWidth;
    private int mHeight;
    private final Paint mPaint = new Paint();

    public CaptureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.RED);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(40);
        mPaint.setTypeface(Typeface.create((Typeface) null, Typeface.BOLD));
        mPaint.setTextAlign(Align.CENTER);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Log.v(TAG, "onSizeChanged: W = " + w + ", H = " + h);
    }

    public void onResume() {
    }

    public void onPause() {
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

    @Override
    protected void onDraw(Canvas canvas) {
        mWidth = getWidth();
        mHeight = getHeight();

        RectF rect = new RectF(mWidth / 2 - 100, 3 * mHeight / 4,
                mWidth / 2 + 100, 3 * mHeight / 4 + 200);
        canvas.drawText(mStatusText, mWidth / 2, mHeight / 2, mPaint);
        canvas.drawArc(rect, -90 + mStartAngle, mSweepAngle, true, mPaint);
        canvas.drawArc(rect, -90 - mStartAngle, mSweepAngle > 0 ? 2 : 0, true, mPaint);
    }
}
