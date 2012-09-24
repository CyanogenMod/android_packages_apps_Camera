/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;

public class PreviewGestures
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "CAM_gestures";

    private static final long TIMEOUT_PIE = 200;
    private static final int MSG_PIE = 1;
    private static final int MODE_NONE = 0;
    private static final int MODE_PIE = 1;
    private static final int MODE_ZOOM = 2;
    private static final int MODE_ALL = 4;

    private CameraActivity mActivity;
    private RenderOverlay mOverlay;
    private PieRenderer mPie;
    private ZoomRenderer mZoom;
    private FocusOverlayManager mFocus;
    private MotionEvent mDown;
    private ScaleGestureDetector mScale;
    private int mMode;
    private int mSlop;
    private int mTapTimeout;
    private boolean mEnabled;
    private boolean mZoomOnly;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MSG_PIE) {
                mMode = MODE_PIE;
                openPie();
                cancelActivityTouchHandling(mDown);
            }
        }
    };

    public PreviewGestures(CameraActivity ctx, RenderOverlay overlay, ZoomRenderer zoom,
            PieRenderer pie, FocusOverlayManager focus) {
        mActivity = ctx;
        mOverlay = overlay;
        mPie = pie;
        mZoom = zoom;
        mFocus = focus;
        mMode = MODE_ALL;
        mScale = new ScaleGestureDetector(ctx, this);
        mSlop = (int) ctx.getResources().getDimension(R.dimen.pie_touch_slop);
        mTapTimeout = ViewConfiguration.getTapTimeout();
        mEnabled = true;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) {
            cancelPie();
        }
    }

    public void setZoomOnly(boolean zoom) {
        mZoomOnly = true;
    }

    public boolean dispatchTouch(MotionEvent m) {
        if (!mEnabled) {
            return mActivity.superDispatchTouchEvent(m);
        }
        if (MotionEvent.ACTION_DOWN == m.getActionMasked()) {
            mMode = MODE_ALL;
            mDown = MotionEvent.obtain(m);
            if (mPie != null && !mZoomOnly) {
                mHandler.sendEmptyMessageDelayed(MSG_PIE, TIMEOUT_PIE);
            }
            if (mZoom != null) {
                mScale.onTouchEvent(m);
            }
            // make sure this is ok
            return mActivity.superDispatchTouchEvent(m);
        } else if (mMode == MODE_NONE) {
            return mActivity.superDispatchTouchEvent(m);
        } else if (mMode == MODE_PIE) {
            return sendToPie(m);
        } else if (mMode == MODE_ZOOM) {
            return mScale.onTouchEvent(m);
        } else {
            if (MotionEvent.ACTION_POINTER_DOWN == m.getActionMasked()) {
                if (!mZoomOnly) {
                    cancelPie();
                }
            }
            // not zoom or pie mode and no timeout yet
            if (mZoom != null) {
                boolean res = mScale.onTouchEvent(m);
                if (mScale.isInProgress()) {
                    cancelPie();
                    cancelActivityTouchHandling(m);
                    return res;
                }
            }
            if (MotionEvent.ACTION_UP == m.getActionMasked()) {
                cancelPie();
                cancelActivityTouchHandling(m);
                // must have been tap to focus
                if (mFocus != null && !mZoomOnly
                        && (m.getEventTime() - mDown.getEventTime() < mTapTimeout)) {
                    mDown.offsetLocation(-mOverlay.getWindowPositionX(),
                            -mOverlay.getWindowPositionY());
                    mFocus.onSingleTapUp((int) mDown.getX(), (int) mDown.getY());
                    return true;
                } else {
                    return mActivity.superDispatchTouchEvent(m);
                }
            } else if (MotionEvent.ACTION_MOVE == m.getActionMasked()) {
                if ((Math.abs(m.getX() - mDown.getX()) > mSlop)
                        || Math.abs(m.getY() - mDown.getY()) > mSlop) {
                    // moved too far and no timeout yet, no focus or pie
                    cancelPie();
                    mMode = MODE_NONE;
                }
                return mActivity.superDispatchTouchEvent(m);
            }
            return false;
        }
    }

    public void cancelActivityTouchHandling(MotionEvent m) {
        MotionEvent c = MotionEvent.obtain(m);
        c.setAction(MotionEvent.ACTION_CANCEL);
        mActivity.superDispatchTouchEvent(c);
    }

    private void openPie() {
        mDown.offsetLocation(-mOverlay.getWindowPositionX(),
                -mOverlay.getWindowPositionY());
        mOverlay.directDispatchTouch(mDown, mPie);
    }

    private void cancelPie() {
        mHandler.removeMessages(MSG_PIE);
    }

    private boolean sendToPie(MotionEvent m) {
        m.offsetLocation(-mOverlay.getWindowPositionX(),
                -mOverlay.getWindowPositionY());
        return mOverlay.directDispatchTouch(m, mPie);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        return mZoom.onScale(detector);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mMode = MODE_ZOOM;
        return mZoom.onScaleBegin(detector);
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mZoom.onScaleEnd(detector);
    }
}