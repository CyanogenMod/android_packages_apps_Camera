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

package com.android.camera;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

class FaceListener implements android.hardware.Camera.FaceDetectionListener {
    private final String TAG = "FaceListener";
    private final boolean LOGV = false;
    private final int MAX_NUM_FACES = 10; // Show 10 faces at most.
    private final Context mContext;
    private final ViewGroup mFrame;
    private int mDisplayOrientation;
    private boolean mMirror;
    private View mFaces[] = new View[MAX_NUM_FACES];
    private Matrix mMatrix = new Matrix();
    private RectF mRect = new RectF();

    public FaceListener(Context context, ViewGroup frame, int orientation) {
        mContext = context;
        mFrame = frame;
        setDisplayOrientation(orientation);
    }

    @Override
    public void onFaceDetection(Face[] faces, android.hardware.Camera camera) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);

        // Prepare the matrix.
        Util.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, mFrame.getWidth(),
                mFrame.getHeight());
        showFaces(faces);
    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
        if (LOGV) Log.v(TAG, "mDisplayOrientation=" + orientation);
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
        if (LOGV) Log.v(TAG, "mMirror=" + mirror);
    }

    private void showFaces(Face[] faces) {
        // The range of the coordinates from the driver is -1000 to 1000.
        // So the maximum length of the width or height is 2000. We need to
        // convert them to UI layout size later.
        for (int i = 0; i < MAX_NUM_FACES; i++) {
            if (i < faces.length) {
                // Inflate the view if it's not done yet.
                if (mFaces[i] == null) {
                    LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                            Context.LAYOUT_INFLATER_SERVICE);
                    mFaces[i] = inflater.inflate(R.layout.face, null);
                    mFrame.addView(mFaces[i]);
                }

                // Transform the coordinates.
                mRect.set(faces[i].rect);
                if (LOGV) dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                if (LOGV) dumpRect(mRect, "Transformed rect");

                // Set width, height, and margin.
                RelativeLayout.LayoutParams p =
                        (RelativeLayout.LayoutParams) mFaces[i].getLayoutParams();
                p.width = Math.round(mRect.width());
                p.height = Math.round(mRect.height());
                p.setMargins(Math.round(mRect.left), Math.round(mRect.top), 0, 0);
                mFaces[i].setLayoutParams(p);
                mFaces[i].setVisibility(View.VISIBLE);
                mFaces[i].requestLayout();
            } else {
                if (mFaces[i] != null) mFaces[i].setVisibility(View.GONE);
            }
        }
    }

    private void dumpRect(RectF rect, String msg) {
        Log.v(TAG, msg + "=(" + rect.left + "," + rect.top
                + "," + rect.right + "," + rect.bottom + ")");
    }
}
