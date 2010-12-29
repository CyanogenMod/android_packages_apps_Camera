/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

class Storage {
    private static final String TAG = "CameraStorage";

    private static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

    public static final String DIRECTORY = DCIM + "/Camera";

    public static final String THUMBNAILS = DCIM + "/.thumbnails";

    // Match the code in MediaProvider.computeBucketValues().
    public static final String BUCKET_ID =
            String.valueOf(DIRECTORY.toLowerCase().hashCode());

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;

    public static class Thumbnail {
        private Uri mBaseUri;
        private long mId;
        private Uri mUri;
        private Bitmap mBitmap;
        private int mOrientation;

        private Thumbnail(Uri baseUri, long id, int orientation) {
            mBaseUri = baseUri;
            mId = id;
            mOrientation = orientation;
        }

        private Thumbnail(Uri uri, Bitmap bitmap, int orientation) {
            mUri = uri;
            mBitmap = bitmap;
            mOrientation = orientation;
        }

        public Uri getOriginalUri() {
            if (mUri == null && mBaseUri != null) {
                mUri = ContentUris.withAppendedId(mBaseUri, mId);
            }
            return mUri;
        }

        public Bitmap getBitmap(ContentResolver resolver) {
            if (mBitmap == null) {
                if (Images.Media.EXTERNAL_CONTENT_URI.equals(mBaseUri)) {
                    mBitmap = Images.Thumbnails.getThumbnail(resolver, mId,
                            Images.Thumbnails.MICRO_KIND, null);
                } else if (Video.Media.EXTERNAL_CONTENT_URI.equals(mBaseUri)) {
                    mBitmap = Video.Thumbnails.getThumbnail(resolver, mId,
                            Video.Thumbnails.MICRO_KIND, null);
                }
            }
            if (mBitmap != null && mOrientation != 0) {
                // We only rotate the thumbnail once even if we get OOM.
                Matrix m = new Matrix();
                m.setRotate(mOrientation, mBitmap.getWidth() * 0.5f,
                        mBitmap.getHeight() * 0.5f);
                mOrientation = 0;

                try {
                    Bitmap rotated = Bitmap.createBitmap(mBitmap, 0, 0,
                            mBitmap.getWidth(), mBitmap.getHeight(), m, true);
                    mBitmap.recycle();
                    mBitmap = rotated;
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to rotate thumbnail", t);
                }
            }
            return mBitmap;
        }
    }

    public static Thumbnail getLastImageThumbnail(ContentResolver resolver) {
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;

        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[] {ImageColumns._ID, ImageColumns.ORIENTATION};
        String selection = ImageColumns.MIME_TYPE + "='image/jpeg' AND " +
                ImageColumns.BUCKET_ID + '=' + BUCKET_ID;
        String order = ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            if (cursor != null && cursor.moveToFirst()) {
                return new Thumbnail(baseUri, cursor.getLong(0), cursor.getInt(1));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static Thumbnail getLastVideoThumbnail(ContentResolver resolver) {
        Uri baseUri = Video.Media.EXTERNAL_CONTENT_URI;

        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[] {VideoColumns._ID};
        String selection = VideoColumns.BUCKET_ID + '=' + BUCKET_ID;
        String order = VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            if (cursor != null && cursor.moveToFirst()) {
                return new Thumbnail(baseUri, cursor.getLong(0), 0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static Thumbnail addImage(ContentResolver resolver, String title,
            long date, Location location, byte[] jpeg) {
        // Save the image.
        String path = DIRECTORY + '/' + title + ".jpg";
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(jpeg);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write image", e);
            return null;
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
        }

        // Get the orientation.
        int orientation = Exif.getOrientation(jpeg);

        // Insert into MediaStore.
        ContentValues values = new ContentValues(9);
        values.put(ImageColumns.TITLE, title);
        values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
        values.put(ImageColumns.DATE_TAKEN, date);
        values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        values.put(ImageColumns.ORIENTATION, orientation);
        values.put(ImageColumns.DATA, path);
        values.put(ImageColumns.SIZE, jpeg.length);

        if (location != null) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        }

        Uri uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.e(TAG, "Failed to write MediaStore");
            return null;
        }

        // Create the thumbnail.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 16;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        if (bitmap == null) {
            Log.e(TAG, "Failed to create thumbnail");
            return null;
        }
        return new Thumbnail(uri, bitmap, orientation);
    }

    public static long getAvailableSpace() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        File dir = new File(DIRECTORY);
        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(DIRECTORY);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatible() {
        File nnnAAAAA = new File(DIRECTORY, "100ANDRO");
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }
}
