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

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.util.Log;

/**
 * Represents a particular video and provides access to the underlying data and
 * two thumbnail bitmaps as well as other information such as the id, and the
 * path to the actual video data.
 */
public class VideoObject extends BaseImage implements IImage {
    private static final String TAG = "VideoObject";
    /**
     * Constructor.
     *
     * @param id        the image id of the image
     * @param cr        the content resolver
     */
    protected VideoObject(ContentResolver cr,
            long id, Uri uri, long miniThumbMagic,
            long dateTaken) {
        super(cr, id, uri, miniThumbMagic,
                dateTaken);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof VideoObject)) return false;
        return fullSizeImageUri().equals(
                ((VideoObject) other).fullSizeImageUri());
    }

    @Override
    public int hashCode() {
        return fullSizeImageUri().toString().hashCode();
    }

    @Override
    public Bitmap miniThumbBitmap() {
        try {
            long id = mId;
            return BitmapManager.instance().getThumbnail(mContentResolver,
                    id, Images.Thumbnails.MICRO_KIND, null, true);
        } catch (Throwable ex) {
            Log.e(TAG, "miniThumbBitmap got exception", ex);
            return null;
        }
    }

    @Override
    public String toString() {
        return new StringBuilder("VideoObject").append(mId).toString();
    }
}
