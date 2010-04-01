/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.camera.gallery;

import com.android.camera.BitmapManager;
import com.android.camera.Util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.util.Log;

/**
 * Represents a particular image and provides access to the underlying bitmap
 * and two thumbnail bitmaps as well as other information such as the id, and
 * the path to the actual image data.
 */
public abstract class BaseImage implements IImage {
    private static final String TAG = "BaseImage";
    protected ContentResolver mContentResolver;

    // Database field
    protected Uri mUri;
    protected long mId;
    private final long mDateTaken;

    protected BaseImage(ContentResolver cr,
            long id, Uri uri, long miniThumbMagic,
            long dateTaken) {
        mContentResolver = cr;
        mId = id;
        mUri = uri;
        mDateTaken = dateTaken;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof Image)) return false;
        return mUri.equals(((Image) other).mUri);
    }

    @Override
    public int hashCode() {
        return mUri.hashCode();
    }

    public Uri fullSizeImageUri() {
        return mUri;
    }

    public long getDateTaken() {
        return mDateTaken;
    }

    public int getDegreesRotated() {
        return 0;
    }

    public Bitmap miniThumbBitmap() {
        Bitmap b = null;
        try {
            long id = mId;
            b = BitmapManager.instance().getThumbnail(mContentResolver, id,
                    Images.Thumbnails.MICRO_KIND, null, false);
        } catch (Throwable ex) {
            Log.e(TAG, "miniThumbBitmap got exception", ex);
            return null;
        }
        if (b != null) {
            b = Util.rotate(b, getDegreesRotated());
        }
        return b;
    }

    @Override
    public String toString() {
        return mUri.toString();
    }
}
