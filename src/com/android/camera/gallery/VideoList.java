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
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Config;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;

/**
 * A collection of all the <code>VideoObject</code> in gallery.
 */
public class VideoList extends BaseImageList implements IImageList {
    private static final String TAG = "BaseImageList";
    private static final boolean VERBOSE = false;

    private static final String[] sProjection = new String[] {
            Video.Media._ID,
            Video.Media.DATA,
            Video.Media.DATE_TAKEN,
            Video.Media.TITLE,
            Video.Media.DISPLAY_NAME,
            Video.Media.TAGS,
            Video.Media.CATEGORY,
            Video.Media.LANGUAGE,
            Video.Media.MINI_THUMB_MAGIC,
            Video.Media.MIME_TYPE};

    static final int INDEX_ID = indexOf(Video.Media._ID);
    static final int INDEX_DATA = indexOf(Video.Media.DATA);
    static final int INDEX_DATE_TAKEN = indexOf(Video.Media.DATE_TAKEN);
    static final int INDEX_TITLE = indexOf(Video.Media.TITLE);
    static final int INDEX_DISPLAY_NAME =
            indexOf(Video.Media.DISPLAY_NAME);
    static final int INDEX_MIME_TYPE = indexOf(Video.Media.MIME_TYPE);
    static final int INDEX_TAGS = indexOf(Video.Media.TAGS);
    static final int INDEX_CATEGORY = indexOf(Video.Media.CATEGORY);
    static final int INDEX_LANGUAGE = indexOf(Video.Media.LANGUAGE);
    static final int INDEX_MINI_THUMB_MAGIC =
            indexOf(Video.Media.MINI_THUMB_MAGIC);
    static final int INDEX_THUMB_ID = indexOf(BaseColumns._ID);

    private static int indexOf(String field) {
        return Util.indexOf(sProjection, field);
    }

    public VideoList(Context ctx, ContentResolver cr, Uri uri, Uri thumbUri,
            int sort, String bucketId) {
        super(ctx, cr, uri, sort, bucketId);

        mCursor = createCursor();
        if (mCursor == null) {
            Log.e(TAG, "unable to create video cursor for " + mBaseUri);
            throw new UnsupportedOperationException();
        }

        if (mCursor == null) {
            throw new UnsupportedOperationException();
        }
        if (mCursor != null && mCursor.moveToFirst()) {
            int row = 0;
            do {
                long imageId = mCursor.getLong(indexId());
                long miniThumbMagic = mCursor.getLong(indexMiniThumbMagic());
                mCache.put(imageId, new VideoObject(imageId, miniThumbMagic,
                        mContentResolver, this, row++));
            } while (mCursor.moveToNext());
        }
    }

    public HashMap<String, String> getBucketIds() {
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("distinct", "true").build();
        Cursor c = Images.Media.query(
                mContentResolver, uri,
                new String[] {
                    VideoColumns.BUCKET_DISPLAY_NAME,
                    VideoColumns.BUCKET_ID
                },
                whereClause(), whereClauseArgs(), sortOrder());
        HashMap<String, String> hash = new HashMap<String, String>();
        if (c != null && c.moveToFirst()) {
            do {
                hash.put(c.getString(1), c.getString(0));
            } while (c.moveToNext());
        }
        return hash;
    }

    protected String whereClause() {
        if (mBucketId != null) {
            return Images.Media.BUCKET_ID + " = '" + mBucketId + "'";
        } else {
            return null;
        }
    }

    protected String[] whereClauseArgs() {
        return null;
    }

    @Override
    protected String thumbnailWhereClause() {
        return MINITHUMB_IS_NULL;
    }

    @Override
    protected String[] thumbnailWhereClauseArgs() {
        return null;
    }

    protected Cursor createCursor() {
        Cursor c = Images.Media.query(
                mContentResolver, mBaseUri, sProjection,
                whereClause(), whereClauseArgs(), sortOrder());
        return c;
    }

    @Override
    protected int indexOrientation() {
        return -1;
    }

    @Override
    protected int indexDateTaken() {
        return INDEX_DATE_TAKEN;
    }

    @Override
    protected int indexMimeType() {
        return INDEX_MIME_TYPE;
    }

    @Override
    protected int indexData() {
        return INDEX_DATA;
    }

    @Override
    protected int indexId() {
        return INDEX_ID;
    }

    @Override
    protected int indexMiniThumbMagic() {
        return INDEX_MINI_THUMB_MAGIC;
    }

    @Override
    protected int indexTitle() {
        return INDEX_TITLE;
    }

    @Override
    protected int indexDisplayName() {
        return -1;
    }

    @Override
    protected int indexThumbId() {
        return INDEX_THUMB_ID;
    }

    @Override
    protected IImage make(long id, long miniThumbMagic, ContentResolver cr,
            IImageList list, int index, int rotation) {
        return new VideoObject(id, miniThumbMagic, mContentResolver, this,
                index);
    }

    private String sortOrder() {
        return Video.Media.DATE_TAKEN +
                (mSort == ImageManager.SORT_ASCENDING ? " ASC " : " DESC");
    }
}