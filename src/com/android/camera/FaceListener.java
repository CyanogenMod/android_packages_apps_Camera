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
import android.graphics.Rect;
import android.hardware.Camera.Face;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

class FaceListener implements android.hardware.Camera.FaceDetectionListener {
    private final String TAG = "FaceListener";
    private final boolean LOGV = true;
    private final int MAX_NUM_FACES = 10; // Show 10 faces at most.
    private final Context mContext;
    private final ViewGroup mFrame;
    private int mDisplayOrientation;
    private View mFaces[] = new View[MAX_NUM_FACES];

    public FaceListener(Context context, ViewGroup frame, int orientation) {
        mContext = context;
        mFrame = frame;
        setDisplayOrientation(orientation);
    }

    @Override
    public void onFaceDetection(Face[] faces, android.hardware.Camera camera) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);

        // Rotate the coordinates if necessary.
        if (mDisplayOrientation != 0) rotateFaces(faces);
        showFaces(faces);
    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
        if (LOGV) Log.v(TAG, "mDisplayOrientation=" + orientation);
    }

    private void rotateFaces(Face[] faces) {
        int tmp;
        for (Face face: faces) {
            Rect rect = face.rect;
            if (LOGV) dumpRect(rect, "Original rect");
            if (mDisplayOrientation== 90) {
                tmp = rect.left;
                rect.left = rect.top;  // x' = y
                rect.top = -tmp;       // y' = -x
                tmp = rect.right;
                rect.right = rect.bottom;
                rect.bottom = -tmp;
            } else if (mDisplayOrientation == 180) {
                rect.left *= -1;       // x' = -x
                rect.top *= -1;        // y' = -y
                rect.right *= -1;
                rect.bottom *= -1;
            } else if (mDisplayOrientation == 270) {
                tmp = rect.left;
                rect.left = -rect.top; // x' = -y
                rect.top = tmp;        // y' = x
                tmp = rect.right;
                rect.right = -rect.bottom;
                rect.bottom = tmp;
            }
            if (LOGV) dumpRect(rect, "Rotated rect");
        }
    }

    private void showFaces(Face[] faces) {
        // The range of the coordinates from the driver is -1000 to 1000.
        // So the maximum length of the width or height is 2000. We need to
        // convert them to UI layout size later.
        double widthRatio = mFrame.getWidth() / 2000.0;
        double heightRatio = mFrame.getHeight() / 2000.0;
        for (int i = 0; i < MAX_NUM_FACES; i++) {
            if (i < faces.length) {
                // Inflate the view if it's not done yet.
                if (mFaces[i] == null) {
                    LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                            Context.LAYOUT_INFLATER_SERVICE);
                    mFaces[i] = inflater.inflate(R.layout.face, null);
                    mFrame.addView(mFaces[i]);
                }

                // Set width and height.
                Rect rect = faces[i].rect;
                RelativeLayout.LayoutParams p =
                        (RelativeLayout.LayoutParams) mFaces[i].getLayoutParams();
                p.width = (int) (rect.width() * widthRatio);
                p.height = (int) (rect.height() * heightRatio);

                // Set margins. Add 1000 so the range is 0 to 2000.
                int left = (int) ((rect.left + 1000) * widthRatio);
                int top = (int) ((rect.top + 1000) * heightRatio);
                p.setMargins(left, top, 0, 0);
                mFaces[i].setLayoutParams(p);
                if (LOGV) {
                    Log.v(TAG, "Face w="+p.width+".h="+p.height+".margin left="+left+".top="+top);
                }

                mFaces[i].setVisibility(View.VISIBLE);
                mFaces[i].requestLayout();
            } else {
                if (mFaces[i] != null) mFaces[i].setVisibility(View.GONE);
            }
        }
    }

    private void dumpRect(Rect rect, String msg) {
        Log.v(TAG, msg + "=(" + rect.left + "," + rect.top
                + "," + rect.right + "," + rect.bottom + ")");
    }
}
