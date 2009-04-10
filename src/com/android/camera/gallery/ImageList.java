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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;

/**
 * Represents an ordered collection of Image objects. Provides an API to add
 * and remove an image.
 */
public class ImageList extends BaseImageList implements IImageList {

    private static final String TAG = "ImageList";
    private static final boolean VERBOSE = false;

    boolean mIsRegistered = false;
    ContentObserver mContentObserver;
    DataSetObserver mDataSetObserver;

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
    public ImageList(Context ctx, ContentResolver cr, Uri imageUri,
            Uri thumbUri, int sort, String bucketId) {
        super(ctx, cr, imageUri, sort, bucketId);
        mBaseUri = imageUri;
        mThumbUri = thumbUri;
        mSort = sort;

        mContentResolver = cr;

        mCursor = createCursor();
        if (mCursor == null) {
            Log.e(TAG, "unable to create image cursor for " + mBaseUri);
            throw new UnsupportedOperationException();
        }

        if (VERBOSE) {
            Log.v(TAG, "for " + mBaseUri.toString() + " got cursor "
                    + mCursor + " with length "
                    + (mCursor != null ? mCursor.getCount() : "-1"));
        }

        final Runnable updateRunnable = new Runnable() {
            public void run() {

                // handling these external updates is causing ANR problems that
                // are unresolved. For now ignore them since there shouldn't
                // be anyone modifying the database on the fly.
                if (true) return;

                synchronized (mCursor) {
                    requery();
                }
                if (mListener != null) mListener.onChange(ImageList.this);
            }
        };

        mContentObserver = new ContentObserver(null) {
            @Override
            public boolean deliverSelfNotifications() {
                return false;
            }

            @Override
            public void onChange(boolean selfChange) {
                if (VERBOSE) {
                    Log.v(TAG, "MyContentObserver.onChange; selfChange == "
                            + selfChange);
                }
                updateRunnable.run();
            }
        };

        mDataSetObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                if (VERBOSE) Log.v(TAG, "MyDataSetObserver.onChanged");
                // handling these external updates is causing ANR problems that
                // are unresolved. For now ignore them since there shouldn't
                // be anyone modifying the database on the fly.

                // updateRunnable.run();
            }

            @Override
            public void onInvalidated() {
                if (VERBOSE) {
                    Log.v(TAG, "MyDataSetObserver.onInvalidated: "
                            + mCursorDeactivated);
                }
            }
        };

        registerObservers();
    }

    private void registerObservers() {
        if (mIsRegistered) return;
        mCursor.registerContentObserver(mContentObserver);
        mCursor.registerDataSetObserver(mDataSetObserver);
        mIsRegistered = true;
    }

    private void unregisterObservers() {
        if (!mIsRegistered) return;
        mCursor.unregisterContentObserver(mContentObserver);
        mCursor.unregisterDataSetObserver(mDataSetObserver);
        mIsRegistered = false;
    }

    @Override
    public void deactivate() {
        super.deactivate();
        unregisterObservers();
    }

    @Override
    protected void activateCursor() {
        super.activateCursor();
        registerObservers();
    }

    private static final String sWhereClause =
            "(" + Images.Media.MIME_TYPE + " in (?, ?, ?))";

    protected String whereClause() {
        if (mBucketId != null) {
            return sWhereClause + " and " + Images.Media.BUCKET_ID + " = '"
                    + mBucketId + "'";
        } else {
            return sWhereClause;
        }
    }

    protected String[] whereClauseArgs() {
        return ACCEPTABLE_IMAGE_TYPES;
    }

    protected Cursor createCursor() {
        Cursor c = Images.Media.query(
                mContentResolver, mBaseUri, BaseImageList.IMAGE_PROJECTION,
                whereClause(), whereClauseArgs(), sortOrder());
        if (VERBOSE) {
            Log.v(TAG, "createCursor got cursor with count "
                    + (c == null ? -1 : c.getCount()));
        }
        return c;
    }

    @Override
    protected int indexOrientation() {
        return INDEX_ORIENTATION;
    }

    @Override
    protected int indexDateTaken() {
        return INDEX_DATE_TAKEN;
    }

    @Override
    protected int indexDescription() {
        return -1;
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
    protected int indexLatitude() {
        return -1;
    }

    @Override
    protected int indexLongitude() {
        return -1;
    }

    @Override
    protected int indexMiniThumbId() {
        return INDEX_MINI_THUMB_MAGIC;
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
        return INDEX_THUMB_ID;
    }

    @Override
    protected IImage make(long id, long miniThumbId, ContentResolver cr,
            IImageList list, long timestamp, int index, int rotation) {
        return new Image(id, miniThumbId, mContentResolver, this, index,
                rotation);
    }

    @Override
    protected Bitmap makeBitmap(int targetWidthHeight, Uri uri,
            ParcelFileDescriptor pfd, BitmapFactory.Options options) {
        Bitmap b = null;
        try {
            if (pfd == null) pfd = makeInputStream(uri);
            if (pfd == null) return null;
            if (options == null) options = new BitmapFactory.Options();

            java.io.FileDescriptor fd = pfd.getFileDescriptor();
            options.inSampleSize = 1;
            if (targetWidthHeight != -1) {
                options.inJustDecodeBounds = true;
                long t1 = System.currentTimeMillis();
                BitmapManager.instance().decodeFileDescriptor(fd, null, options);
                long t2 = System.currentTimeMillis();
                if (options.mCancel || options.outWidth == -1
                        || options.outHeight == -1) {
                    return null;
                }
                options.inSampleSize =
                        Util.computeSampleSize(options, targetWidthHeight);
                options.inJustDecodeBounds = false;
            }

            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            long t1 = System.currentTimeMillis();
            b = BitmapManager.instance()
                    .decodeFileDescriptor(fd, null, options);
            long t2 = System.currentTimeMillis();
            if (VERBOSE) {
                Log.v(TAG, "A: got bitmap " + b + " with sampleSize "
                        + options.inSampleSize + " took " + (t2 - t1));
            }
        } catch (OutOfMemoryError ex) {
            if (VERBOSE) {
                Log.v(TAG, "got oom exception " + ex);
            }
            return null;
        } finally {
            Util.closeSiliently(pfd);
        }
        return b;
    }

    private ParcelFileDescriptor makeInputStream(Uri uri) {
        try {
            return mContentResolver.openFileDescriptor(uri, "r");
        } catch (IOException ex) {
            return null;
        }
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

