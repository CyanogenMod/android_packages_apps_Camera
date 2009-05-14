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
import com.android.camera.Util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import java.util.HashMap;

/**
 * Represents an ordered collection of Image objects. Provides an API to add
 * and remove an image.
 */
public class ImageList extends BaseImageList implements IImageList {

    private static final String TAG = "ImageList";

    private static final String[] ACCEPTABLE_IMAGE_TYPES =
            new String[] { "image/jpeg", "image/png", "image/gif" };

    public HashMap<String, String> getBucketIds() {
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("distinct", "true").build();
        Cursor c = Images.Media.query(
                mContentResolver, uri,
                new String[] {
                    ImageColumns.BUCKET_DISPLAY_NAME,
                    ImageColumns.BUCKET_ID},
                whereClause(), whereClauseArgs(), sortOrder());

        HashMap<String, String> hash = new HashMap<String, String>();
        if (c != null && c.moveToFirst()) {
            do {
                hash.put(c.getString(1), c.getString(0));
            } while (c.moveToNext());
        }
        return hash;
    }
    /**
     * ImageList constructor.
     * @param cr    ContentResolver
     */
    public ImageList(ContentResolver cr, Uri imageUri,
            Uri thumbUri, int sort, String bucketId) {
        super(cr, imageUri, sort, bucketId);
        mThumbUri = thumbUri;

        mCursor = createCursor();
        if (mCursor == null) {
            Log.e(TAG, "unable to create image cursor for " + mBaseUri);
            throw new UnsupportedOperationException();
        }
    }

    private static final String WHERE_CLAUSE =
            "(" + Images.Media.MIME_TYPE + " in (?, ?, ?))";

    protected String whereClause() {
        if (mBucketId != null) {
            return WHERE_CLAUSE + " and " + Images.Media.BUCKET_ID + " = '"
                    + mBucketId + "'";
        } else {
            return WHERE_CLAUSE;
        }
    }

    protected String[] whereClauseArgs() {
        return ACCEPTABLE_IMAGE_TYPES;
    }

    protected Cursor createCursor() {
        Cursor c = Images.Media.query(
                mContentResolver, mBaseUri, IMAGE_PROJECTION,
                whereClause(), whereClauseArgs(), sortOrder());
        return c;
    }

    static final String[] IMAGE_PROJECTION = new String[] {
            BaseColumns._ID,
            MediaColumns.DATA,
            ImageColumns.DATE_TAKEN,
            ImageColumns.MINI_THUMB_MAGIC,
            ImageColumns.ORIENTATION,
            ImageColumns.MIME_TYPE};

    private static final int INDEX_ID
            = Util.indexOf(IMAGE_PROJECTION, BaseColumns._ID);
    private static final int INDEX_DATA =
            Util.indexOf(IMAGE_PROJECTION, MediaColumns.DATA);
    private static final int INDEX_MIME_TYPE =
            Util.indexOf(IMAGE_PROJECTION, MediaColumns.MIME_TYPE);
    private static final int INDEX_DATE_TAKEN =
            Util.indexOf(IMAGE_PROJECTION, ImageColumns.DATE_TAKEN);
    private static final int INDEX_MINI_THUMB_MAGIC =
            Util.indexOf(IMAGE_PROJECTION, ImageColumns.MINI_THUMB_MAGIC);
    private static final int INDEX_ORIENTATION =
            Util.indexOf(IMAGE_PROJECTION, ImageColumns.ORIENTATION);

    @Override
    protected int indexId() {
        return INDEX_ID;
    }

    @Override
    protected int indexData() {
        return INDEX_DATA;
    }

    @Override
    protected int indexMimeType() {
        return INDEX_MIME_TYPE;
    }

    @Override
    protected int indexDateTaken() {
        return INDEX_DATE_TAKEN;
    }

    @Override
    protected int indexMiniThumbMagic() {
        return INDEX_MINI_THUMB_MAGIC;
    }

    @Override
    protected int indexOrientation() {
        return INDEX_ORIENTATION;
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
    protected IImage make(long id, long miniThumbMagic, ContentResolver cr,
            IImageList list, int index, int rotation) {
        return new Image(id, miniThumbMagic, mContentResolver, this, index,
                rotation);
    }

    private String sortOrder() {
        // add id to the end so that we don't ever get random sorting
        // which could happen, I suppose, if the first two values were
        // duplicated
        String ascending =
                mSort == ImageManager.SORT_ASCENDING ? " ASC" : " DESC";
        return Images.Media.DATE_TAKEN + ascending + "," + Images.Media._ID
                + ascending;
    }
}
