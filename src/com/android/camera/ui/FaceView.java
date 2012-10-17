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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.camera.CameraActivity;
import com.android.camera.CameraScreenNail;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.gallery3d.common.ApiHelper;

@TargetApi(ApiHelper.VERSION_CODES.ICE_CREAM_SANDWICH)
public class FaceView extends View implements FocusIndicator, Rotatable {
    private static final String TAG = "CAM FaceView";
    private final boolean LOGV = false;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mDisplayOrientation;
    // The orientation compensation for the face indicator to make it look
    // correctly in all device orientations. Ex: if the value is 90, the
    // indicator should be rotated 90 degrees counter-clockwise.
    private int mOrientation;
    private boolean mMirror;
    private boolean mPause;
    private Matrix mMatrix = new Matrix();
    private RectF mRect = new RectF();
    // As face detection can be flaky, we add a layer of filtering on top of it
    // to avoid rapid changes in state (eg, flickering between has faces and
    // not having faces)
    private Face[] mFaces;
    private Face[] mPendingFaces;
    private int mColor;
    private final int mFocusingColor;
    private final int mFocusedColor;
    private final int mFailColor;
    private Paint mPaint;
    private volatile boolean mBlocked;

    private static final int MSG_SWITCH_FACES = 1;
    private static final int SWITCH_DELAY = 70;
    private boolean mStateSwitchPending = false;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SWITCH_FACES:
                mStateSwitchPending = false;
                mFaces = mPendingFaces;
                invalidate();
                break;
            }
        }
    };

    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = getResources();
        mFocusingColor = res.getColor(R.color.face_detect_start);
        mFocusedColor = res.getColor(R.color.face_detect_success);
        mFailColor = res.getColor(R.color.face_detect_fail);
        mColor = mFocusingColor;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Style.STROKE);
        mPaint.setStrokeWidth(res.getDimension(R.dimen.face_circle_stroke));
    }

    public void setFaces(Face[] faces) {
        if (LOGV) Log.v(TAG, "Num of faces=" + faces.length);
        if (mPause) return;
        if (mFaces != null) {
            if ((faces.length > 0 && mFaces.length == 0)
                    || (faces.length == 0 && mFaces.length > 0)) {
                mPendingFaces = faces;
                if (!mStateSwitchPending) {
                    mStateSwitchPending = true;
                    mHandler.sendEmptyMessageDelayed(MSG_SWITCH_FACES, SWITCH_DELAY);
                }
                return;
            }
        }
        if (mStateSwitchPending) {
            mStateSwitchPending = false;
            mHandler.removeMessages(MSG_SWITCH_FACES);
        }
        mFaces = faces;
        invalidate();
    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
        if (LOGV) Log.v(TAG, "mDisplayOrientation=" + orientation);
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        invalidate();
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
        if (LOGV) Log.v(TAG, "mMirror=" + mirror);
    }

    public boolean faceExists() {
        return (mFaces != null && mFaces.length > 0);
    }

    @Override
    public void showStart() {
        mColor = mFocusingColor;
        invalidate();
    }

    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showSuccess(boolean timeout) {
        mColor = mFocusedColor;
        invalidate();
    }

    // Ignore the parameter. No autofocus animation for face detection.
    @Override
    public void showFail(boolean timeout) {
        mColor = mFailColor;
        invalidate();
    }

    @Override
    public void clear() {
        // Face indicator is displayed during preview. Do not clear the
        // drawable.
        mColor = mFocusingColor;
        mFaces = null;
        invalidate();
    }

    public void pause() {
        mPause = true;
    }

    public void resume() {
        mPause = false;
    }

    public void setBlockDraw(boolean block) {
        mBlocked = block;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mBlocked && (mFaces != null) && (mFaces.length > 0)) {
            final CameraScreenNail sn = ((CameraActivity) getContext()).getCameraScreenNail();
            int rw = sn.getUncroppedRenderWidth();
            int rh = sn.getUncroppedRenderHeight();
            // Prepare the matrix.
            if (((rh > rw) && ((mDisplayOrientation == 0) || (mDisplayOrientation == 180)))
                    || ((rw > rh) && ((mDisplayOrientation == 90) || (mDisplayOrientation == 270)))) {
                int temp = rw;
                rw = rh;
                rh = temp;
            }
            Util.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, rw, rh);
            int dx = (getWidth() - rw) / 2;
            int dy = (getHeight() - rh) / 2;

            // Focus indicator is directional. Rotate the matrix and the canvas
            // so it looks correctly in all orientations.
            canvas.save();
            mMatrix.postRotate(mOrientation); // postRotate is clockwise
            canvas.rotate(-mOrientation); // rotate is counter-clockwise (for canvas)
            for (int i = 0; i < mFaces.length; i++) {
                // Filter out false positives.
                if (mFaces[i].score < 50) continue;

                // Transform the coordinates.
                mRect.set(mFaces[i].rect);
                if (LOGV) Util.dumpRect(mRect, "Original rect");
                mMatrix.mapRect(mRect);
                if (LOGV) Util.dumpRect(mRect, "Transformed rect");
                mPaint.setColor(mColor);
                mRect.offset(dx, dy);
                canvas.drawOval(mRect, mPaint);
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }
}
