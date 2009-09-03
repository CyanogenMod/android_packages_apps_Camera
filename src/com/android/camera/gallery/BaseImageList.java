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
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Parcel;
import android.provider.BaseColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    protected MiniThumbFile mMiniThumbFile;
    protected Uri mThumbUri;
    protected boolean mCursorDeactivated = false;

    public BaseImageList(Uri uri, int sort, String bucketId) {
        mSort = sort;
        mBaseUri = uri;
        mBucketId = bucketId;
        mMiniThumbFile = new MiniThumbFile(uri);
    }

    protected BaseImageList(Parcel in) {
        mSort = in.readInt();
        mBaseUri = (Uri) in.readParcelable(null);
        mBucketId = in.readString();
        mMiniThumbFile = new MiniThumbFile(mBaseUri);
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mSort);
        out.writeParcelable(mBaseUri, flags);
        out.writeString(mBucketId);
    }

    public void open(ContentResolver resolver) {
        mContentResolver = resolver;
        mCursor = createCursor();

        // If the media provider is killed, we will fail to get the cursor.
        // This is a workaround to wait a bit and retry in the hope that the
        // new instance of media provider will be created soon enough.
        if (mCursor == null) {
            for (int i = 0; i < 10; i++) {
                Log.w(TAG, "createCursor failed, retry...");
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ex) {
                    // ignore.
                }
                mCursor = createCursor();
                if (mCursor != null) break;
            }
        }

        // TODO: We need to clear the cache because we may "reopen" the image
        // list. After we implement the image list state, we can remove this
        // kind of usage.
        mCache.clear();
    }

    // TODO: merge close() and deactivate()
    public void close() {
        mContentResolver = null;
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    public int describeContents() {
        return 0;
    }

    /**
     * Store a given thumbnail in the database.
     */
    protected Bitmap storeThumbnail(Bitmap thumb, long imageId) {
        if (thumb == null) return null;
        try {
            Uri uri = getThumbnailUri(imageId, thumb.getWidth(),
                    thumb.getHeight());
            if (uri == null) {
                return thumb;
            }
            OutputStream thumbOut = mContentResolver.openOutputStream(uri);
            thumb.compress(Bitmap.CompressFormat.JPEG, 60, thumbOut);
            thumbOut.close();
            return thumb;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to store thumbnail", ex);
            return thumb;
        }
    }

    /**
     * Store a JPEG thumbnail from the EXIF header in the database.
     */
    protected boolean storeThumbnail(
            byte[] jpegThumbnail, long imageId, int width, int height) {
        if (jpegThumbnail == null) return false;

        Uri uri = getThumbnailUri(imageId, width, height);
        if (uri == null) {
            return false;
        }
        try {
            OutputStream thumbOut = mContentResolver.openOutputStream(uri);
            thumbOut.write(jpegThumbnail);
            thumbOut.close();
            return true;
        } catch (FileNotFoundException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private static final String[] THUMB_PROJECTION = new String[] {
        BaseColumns._ID
    };

    private Uri getThumbnailUri(long imageId, int width, int height) {

        // we do not store thumbnails for DRM'd images
        if (mThumbUri == null) {
            return null;
        }

        Cursor c = mContentResolver.query(mThumbUri, THUMB_PROJECTION,
                Thumbnails.IMAGE_ID + "=?",
                new String[]{String.valueOf(imageId)}, null);
        try {
            if (c.moveToNext()) {
                return ContentUris.withAppendedId(mThumbUri, c.getLong(0));
            }
        } finally {
            c.close();
        }
        ContentValues values = new ContentValues(4);
        values.put(Thumbnails.KIND, Thumbnails.MINI_KIND);
        values.put(Thumbnails.IMAGE_ID, imageId);
        values.put(Thumbnails.HEIGHT, height);
        values.put(Thumbnails.WIDTH, width);
        try {
            return mContentResolver.insert(mThumbUri, values);
        } catch (Exception ex) {
            return null;
        }
    }

    private static final Random sRandom =
            new Random(System.currentTimeMillis());

    // If the photo has an EXIF thumbnail and it's big enough, extract it and
    // save that JPEG as the large thumbnail without re-encoding it. We still
    // have to decompress it though, in order to generate the minithumb.
    private Bitmap createThumbnailFromEXIF(String filePath, long id) {
        if (filePath == null) return null;

        byte [] thumbData = ExifInterface.getExifThumbnail(filePath);
        if (thumbData == null) return null;

        // Sniff the size of the EXIF thumbnail before decoding it. Photos
        // from the device will pass, but images that are side loaded from
        // other cameras may not.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(thumbData, 0, thumbData.length, options);
        int width = options.outWidth;
        int height = options.outHeight;
        if (width >= IImage.THUMBNAIL_TARGET_SIZE
                && height >= IImage.THUMBNAIL_TARGET_SIZE) {

            // We do not check the return value of storeThumbnail because
            // we should return the mini thumb even if the storing fails.
            storeThumbnail(thumbData, id, width, height);

            // this is used for *encoding* the minithumb, so
            // we don't want to dither or convert to 565 here.
            //
            // Decode with a scaling factor
            // to match MINI_THUMB_TARGET_SIZE closely
            // which will produce much better scaling quality
            // and is significantly faster.
            options.inSampleSize =
                    Util.computeSampleSize(options,
                    IImage.MINI_THUMB_TARGET_SIZE,
                    IImage.MINI_THUMB_MAX_NUM_PIXELS);
            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(
                    thumbData, 0, thumbData.length, options);
        }
        return null;
    }

    // The fallback case is to decode the original photo to thumbnail size,
    // then encode it as a JPEG. We return the thumbnail Bitmap in order to
    // create the minithumb from it.
    private Bitmap createThumbnailFromUri(Uri uri, long id) {
        Bitmap bitmap = Util.makeBitmap(IImage.THUMBNAIL_TARGET_SIZE,
                IImage.THUMBNAIL_MAX_NUM_PIXELS, uri, mContentResolver);
        if (bitmap != null) {
            storeThumbnail(bitmap, id);
        } else {
            bitmap = Util.makeBitmap(IImage.MINI_THUMB_TARGET_SIZE,
                    IImage.MINI_THUMB_MAX_NUM_PIXELS, uri, mContentResolver);
        }
        return bitmap;
    }

    public void checkThumbnail(int index) throws IOException {
        checkThumbnail((BaseImage) getImageAt(index), null);
    }

    /**
     * Checks to see if a mini thumbnail exists in the cache. If not, tries to
     * create it and add it to the cache.
     * @param createdThumbnailData if this parameter is non-null, and a new
     *         mini-thumbnail bitmap is created, the new bitmap's data will be
     *         stored in createdThumbnailData[0]. Note that if the sdcard is
     *         full, it's possible that createdThumbnailData[0] will be set
     *         even if the method throws an IOException. This is actually
     *         useful, because it allows the caller to use the created
     *         thumbnail even if the sdcard is full.
     * @throws IOException
     */
    public void checkThumbnail(BaseImage existingImage,
            byte[][] createdThumbnailData) throws IOException {
        long magic, id;

        magic = existingImage.mMiniThumbMagic;
        id = existingImage.fullSizeImageId();

        if (magic != 0) {
            long fileMagic = mMiniThumbFile.getMagic(id);
            if (fileMagic == magic) {
                return;
            }
        }

        // If we can't retrieve the thumbnail, first check if there is one
        // embedded in the EXIF data. If not, or it's not big enough,
        // decompress the full size image.
        Bitmap bitmap = null;
        String filePath = existingImage.getDataPath();

        if (filePath != null) {
            boolean isVideo = ImageManager.isVideo(existingImage);
            if (isVideo) {
                bitmap = Util.createVideoThumbnail(filePath);
            } else {
                bitmap = createThumbnailFromEXIF(filePath, id);
                if (bitmap == null) {
                    bitmap = createThumbnailFromUri(
                            ContentUris.withAppendedId(mBaseUri, id), id);
                }
            }
            int degrees = existingImage.getDegreesRotated();
            if (degrees != 0) {
                bitmap = Util.rotate(bitmap, degrees);
            }
        }

        // make a new magic number since things are out of sync
        do {
            magic = sRandom.nextLong();
        } while (magic == 0);

        if (bitmap != null) {
            byte [] data = Util.miniThumbData(bitmap);
            if (createdThumbnailData != null) {
                createdThumbnailData[0] = data;
            }

            // This could throw IOException.
            saveMiniThumbToFile(data, id, magic);
        }

        ContentValues values = new ContentValues();
        values.put(ImageColumns.MINI_THUMB_MAGIC, magic);
        mContentResolver.update(
                existingImage.fullSizeImageUri(), values, null, null);
        existingImage.mMiniThumbMagic = magic;
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

    public void deactivate() {
        try {
            invalidateCursor();
        } catch (IllegalStateException e) {
            // IllegalStateException may be thrown if the cursor is stale.
            Log.e(TAG, "Caught exception while deactivating cursor.", e);
        }
        mMiniThumbFile.deactivate();
    }

    public int getCount() {
        Cursor cursor = getCursor();
        synchronized (cursor) {
            return cursor.getCount();
        }
    }

    public boolean isEmpty() {
        return getCount() == 0;
    }

    private Cursor getCursor() {
        synchronized (mCursor) {
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
            synchronized (cursor) {
                result = cursor.moveToPosition(i)
                        ? loadImageFromCursor(cursor)
                        : null;
                mCache.put(i, result);
            }
        }
        return result;
    }

    byte [] getMiniThumbFromFile(long id, byte [] data, long magicCheck) {
        return mMiniThumbFile.getMiniThumbFromFile(id, data, magicCheck);
    }

    void saveMiniThumbToFile(Bitmap bitmap, long id, long magic)
            throws IOException {
        mMiniThumbFile.saveMiniThumbToFile(bitmap, id, magic);
    }

    void saveMiniThumbToFile(byte[] data, long id, long magic)
            throws IOException {
        mMiniThumbFile.saveMiniThumbToFile(data, id, magic);
    }

    public boolean removeImage(IImage image) {
        // TODO: need to delete the thumbnails as well
        if (mContentResolver.delete(image.fullSizeImageUri(), null, null) > 0) {
            ((BaseImage) image).onRemove();
            invalidateCursor();
            invalidateCache();
            return true;
        } else {
            return false;
        }
    }

    public boolean removeImageAt(int i) {
        // TODO: need to delete the thumbnails as well
        return removeImage(getImageAt(i));
    }

    protected abstract Cursor createCursor();

    protected abstract BaseImage loadImageFromCursor(Cursor cursor);

    protected abstract long getImageId(Cursor cursor);

    protected void invalidateCursor() {
        mCursor.deactivate();
        mCursorDeactivated = true;
    }

    protected void invalidateCache() {
        mCache.clear();
    }

    private static final Pattern sPathWithId = Pattern.compile("(.*)/\\d+");

    private static String getPathWithoutId(Uri uri) {
        String path = uri.getPath();
        Matcher matcher = sPathWithId.matcher(path);
        return matcher.matches() ? matcher.group(1) : path;
    }

    private boolean isChildImageUri(Uri uri) {
        // Sometimes, the URI of an image contains a query string with key
        // "bucketId" inorder to restore the image list. However, the query
        // string is not part of the mBaseUri. So, we check only other parts
        // of the two Uri to see if they are the same.
        Uri base = mBaseUri;
        return Util.equals(base.getScheme(), uri.getScheme())
                && Util.equals(base.getHost(), uri.getHost())
                && Util.equals(base.getAuthority(), uri.getAuthority())
                && Util.equals(base.getPath(), getPathWithoutId(uri));
    }

    public IImage getImageForUri(Uri uri) {
        if (!isChildImageUri(uri)) return null;
        // Find the id of the input URI.
        long matchId;
        try {
            matchId = ContentUris.parseId(uri);
        } catch (NumberFormatException ex) {
            Log.i(TAG, "fail to get id in: " + uri, ex);
            return null;
        }
        // TODO: design a better method to get URI of specified ID
        Cursor cursor = getCursor();
        synchronized (cursor) {
            cursor.moveToPosition(-1); // before first
            for (int i = 0; cursor.moveToNext(); ++i) {
                if (getImageId(cursor) == matchId) {
                    BaseImage image = mCache.get(i);
                    if (image == null) {
                        image = loadImageFromCursor(cursor);
                        mCache.put(i, image);
                    }
                    return image;
                }
            }
            return null;
        }
    }

    public int getImageIndex(IImage image) {
        return ((BaseImage) image).mIndex;
    }
}
