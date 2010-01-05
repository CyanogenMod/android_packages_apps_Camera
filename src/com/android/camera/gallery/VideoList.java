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
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video.Media;

/**
 * A collection of all the <code>VideoObject</code> in gallery.
 */
public class VideoList extends BaseImageList {

    @SuppressWarnings("unused")
    private static final String TAG = "BaseImageList";

    private static final String[] VIDEO_PROJECTION = new String[] {
            Media._ID,
            Media.DATA,
            Media.DATE_TAKEN,
            Media.TITLE,
            Media.MINI_THUMB_MAGIC,
            Media.MIME_TYPE,
            Media.DATE_MODIFIED};

    private static final int INDEX_ID = 0;
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_DATE_TAKEN = 2;
    private static final int INDEX_TITLE = 3;
    private static final int INDEX_MIMI_THUMB_MAGIC = 4;
    private static final int INDEX_MIME_TYPE = 5;
    private static final int INDEX_DATE_MODIFIED = 6;

    @Override
    protected long getImageId(Cursor cursor) {
        return cursor.getLong(INDEX_ID);
    }

    @Override
    protected BaseImage loadImageFromCursor(Cursor cursor) {
        long id = cursor.getLong(INDEX_ID);
        String dataPath = cursor.getString(INDEX_DATA_PATH);
        long dateTaken = cursor.getLong(INDEX_DATE_TAKEN);
        if (dateTaken == 0) {
            dateTaken = cursor.getLong(INDEX_DATE_MODIFIED) * 1000;
        }
        long miniThumbMagic = cursor.getLong(INDEX_MIMI_THUMB_MAGIC);
        String title = cursor.getString(INDEX_TITLE);
        String mimeType = cursor.getString(INDEX_MIME_TYPE);
        if (title == null || title.length() == 0) {
            title = dataPath;
        }
        return new VideoObject(this, mContentResolver,
                id, cursor.getPosition(), contentUri(id), dataPath,
                miniThumbMagic, mimeType, dateTaken, title);
    }

    public VideoList(ContentResolver resolver, Uri uri, int sort,
            String bucketId) {
        super(resolver, uri, sort, bucketId);
    }

    protected String whereClause() {
        return mBucketId != null
                ? Images.Media.BUCKET_ID + " = '" + mBucketId + "'"
                : null;
    }

    protected String[] whereClauseArgs() {
        return null;
    }

    @Override
    protected Cursor createCursor() {
        Cursor c = Images.Media.query(
                mContentResolver, mBaseUri, VIDEO_PROJECTION,
                whereClause(), whereClauseArgs(), sortOrder());
        return c;
    }
}
