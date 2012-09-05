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

package com.android.camera.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;


public class CameraSwitcher extends HorizontalScrollView {

    private static final String TAG = "CAM_Switcher";

    private static final long FLING_DURATION = 250;

    private static final int MSG_SNAP = 1;
    private static final int MSG_SET_CAM = 2;

    public interface CameraSwitchListener {

        public void onScroll();

        public void onCameraSelected(int i);
    }

    private LinearLayout mContent;
    private GestureDetector mDetector;
    private Gestures mGestures;
    private CameraSwitchListener mListener;
    private int mCurrentIndex;
    private int mChildWidth;
    private int mOffset;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SNAP:
                snap();
                break;
            case MSG_SET_CAM:
                if (mListener != null) {
                    mListener.onCameraSelected(mCurrentIndex);
                }
                break;
            }
        }
    };

    public CameraSwitcher(Context context) {
        super(context);
        init(context);
    }

    public CameraSwitcher(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public CameraSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        setFillViewport(true);
        setHorizontalScrollBarEnabled(false);
        mContent = new Content(context);
        addView(mContent, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        mGestures = new Gestures();
        mDetector = new GestureDetector(context, mGestures);
        mDetector.setSlopScale(context, 1.5f);
        // initialize to non-zero
        mChildWidth = 100;
        mOffset = 100;
    }

    public void setCurrentModule(int i) {
        Log.i(TAG, "set current camera "+i);
        mCurrentIndex = i;
        reposition();

    }

    public void setSwitchListener(CameraSwitchListener l) {
        mListener = l;
    }

    public boolean onTouchEvent(MotionEvent evt) {
        return mDetector.onTouchEvent(evt, getScrollX(), 0);
    }

    @Override
    public void scrollBy(int dx, int dy) {
        if (mListener != null) {
            mListener.onScroll();
        }
        super.scrollBy(dx,  dy);
    }

    public void add(View view, ViewGroup.LayoutParams lp) {
        mContent.addView(view, lp);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // child width
        int w = r - l;
        mChildWidth = w * 2 / 5;
        mOffset = (w - mChildWidth) / 2;
        int n = mContent.getChildCount();
        mContent.layout(0, 0, mChildWidth * n + 2 * mOffset, (b - t));
        int cl = mOffset;
        int h = b - t;
        for (int i = 0; i < n; i++) {
            View c = mContent.getChildAt(i);
            c.layout(cl, 0, cl + mChildWidth, h);
            cl += mChildWidth;
        }
        reposition();
    }

    private void reposition() {
        scrollTo(mCurrentIndex * mChildWidth, 0);
    }

    private void snap() {
        int cx = getScrollX() + getWidth() / 2 - mOffset;
        if (mChildWidth != 0) {
            int pos = cx / mChildWidth;
            int scrollpos = pos * mChildWidth;
            smoothScrollTo(scrollpos, 0);
            mCurrentIndex = pos;
            mHandler.sendEmptyMessageDelayed(MSG_SET_CAM, 100);
        }
    }

    private class Content extends LinearLayout {

        public Content(Context context) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
        }

    }

    class Gestures extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            fling(- (int) velocityX);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SNAP), FLING_DURATION);
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                float distanceX, float distanceY) {
            scrollBy((int) distanceX, 0);
            return true;
        }

        @Override
        public boolean onScrollUp(MotionEvent e) {
            snap();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return super.onSingleTapUp(e);
        }

    }

}
