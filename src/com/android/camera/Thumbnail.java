/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

class Thumbnail {
    private static final String TAG = "Thumbnail";

    private static final int BUFSIZE = 4096;

    private Uri mUri;
    private Bitmap mBitmap;

    public Thumbnail(Uri uri, Bitmap bitmap, int orientation) {
        mUri = uri;
        mBitmap = rotateImage(bitmap, orientation);
    }

    public Uri getUri() {
        return mUri;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    private static Bitmap rotateImage(Bitmap bitmap, int orientation) {
        if (orientation != 0) {
            // We only rotate the thumbnail once even if we get OOM.
            Matrix m = new Matrix();
            m.setRotate(orientation, bitmap.getWidth() * 0.5f,
                    bitmap.getHeight() * 0.5f);

            try {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), m, true);
                bitmap.recycle();
                return rotated;
            } catch (Throwable t) {
                Log.w(TAG, "Failed to rotate thumbnail", t);
            }
        }
        return bitmap;
    }

    // Stores the bitmap to the specified file.
    public void saveTo(File file) {
        FileOutputStream f = null;
        BufferedOutputStream b = null;
        DataOutputStream d = null;
        try {
            f = new FileOutputStream(file);
            b = new BufferedOutputStream(f, BUFSIZE);
            d = new DataOutputStream(b);
            d.writeUTF(mUri.toString());
            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, d);
            d.close();
        } catch (IOException e) {
            Log.e(TAG, "Fail to store bitmap. path=" + file.getPath(), e);
        } finally {
            Util.closeSilently(f);
            Util.closeSilently(b);
            Util.closeSilently(d);
        }
    }


    // Loads the data from the specified file.
    // Returns null if failure.
    public static Thumbnail loadFrom(File file) {
        Uri uri = null;
        Bitmap bitmap = null;
        FileInputStream f = null;
        BufferedInputStream b = null;
        DataInputStream d = null;
        try {
            f = new FileInputStream(file);
            b = new BufferedInputStream(f, BUFSIZE);
            d = new DataInputStream(b);
            uri = Uri.parse(d.readUTF());
            bitmap = BitmapFactory.decodeStream(d);
            d.close();
        } catch (IOException e) {
            Log.i(TAG, "Fail to load bitmap. " + e);
            return null;
        } finally {
            Util.closeSilently(f);
            Util.closeSilently(b);
            Util.closeSilently(d);
        }
        return new Thumbnail(uri, bitmap, 0);
    }

    public static Thumbnail getLastImageThumbnail(ContentResolver resolver) {
        Uri baseUri = Images.Media.EXTERNAL_CONTENT_URI;

        Uri query = baseUri.buildUpon().appendQueryParameter("limit", "1").build();
        String[] projection = new String[] {ImageColumns._ID, ImageColumns.ORIENTATION};
        String selection = ImageColumns.MIME_TYPE + "='image/jpeg' AND " +
                ImageColumns.BUCKET_ID + '=' + Storage.BUCKET_ID;
        String order = ImageColumns.DATE_TAKEN + " DESC," + ImageColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                int orientation = cursor.getInt(1);
                Bitmap bitmap = Images.Thumbnails.getThumbnail(resolver, id,
                        Images.Thumbnails.MINI_KIND, null);
                Uri uri = ContentUris.withAppendedId(baseUri, id);
                // Ensure there's no OOM. Ensure database and storage are in sync.
                if (bitmap != null && Util.isUriValid(uri, resolver)) {
                    return new Thumbnail(uri, bitmap, orientation);
                }
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
        String[] projection = new String[] {VideoColumns._ID, MediaColumns.DATA};
        String selection = VideoColumns.BUCKET_ID + '=' + Storage.BUCKET_ID;
        String order = VideoColumns.DATE_TAKEN + " DESC," + VideoColumns._ID + " DESC";

        Cursor cursor = null;
        try {
            cursor = resolver.query(query, projection, selection, null, order);
            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, "getLastVideoThumbnail: " + cursor.getString(1));
                long id = cursor.getLong(0);
                Bitmap bitmap = Video.Thumbnails.getThumbnail(resolver, id,
                        Video.Thumbnails.MINI_KIND, null);
                Uri uri = ContentUris.withAppendedId(baseUri, id);
                // Ensure there's no OOM. Ensure database and storage are in sync.
                if (bitmap != null && Util.isUriValid(uri, resolver)) {
                    return new Thumbnail(uri, bitmap, 0);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static Thumbnail createThumbnail(byte[] jpeg, int orientation, int inSampleSize,
            Uri uri) {
        // Create the thumbnail.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = inSampleSize;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        if (bitmap == null) {
            Log.e(TAG, "Failed to create thumbnail");
            return null;
        }
        return new Thumbnail(uri, bitmap, orientation);
    }
}
