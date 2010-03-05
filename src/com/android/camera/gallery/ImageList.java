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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Images.Media;

/**
 * Represents an ordered collection of Image objects. Provides an API to add
 * and remove an image.
 */
public class ImageList extends BaseImageList implements IImageList {

    @SuppressWarnings("unused")
    private static final String TAG = "ImageList";

    private static final String[] ACCEPTABLE_IMAGE_TYPES =
            new String[] { "image/jpeg", "image/png", "image/gif" };

    /**
     * ImageList constructor.
     */
    public ImageList(ContentResolver resolver, Uri imageUri,
            int sort, String bucketId) {
        super(resolver, imageUri, sort, bucketId);
    }

    private static final String WHERE_CLAUSE =
            "(" + Media.MIME_TYPE + " in (?, ?, ?))";
    private static final String WHERE_CLAUSE_WITH_BUCKET_ID =
            WHERE_CLAUSE + " AND " + Media.BUCKET_ID + " = ?";

    protected String whereClause() {
        return mBucketId == null ? WHERE_CLAUSE : WHERE_CLAUSE_WITH_BUCKET_ID;
    }

    protected String[] whereClauseArgs() {
        // TODO: Since mBucketId won't change, we should keep the array.
        if (mBucketId != null) {
            int count = ACCEPTABLE_IMAGE_TYPES.length;
            String[] result = new String[count + 1];
            System.arraycopy(ACCEPTABLE_IMAGE_TYPES, 0, result, 0, count);
            result[count] = mBucketId;
            return result;
        }
        return ACCEPTABLE_IMAGE_TYPES;
    }

    @Override
    protected Cursor createCursor() {
        Cursor c = Media.query(
                mContentResolver, mBaseUri, IMAGE_PROJECTION,
                whereClause(), whereClauseArgs(), sortOrder());
        return c;
    }

    static final String[] IMAGE_PROJECTION = new String[] {
            Media._ID,
            Media.DATE_TAKEN,
            Media.MINI_THUMB_MAGIC,
            Media.ORIENTATION,
            Media.DATE_MODIFIED};

    private static final int INDEX_ID = 0;
    private static final int INDEX_DATE_TAKEN = 1;
    private static final int INDEX_MINI_THUMB_MAGIC = 2;
    private static final int INDEX_ORIENTATION = 3;
    private static final int INDEX_DATE_MODIFIED = 4;

    @Override
    protected BaseImage loadImageFromCursor(Cursor cursor) {
        long id = cursor.getLong(INDEX_ID);
        long dateTaken = cursor.getLong(INDEX_DATE_TAKEN);
        if (dateTaken == 0) {
            dateTaken = cursor.getLong(INDEX_DATE_MODIFIED) * 1000;
        }
        long miniThumbMagic = cursor.getLong(INDEX_MINI_THUMB_MAGIC);
        int orientation = cursor.getInt(INDEX_ORIENTATION);
        return new Image(mContentResolver, id,
                contentUri(id), miniThumbMagic, dateTaken,
                orientation);
    }
}
