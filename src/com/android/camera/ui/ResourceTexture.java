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

import com.android.camera.Util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

class ResourceTexture extends BitmapTexture {

    protected final Context mContext;
    protected final int mResId;
    protected Bitmap mBitmap;

    public ResourceTexture(Context context, int resId) {
        mContext = Util.checkNotNull(context);
        mResId = resId;
    }

    @Override
    protected Bitmap getBitmap() {
        if (mBitmap != null) return mBitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        mBitmap = BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
        setSize(mBitmap.getWidth(), mBitmap.getHeight());
        return mBitmap;
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        Util.Assert(bitmap == mBitmap);
        bitmap.recycle();
        mBitmap = null;
    }
}
