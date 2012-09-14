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
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RenderOverlay extends RotateLayout {

    private static final String TAG = "CAM_Overlay";

    interface Renderer {

        public boolean handlesTouch();
        public boolean onTouchEvent(MotionEvent evt);
        public void setOverlay(RenderOverlay overlay);
        public void layout(int left, int top, int right, int bottom);
        public void draw(Canvas canvas);

    }

    private RenderView mRenderView;
    private List<Renderer> mClients;

    // reverse list of touch clients
    private List<Renderer> mTouchClients;
    private int[] mPosition = new int[2];

    public RenderOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRenderView = new RenderView(context);
        addView(mRenderView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mClients = new ArrayList<Renderer>(10);
        mTouchClients = new ArrayList<Renderer>(10);
    }

    public void addRenderer(Renderer renderer) {
        mClients.add(renderer);
        renderer.setOverlay(this);
        if (renderer.handlesTouch()) {
            mTouchClients.add(0, renderer);
        }
    }

    public void addRenderer(int pos, Renderer renderer) {
        mClients.add(pos, renderer);
        renderer.setOverlay(this);
    }

    public void remove(Renderer renderer) {
        mClients.remove(renderer);
        renderer.setOverlay(null);
    }

    public int getClientSize() {
        return mClients.size();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        return false;
    }

    public boolean directDispatchTouch(MotionEvent m) {
        MotionEvent m1 = MotionEvent.obtain(m);
        m1.setLocation(m.getX() - mPosition[0], m.getY() - mPosition[1]);
        return super.dispatchTouchEvent(m1);
    }

    private void adjustPosition() {
        getLocationInWindow(mPosition);
    }

    public int getWindowPositionX() {
        return mPosition[0];
    }

    public int getWindowPositionY() {
        return mPosition[1];
    }

    private class RenderView extends View {

        public RenderView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent evt) {
            if (mTouchClients != null) {
                boolean res = false;
                for (Renderer client : mTouchClients) {
                    res |= client.onTouchEvent(evt);
                }
                return res;
            }
            return false;
        }

        @Override
        public void layout(int left, int top, int right, int bottom) {
            adjustPosition();
            super.layout(left,  top, right, bottom);
            if (mClients == null) return;
            for (Renderer renderer : mClients) {
                renderer.layout(left, top, right, bottom);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (mClients == null) return;
            for (Renderer renderer : mClients) {
                renderer.draw(canvas);
            }
            invalidate();
        }
    }

}
