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
import com.android.camera.ImageManager;
import com.android.camera.Util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.HashMap;

/**
 * An implementation of interface <code>IImageList</code> which contains only
 * one image.
 */
public class SingleImageList extends BaseImageList implements IImageList {
    private static final String TAG = "SingleImageList";
    private static final boolean VERBOSE = false;

    private IImage mSingleImage;

    public SingleImageList(ContentResolver cr, Uri uri) {
        super(null, cr, uri, ImageManager.SORT_ASCENDING, null);
        mSingleImage = new UriImage(this, cr, uri);
    }

    public HashMap<String, String> getBucketIds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deactivate() {
        // nothing to do here
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public IImage getImageAt(int i) {
        return i == 0 ? mSingleImage : null;
    }

    @Override
    public IImage getImageForUri(Uri uri) {
        return uri.equals(mUri) ? mSingleImage : null;
    }

    @Override
    protected int indexOrientation() {
        return -1;
    }

    @Override
    protected int indexDateTaken() {
        return -1;
    }

    @Override
    protected int indexMimeType() {
        return -1;
    }

    @Override
    protected int indexId() {
        return -1;
    }

    @Override
    protected int indexData() {
        return -1;
    }

    @Override
    protected int indexMiniThumbId() {
        return -1;
    }

    @Override
    protected int indexTitle() {
        return -1;
    }

    @Override
    protected int indexDisplayName() {
        return -1;
    }

    @Override
    protected int indexThumbId() {
        return -1;
    }

    @Override
    protected Bitmap makeBitmap(int targetWidthHeight, Uri uri,
            ParcelFileDescriptor pfdInput, BitmapFactory.Options options) {
        Bitmap b = null;
        try {
            if (options == null) options = new BitmapFactory.Options();
            options.inSampleSize = 1;

            if (targetWidthHeight != -1) {
                options.inJustDecodeBounds = true;
                BitmapManager.instance().decodeFileDescriptor(
                        pfdInput.getFileDescriptor(), null, options);
                options.inSampleSize =
                        Util.computeSampleSize(options, targetWidthHeight);
                options.inJustDecodeBounds = false;
            }
            b = BitmapManager.instance().decodeFileDescriptor(
                    pfdInput.getFileDescriptor(), null, options);
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Got oom exception ", ex);
            return null;
        } finally {
            Util.closeSiliently(pfdInput);
        }
        return b;
    }
}
