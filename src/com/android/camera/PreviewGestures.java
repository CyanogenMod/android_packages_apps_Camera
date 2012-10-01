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
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;

import java.util.ArrayList;
import java.util.List;

public class PreviewGestures
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final String TAG = "CAM_gestures";

    private static final long TIMEOUT_PIE = 200;
    private static final int MSG_PIE = 1;
    private static final int MODE_NONE = 0;
    private static final int MODE_PIE = 1;
    private static final int MODE_ZOOM = 2;
    private static final int MODE_MODULE = 3;
    private static final int MODE_ALL = 4;

    private CameraActivity mActivity;
    private CameraModule mModule;
    private RenderOverlay mOverlay;
    private PieRenderer mPie;
    private ZoomRenderer mZoom;
    private MotionEvent mDown;
    private ScaleGestureDetector mScale;
    private List<View> mReceivers;
    private int mMode;
    private int mSlop;
    private int mTapTimeout;
    private boolean mEnabled;
    private boolean mZoomOnly;
    private int mOrientation;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MSG_PIE) {
                mMode = MODE_PIE;
                openPie();
                cancelActivityTouchHandling(mDown);
            }
        }
    };

    public PreviewGestures(CameraActivity ctx, CameraModule module,
            ZoomRenderer zoom, PieRenderer pie) {
        mActivity = ctx;
        mModule = module;
        mPie = pie;
        mZoom = zoom;
        mMode = MODE_ALL;
        mScale = new ScaleGestureDetector(ctx, this);
        mSlop = (int) ctx.getResources().getDimension(R.dimen.pie_touch_slop);
        mTapTimeout = ViewConfiguration.getTapTimeout();
        mEnabled = true;
    }

    public void setRenderOverlay(RenderOverlay overlay) {
        mOverlay = overlay;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) {
            cancelPie();
        }
    }

    public void setZoomOnly(boolean zoom) {
        mZoomOnly = zoom;
    }

    public void addTouchReceiver(View v) {
        if (mReceivers == null) {
            mReceivers = new ArrayList<View>();
        }
        mReceivers.add(v);
    }

    public void clearTouchReceivers() {
        if (mReceivers != null) {
            mReceivers.clear();
        }
    }

    public boolean dispatchTouch(MotionEvent m) {
        if (!mEnabled) {
            return mActivity.superDispatchTouchEvent(m);
        }
        if (MotionEvent.ACTION_DOWN == m.getActionMasked()) {
            if (checkReceivers(m)) {
                mMode = MODE_MODULE;
                return mActivity.superDispatchTouchEvent(m);
            } else {
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
            }
        } else if (mMode == MODE_NONE) {
            return false;
        } else if (mMode == MODE_PIE) {
            return sendToPie(m);
        } else if (mMode == MODE_ZOOM) {
            return mScale.onTouchEvent(m);
        } else if (mMode == MODE_MODULE) {
            return mActivity.superDispatchTouchEvent(m);
        } else {
            // didn't receive down event previously;
            // assume module wasn't initialzed and ignore this event.
            if (mDown == null) {
                return true;
            }
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
                // must have been tap
                if (m.getEventTime() - mDown.getEventTime() < mTapTimeout) {
                    mModule.onSingleTapUp(null,
                            (int) mDown.getX() - mOverlay.getWindowPositionX(),
                            (int) mDown.getY() - mOverlay.getWindowPositionY());
                    return true;
                } else {
                    return mActivity.superDispatchTouchEvent(m);
                }
            } else if (MotionEvent.ACTION_MOVE == m.getActionMasked()) {
                if ((Math.abs(m.getX() - mDown.getX()) > mSlop)
                        || Math.abs(m.getY() - mDown.getY()) > mSlop) {
                    // moved too far and no timeout yet, no focus or pie
                    cancelPie();
                    if (isSwipe(m)) {
                        mMode = MODE_MODULE;
                        return mActivity.superDispatchTouchEvent(m);
                    } else {
                        mMode = MODE_NONE;
                        cancelActivityTouchHandling(m);
                    }
                }
            }
            return false;
        }
    }

    private boolean checkReceivers(MotionEvent m) {
        if (mReceivers != null) {
            for (View receiver : mReceivers) {
                if (isInside(m, receiver)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSwipe(MotionEvent m) {
        float dx = 0;
        switch (mOrientation) {
        case 0:
            dx = m.getX() - mDown.getX();
          return (dx < 0 && Math.abs(m.getY() - mDown.getY()) / -dx < 0.6f);
        case 90:
            dx = - (m.getY() - mDown.getY());
            return (dx < 0 && Math.abs(m.getX() - mDown.getX()) / -dx < 0.6f);
        case 180:
            dx = -(m.getX() - mDown.getX());
            return (dx < 0 && Math.abs(m.getY() - mDown.getY()) / -dx < 0.6f);
        case 270:
            dx = m.getY() - mDown.getY();
            return (dx < 0 && Math.abs(m.getX() - mDown.getX()) / -dx < 0.6f);
        }
        return false;
    }

    private boolean isInside(MotionEvent evt, View v) {
        return (v.getVisibility() == View.VISIBLE
                && evt.getX() >= v.getLeft() && evt.getX() < v.getRight()
                && evt.getY() >= v.getTop() && evt.getY() < v.getBottom());
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
