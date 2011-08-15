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

package com.android.camera.ui;

import com.android.camera.R;
import com.android.camera.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.Face;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class FaceView extends View {
    private final String TAG = "FaceView";
    private final boolean LOGV = false;
    private int mDisplayOrientation;
    private boolean mMirror;
    private Matrix mMatrix = new Matrix();
    private RectF mRect = new RectF();
    private Face[] mFaces;
    private Drawable mFaceRect;

    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFaceRect = getResources().getDrawable(R.drawable.focus_focused);
    }

    public void setFaces(Face[] faces) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);
        mFaces = faces;
        invalidate();
    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
        if (LOGV) Log.v(TAG, "mDisplayOrientation=" + orientation);
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
        if (LOGV) Log.v(TAG, "mMirror=" + mirror);
    }

    public boolean faceExists() {
        return (mFaces != null && mFaces.length > 0);
    }

    public void clearFaces() {
        mFaces = null;
        invalidate();
    }

    private void dumpRect(RectF rect, String msg) {
        Log.v(TAG, msg + "=(" + rect.left + "," + rect.top
                + "," + rect.right + "," + rect.bottom + ")");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mFaces != null && mFaces.length > 0) {
            // Prepare the matrix.
            Util.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, getWidth(),
                    getHeight());

            for (int i = 0; i < mFaces.length; i++) {
                // Transform the coordinates.
                mRect.set(mFaces[i].rect);
                if (LOGV) dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                if (LOGV) dumpRect(mRect, "Transformed rect");

                mFaceRect.setBounds(Math.round(mRect.left), Math.round(mRect.top),
                        Math.round(mRect.right), Math.round(mRect.bottom));
                mFaceRect.draw(canvas);
            }
        }
        super.onDraw(canvas);
    }
}
