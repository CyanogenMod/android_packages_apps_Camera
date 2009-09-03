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

import com.android.camera.ImageManager;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;

/**
 * An implementation of interface <code>IImageList</code> which contains only
 * one image.
 */
// TODO: consider implements not extends
public class SingleImageList extends BaseImageList {

    @SuppressWarnings("unused")
    private static final String TAG = "BaseImageList";

    private IImage mSingleImage;

    public SingleImageList(Uri uri) {
        super(uri, ImageManager.SORT_ASCENDING, null);
    }

    @Override
    public void open(ContentResolver resolver) {
        mContentResolver = resolver;
        mSingleImage = new UriImage(this, resolver, mBaseUri);
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
    protected long getImageId(Cursor cursor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getImageIndex(IImage image) {
        return image == mSingleImage ? 0 : -1;
    }

    @Override
    public IImage getImageAt(int i) {
        return i == 0 ? mSingleImage : null;
    }

    @Override
    public boolean removeImage(IImage image) {
        return false;
    }

    @Override
    public IImage getImageForUri(Uri uri) {
        return uri.equals(mBaseUri) ? mSingleImage : null;
    }

    @Override
    protected Cursor createCursor() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected BaseImage loadImageFromCursor(Cursor cursor) {
        throw new UnsupportedOperationException();
    }
}
