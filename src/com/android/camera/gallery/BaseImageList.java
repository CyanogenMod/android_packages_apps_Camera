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
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * A collection of <code>BaseImage</code>s.
 */
public abstract class BaseImageList implements IImageList {
    private static final String TAG = "BaseImageList";
    private static final int CACHE_CAPACITY = 512;
    private final LruCache<Integer, BaseImage> mCache =
            new LruCache<Integer, BaseImage>(CACHE_CAPACITY);

    protected ContentResolver mContentResolver;
    protected int mSort;

    protected Uri mBaseUri;
    protected Cursor mCursor;
    protected String mBucketId;
    protected boolean mCursorDeactivated = false;

    public BaseImageList(ContentResolver resolver, Uri uri, int sort,
            String bucketId) {
        mSort = sort;
        mBaseUri = uri;
        mBucketId = bucketId;
        mContentResolver = resolver;
        mCursor = createCursor();

        if (mCursor == null) {
            Log.w(TAG, "createCursor returns null.");
        }

        // TODO: We need to clear the cache because we may "reopen" the image
        // list. After we implement the image list state, we can remove this
        // kind of usage.
        mCache.clear();
    }

    public void close() {
        try {
            invalidateCursor();
        } catch (IllegalStateException e) {
            // IllegalStateException may be thrown if the cursor is stale.
            Log.e(TAG, "Caught exception while deactivating cursor.", e);
        }
        mContentResolver = null;
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    // TODO: Change public to protected
    public Uri contentUri(long id) {
        // TODO: avoid using exception for most cases
        try {
            // does our uri already have an id (single image query)?
            // if so just return it
            long existingId = ContentUris.parseId(mBaseUri);
            if (existingId != id) Log.e(TAG, "id mismatch");
            return mBaseUri;
        } catch (NumberFormatException ex) {
            // otherwise tack on the id
            return ContentUris.withAppendedId(mBaseUri, id);
        }
    }

    public int getCount() {
        Cursor cursor = getCursor();
        if (cursor == null) return 0;
        synchronized (this) {
            return cursor.getCount();
        }
    }

    public boolean isEmpty() {
        return getCount() == 0;
    }

    private Cursor getCursor() {
        synchronized (this) {
            if (mCursor == null) return null;
            if (mCursorDeactivated) {
                mCursor.requery();
                mCursorDeactivated = false;
            }
            return mCursor;
        }
    }

    public IImage getImageAt(int i) {
        BaseImage result = mCache.get(i);
        if (result == null) {
            Cursor cursor = getCursor();
            if (cursor == null) return null;
            synchronized (this) {
                result = cursor.moveToPosition(i)
                        ? loadImageFromCursor(cursor)
                        : null;
                mCache.put(i, result);
            }
        }
        return result;
    }

    protected abstract Cursor createCursor();

    protected abstract BaseImage loadImageFromCursor(Cursor cursor);

    protected void invalidateCursor() {
        if (mCursor == null) return;
        mCursor.deactivate();
        mCursorDeactivated = true;
    }

    // This provides a default sorting order string for subclasses.
    // The list is first sorted by date, then by id. The order can be ascending
    // or descending, depending on the mSort variable.
    // The date is obtained from the "datetaken" column. But if it is null,
    // the "date_modified" column is used instead.
    protected String sortOrder() {
        String ascending =
                (mSort == ImageManager.SORT_ASCENDING)
                ? " ASC"
                : " DESC";

        // Use DATE_TAKEN if it's non-null, otherwise use DATE_MODIFIED.
        // DATE_TAKEN is in milliseconds, but DATE_MODIFIED is in seconds.
        String dateExpr =
                "case ifnull(datetaken,0)" +
                " when 0 then date_modified*1000" +
                " else datetaken" +
                " end";

        // Add id to the end so that we don't ever get random sorting
        // which could happen, I suppose, if the date values are the same.
        return dateExpr + ascending + ", _id" + ascending;
    }
}
