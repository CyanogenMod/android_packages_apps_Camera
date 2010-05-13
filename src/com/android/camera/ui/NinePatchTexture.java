/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;

import javax.microedition.khronos.opengles.GL11;

public class NinePatchTexture extends FrameTexture {

    private MyTexture mDelegate;

    private NinePatchDrawable mNinePatch;

    private final Context mContext;
    private final int mResId;

    private int mLastWidth = -1;
    private int mLastHeight = -1;

    private final Rect mPaddings = new Rect();

    public NinePatchTexture(Context context, int resId) {
        this.mContext = context;
        this.mResId = resId;
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
    }

    @Override
    protected boolean bind(GLRootView root, GL11 gl) {
        if (mLastWidth != mWidth || mLastHeight != mHeight) {
            if (mDelegate != null) mDelegate.deleteFromGL();
            mDelegate = new MyTexture(mWidth, mHeight);
            mLastWidth = mWidth;
            mLastHeight = mHeight;
        }
        return mDelegate.bind(root, gl);
    }

    @Override
    public void getTextureCoords(float coord[], int offset) {
        mDelegate.getTextureCoords(coord, offset);
    }

    protected NinePatchDrawable getNinePatch() {
        if (mNinePatch == null) {
            mNinePatch = (NinePatchDrawable)
                    mContext.getResources().getDrawable(mResId);
            mNinePatch.getPadding(mPaddings);
        }
        return mNinePatch;
    }

    private class MyTexture extends CanvasTexture {

        public MyTexture(int width, int height) {
            super(width, height);
        }

        @Override
        protected void onDraw (Canvas canvas, Bitmap backing) {
            NinePatchDrawable npd = getNinePatch();
            npd.setBounds(0, 0, mWidth, mHeight);
            npd.draw(canvas);
        }
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        mDelegate.freeBitmap(bitmap);
    }

    @Override
    protected Bitmap getBitmap() {
        return mDelegate.getBitmap();
    }

    public int getIntrinsicWidth() {
        return getNinePatch().getIntrinsicWidth();
    }

    public int getIntrinsicHeight() {
        return getNinePatch().getIntrinsicHeight();
    }

    @Override
    public Rect getPaddings() {
        // get the paddings from nine patch
        if (mNinePatch == null) getNinePatch();
        return mPaddings;
    }
}
