/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.camera.gallery.BaseImageList;
import com.android.camera.gallery.DrmImageList;
import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;
import com.android.camera.gallery.Image;
import com.android.camera.gallery.ImageList;
import com.android.camera.gallery.ImageListUber;
import com.android.camera.gallery.SingleImageList;
import com.android.camera.gallery.VideoList;
import com.android.camera.gallery.VideoObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DrmStore;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * ImageManager is used to retrieve and store images
 * in the media content provider.
 */
public class ImageManager {
    private static final String TAG = "ImageManager";

    private static final Uri STORAGE_URI = Images.Media.EXTERNAL_CONTENT_URI;
    private static final Uri THUMB_URI
            = Images.Thumbnails.EXTERNAL_CONTENT_URI;

    private static final Uri VIDEO_STORAGE_URI =
            Uri.parse("content://media/external/video/media");

    // ImageListParam specifies all the parameters we need to create an image
    // list (we also need a ContentResolver).
    public static class ImageListParam implements Parcelable {
        public DataLocation mLocation;
        public int mInclusion;
        public int mSort;
        public String mBucketId;

        // This is only used if we are creating a single image list.
        public Uri mSingleImageUri;

        // This is only used if we are creating an empty image list.
        public boolean mIsEmptyImageList;

        public ImageListParam() {}

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mLocation.ordinal());
            out.writeInt(mInclusion);
            out.writeInt(mSort);
            out.writeString(mBucketId);
            out.writeParcelable(mSingleImageUri, flags);
            out.writeInt(mIsEmptyImageList ? 1 : 0);
        }

        private ImageListParam(Parcel in) {
            mLocation = DataLocation.values()[in.readInt()];
            mInclusion = in.readInt();
            mSort = in.readInt();
            mBucketId = in.readString();
            mSingleImageUri = in.readParcelable(null);
            mIsEmptyImageList = (in.readInt() != 0);
        }

        public String toString() {
            return String.format("ImageListParam{loc=%s,inc=%d,sort=%d," +
                "bucket=%s,empty=%b,single=%s}", mLocation, mInclusion,
                mSort, mBucketId, mIsEmptyImageList, mSingleImageUri);
        }

        public static final Parcelable.Creator CREATOR
                = new Parcelable.Creator() {
            public ImageListParam createFromParcel(Parcel in) {
                return new ImageListParam(in);
            }

            public ImageListParam[] newArray(int size) {
                return new ImageListParam[size];
            }
        };

        public int describeContents() {
            return 0;
        }
    }

    // Location
    public static enum DataLocation { NONE, INTERNAL, EXTERNAL, ALL }

    // Inclusion
    public static final int INCLUDE_IMAGES = (1 << 0);
    public static final int INCLUDE_DRM_IMAGES = (1 << 1);
    public static final int INCLUDE_VIDEOS = (1 << 2);

    // Sort
    public static final int SORT_ASCENDING = 1;
    public static final int SORT_DESCENDING = 2;

    public static final String CAMERA_IMAGE_BUCKET_NAME =
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/Camera";
    public static final String CAMERA_IMAGE_BUCKET_ID =
            getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common
     * function.
     */
    public static String getBucketId(String path) {
        return String.valueOf(path.toLowerCase().hashCode());
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatibleFolder() {
        File nnnAAAAA = new File(
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/100ANDRO");
        if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir())) {
            Log.e(TAG, "create NNNAAAAA file: " + nnnAAAAA.getPath()
                    + " failed");
        }
    }

    public static int roundOrientation(int orientationInput) {
        int orientation = orientationInput;
        if (orientation == -1) {
            orientation = 0;
        }

        orientation = orientation % 360;
        int retVal;
        if (orientation < (0 * 90) + 45) {
            retVal = 0;
        } else if (orientation < (1 * 90) + 45) {
            retVal = 90;
        } else if (orientation < (2 * 90) + 45) {
            retVal = 180;
        } else if (orientation < (3 * 90) + 45) {
            retVal = 270;
        } else {
            retVal = 0;
        }

        return retVal;
    }

    /**
     * @return true if the mimetype is an image mimetype.
     */
    public static boolean isImageMimeType(String mimeType) {
        return mimeType.startsWith("image/");
    }

    /**
     * @return true if the mimetype is a video mimetype.
     */
    /* This is commented out because isVideo is not calling this now.
    public static boolean isVideoMimeType(String mimeType) {
        return mimeType.startsWith("video/");
    }
    */

    /**
     * @return true if the image is an image.
     */
    public static boolean isImage(IImage image) {
        return isImageMimeType(image.getMimeType());
    }

    /**
     * @return true if the image is a video.
     */
    public static boolean isVideo(IImage image) {
        // This is the right implementation, but we use instanceof for speed.
        //return isVideoMimeType(image.getMimeType());
        return (image instanceof VideoObject);
    }

    public static void setImageSize(ContentResolver cr, Uri uri, long size) {
        ContentValues values = new ContentValues();
        values.put(Images.Media.SIZE, size);
        cr.update(uri, values, null, null);
    }

    public static Uri addImage(ContentResolver cr, String title,
            long dateTaken, Location location,
            int orientation, String directory, String filename) {

        ContentValues values = new ContentValues(7);
        values.put(Images.Media.TITLE, title);

        // That filename is what will be handed to Gmail when a user shares a
        // photo. Gmail gets the name of the picture attachment from the
        // "DISPLAY_NAME" field.
        values.put(Images.Media.DISPLAY_NAME, filename);
        values.put(Images.Media.DATE_TAKEN, dateTaken);
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.ORIENTATION, orientation);

        if (location != null) {
            values.put(Images.Media.LATITUDE, location.getLatitude());
            values.put(Images.Media.LONGITUDE, location.getLongitude());
        }

        if (directory != null && filename != null) {
            String value = directory + "/" + filename;
            values.put(Images.Media.DATA, value);
        }

        return cr.insert(STORAGE_URI, values);
    }

    public static void storeImage(
            Uri uri, ContentResolver cr, int orientation,
            Bitmap source, byte [] jpegData) {

        if (source == null && jpegData == null || uri == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        boolean complete = false;
        try {
            long id = ContentUris.parseId(uri);
            BaseImageList il = new ImageList(
                    cr, STORAGE_URI, THUMB_URI, SORT_ASCENDING, null);

            // TODO: Redesign the process of adding new images. We should
            //     create an <code>IImage</code> in "ImageManager.addImage"
            //     and pass the image object to here.
            Image image = new Image(il, cr, id, 0, il.contentUri(id), null,
                    0, null, 0, null, null, 0);
            String[] projection = new String[] {
                    ImageColumns._ID,
                    ImageColumns.MINI_THUMB_MAGIC, ImageColumns.DATA};
            Cursor c = cr.query(uri, projection, null, null, null);
            String filepath;
            try {
                c.moveToPosition(0);
                filepath = c.getString(2);
            } finally {
                c.close();
            }
            image.saveImageContents(
                    source, jpegData, orientation, true, filepath);

            ContentValues values = new ContentValues();
            values.put(ImageColumns.MINI_THUMB_MAGIC, 0);
            cr.update(uri, values, null, null);
            complete = true;
        } finally {
            if (!complete) {
                try {
                    cr.delete(uri, null, null);
                } catch (Throwable t) {
                    // ignore it while clean up.
                }
            }
        }
    }

    // This is the factory function to create an image list.
    public static IImageList makeImageList(ContentResolver cr,
            ImageListParam param) {

        DataLocation location = param.mLocation;
        int inclusion = param.mInclusion;
        int sort = param.mSort;
        String bucketId = param.mBucketId;
        Uri singleImageUri = param.mSingleImageUri;
        boolean isEmptyImageList = param.mIsEmptyImageList;

        if (isEmptyImageList || cr == null) {
            return new EmptyImageList();
        }

        if (singleImageUri != null) {
            return new SingleImageList(cr, singleImageUri);
        }

        // false ==> don't require write access
        boolean haveSdCard = hasStorage(false);

        // use this code to merge videos and stills into the same list
        ArrayList<BaseImageList> l = new ArrayList<BaseImageList>();

        if (haveSdCard && location != DataLocation.INTERNAL) {
            if ((inclusion & INCLUDE_IMAGES) != 0) {
                l.add(new ImageList(
                        cr, STORAGE_URI, THUMB_URI, sort, bucketId));
            }
            if ((inclusion & INCLUDE_VIDEOS) != 0) {
                l.add(new VideoList(cr, VIDEO_STORAGE_URI, sort, bucketId));
            }
        }
        if (location == DataLocation.INTERNAL || location == DataLocation.ALL) {
            if ((inclusion & INCLUDE_IMAGES) != 0) {
                l.add(new ImageList(cr,
                        Images.Media.INTERNAL_CONTENT_URI,
                        Images.Thumbnails.INTERNAL_CONTENT_URI,
                        sort, bucketId));
            }
            if ((inclusion & INCLUDE_DRM_IMAGES) != 0) {
                l.add(new DrmImageList(
                        cr, DrmStore.Images.CONTENT_URI, sort, bucketId));
            }
        }

        // Optimization: If some of the lists are empty, remove them.
        // If there is only one remaining list, return it directly.
        Iterator<BaseImageList> iter = l.iterator();
        while (iter.hasNext()) {
            BaseImageList sublist = iter.next();
            if (sublist.isEmpty()) iter.remove();
        }

        if (l.size() == 1) {
            BaseImageList list = l.get(0);
            return list;
        }

        ImageListUber uber = new ImageListUber(
                l.toArray(new IImageList[l.size()]), sort);
        return uber;
    }

    // This is a convenience function to create an image list from a Uri.
    public static IImageList makeImageList(ContentResolver cr, Uri uri,
            int sort) {
        String uriString = (uri != null) ? uri.toString() : "";

        // TODO: we need to figure out whether we're viewing
        // DRM images in a better way.  Is there a constant
        // for content://drm somewhere??

        if (uriString.startsWith("content://drm")) {
            return makeImageList(cr, DataLocation.ALL, INCLUDE_DRM_IMAGES, sort,
                    null);
        } else if (uriString.startsWith("content://media/external/video")) {
            return makeImageList(cr, DataLocation.EXTERNAL, INCLUDE_VIDEOS,
                    sort, null);
        } else if (isSingleImageMode(uriString)) {
            return makeSingleImageList(cr, uri);
        } else {
            String bucketId = uri.getQueryParameter("bucketId");
            return makeImageList(cr, DataLocation.ALL, INCLUDE_IMAGES, sort,
                    bucketId);
        }
    }

    static boolean isSingleImageMode(String uriString) {
        return !uriString.startsWith(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
                && !uriString.startsWith(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString());
    }

    private static class EmptyImageList implements IImageList {
        public void close() {
        }

        public void checkThumbnail(int index) {
        }

        public HashMap<String, String> getBucketIds() {
            return new HashMap<String, String>();
        }

        public int getCount() {
            return 0;
        }

        public boolean isEmpty() {
            return true;
        }

        public IImage getImageAt(int i) {
            return null;
        }

        public IImage getImageForUri(Uri uri) {
            return null;
        }

        public boolean removeImage(IImage image) {
            return false;
        }

        public boolean removeImageAt(int i) {
            return false;
        }

        public int getImageIndex(IImage image) {
            throw new UnsupportedOperationException();
        }
    }

    public static ImageListParam getImageListParam(DataLocation location,
         int inclusion, int sort, String bucketId) {
         ImageListParam param = new ImageListParam();
         param.mLocation = location;
         param.mInclusion = inclusion;
         param.mSort = sort;
         param.mBucketId = bucketId;
         return param;
    }

    public static ImageListParam getSingleImageListParam(Uri uri) {
        ImageListParam param = new ImageListParam();
        param.mSingleImageUri = uri;
        return param;
    }

    public static ImageListParam getEmptyImageListParam() {
        ImageListParam param = new ImageListParam();
        param.mIsEmptyImageList = true;
        return param;
    }

    public static IImageList makeImageList(ContentResolver cr,
            DataLocation location, int inclusion, int sort, String bucketId) {
        ImageListParam param = getImageListParam(location, inclusion, sort,
                bucketId);
        return makeImageList(cr, param);
    }

    public static IImageList makeEmptyImageList() {
        return makeImageList(null, getEmptyImageListParam());
    }

    public static IImageList  makeSingleImageList(ContentResolver cr, Uri uri) {
        return makeImageList(cr, getSingleImageListParam(uri));
    }

    private static boolean checkFsWritable() {
        // Create a temporary file to see whether a volume is really writeable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        String directoryName =
                Environment.getExternalStorageDirectory().toString() + "/DCIM";
        File directory = new File(directoryName);
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()) {
                return false;
            }
        }
        File f = new File(directoryName, ".probe");
        try {
            // Remove stale file if any
            if (f.exists()) {
                f.delete();
            }
            if (!f.createNewFile()) {
                return false;
            }
            f.delete();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static boolean hasStorage() {
        return hasStorage(true);
    }

    public static boolean hasStorage(boolean requireWriteAccess) {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (requireWriteAccess) {
                boolean writable = checkFsWritable();
                return writable;
            } else {
                return true;
            }
        } else if (!requireWriteAccess
                && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    private static Cursor query(ContentResolver resolver, Uri uri,
            String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        try {
            if (resolver == null) {
                return null;
            }
            return resolver.query(
                    uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }

    }

    public static boolean isMediaScannerScanning(ContentResolver cr) {
        boolean result = false;
        Cursor cursor = query(cr, MediaStore.getMediaScannerUri(),
                new String [] {MediaStore.MEDIA_SCANNER_VOLUME},
                null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }

        return result;
    }

    public static String getLastImageThumbPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.thumbnails/image_last_thumb";
    }

    public static String getLastVideoThumbPath() {
        return Environment.getExternalStorageDirectory().toString() +
               "/DCIM/.thumbnails/video_last_thumb";
    }
}
