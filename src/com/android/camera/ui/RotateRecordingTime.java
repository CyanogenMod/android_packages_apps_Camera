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

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.graphics.Canvas;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.camera.R;

// This is a rectangle that contains recording text view. Canvas is rotated
// before passing to recording text.
public class RotateRecordingTime extends FrameLayout {
    private static final String TAG = "RotateRecordingTime";
    private float mTextSize;
    private int mOrientation;

    public RotateRecordingTime(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        TextView v = (TextView) findViewById(R.id.recording_time);
        mTextSize = (float) v.getTextSize();
    }

    // degrees in counter-clockwise
    public void setOrientation(int degrees) {
        if (degrees % 90 == 0) {
            mOrientation = degrees % 360;
            if (mOrientation < 0) mOrientation += 360;
        } else {
            Log.e(TAG, "Invalid orientation=" + degrees);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        float width = (float) getWidth();
        float height = (float) getHeight();
        if (mOrientation == 0 || mOrientation == 180) {
            canvas.translate(0, height / 2 - mTextSize / 2);
        } else {
            canvas.translate(-width / 2 + mTextSize / 2, 0);
        }
        // Rotate the canvas in the opposite direction to compensate.
        canvas.rotate(-mOrientation, width / 2, height / 2);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}

