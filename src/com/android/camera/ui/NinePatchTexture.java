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
import android.graphics.BitmapFactory;
import android.graphics.Rect;

class NinePatchTexture extends ResourceTexture {
    private NinePatchChunk mChunk;

    public NinePatchTexture(Context context, int resId) {
        super(context, resId);
    }

    @Override
    protected Bitmap getBitmap() {
        if (mBitmap != null) return mBitmap;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
        mBitmap = bitmap;
        setSize(bitmap.getWidth(), bitmap.getHeight());
        mChunk = NinePatchChunk.deserialize(bitmap.getNinePatchChunk());
        if (mChunk == null) {
            throw new RuntimeException("invalid nine-patch image: " + mResId);
        }
        return bitmap;
    }

    public Rect getPaddings() {
        // get the paddings from nine patch
        if (mChunk == null) getBitmap();
        return mChunk.mPaddings;
    }

    public NinePatchChunk getNinePatchChunk() {
        if (mChunk == null) getBitmap();
        return mChunk;
    }

    @Override
    public void draw(GLRootView root, int x, int y, int w, int h) {
        root.drawNinePatch(this, x, y, w, h);
    }
}
