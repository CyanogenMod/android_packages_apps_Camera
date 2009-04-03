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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.camera.ImageManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * An implementation of interface <code>IImageList</code> which contains only
 * one image.
 */
public class SingleImageList extends BaseImageList implements IImageList {
    private static final String TAG = "SingleImageList";
    private static final boolean VERBOSE = false;
    private static final int THUMBNAIL_TARGET_SIZE = 320;

    private IImage mSingleImage;

    private class UriImage extends SimpleBaseImage {

        UriImage() {
        }

        public String getDataPath() {
            return mUri.getPath();
        }

        InputStream getInputStream() {
            try {
                if (mUri.getScheme().equals("file")) {
                    String path = mUri.getPath();
                    if (VERBOSE) Log.v(TAG, "path is " + path);
                    return new java.io.FileInputStream(mUri.getPath());
                } else {
                    return mContentResolver.openInputStream(mUri);
                }
            } catch (FileNotFoundException ex) {
                return null;
            }
        }

        ParcelFileDescriptor getPFD() {
            try {
                if (mUri.getScheme().equals("file")) {
                    String path = mUri.getPath();
                    if (VERBOSE) Log.v(TAG, "path is " + path);
                    return ParcelFileDescriptor.open(new File(path),
                            ParcelFileDescriptor.MODE_READ_ONLY);
                } else {
                    return mContentResolver.openFileDescriptor(mUri, "r");
                }
            } catch (FileNotFoundException ex) {
                return null;
            }
        }

        public Bitmap fullSizeBitmap(int targetWidthHeight) {
            try {
                ParcelFileDescriptor pfdInput = getPFD();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(
                        pfdInput.getFileDescriptor(), null, options);

                if (targetWidthHeight != -1) {
                    options.inSampleSize =
                            Util.computeSampleSize(options, targetWidthHeight);
                }

                options.inJustDecodeBounds = false;
                options.inDither = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                Bitmap b = BitmapFactory.decodeFileDescriptor(
                        pfdInput.getFileDescriptor(), null, options);
                if (VERBOSE) {
                    Log.v(TAG, "B: got bitmap " + b + " with sampleSize "
                            + options.inSampleSize);
                }
                pfdInput.close();
                return b;
            } catch (Exception ex) {
                Log.e(TAG, "got exception decoding bitmap " + ex.toString());
                return null;
            }
        }

        final class LoadBitmapCancelable extends BaseCancelable
                implements IGetBitmapCancelable {
            ParcelFileDescriptor mPfdInput;
            BitmapFactory.Options mOptions = new BitmapFactory.Options();
            long mCancelInitiationTime;
            int mTargetWidthOrHeight;

            public LoadBitmapCancelable(
                    ParcelFileDescriptor pfd, int targetWidthOrHeight) {
                mPfdInput = pfd;
                mTargetWidthOrHeight = targetWidthOrHeight;
            }

            @Override
            public boolean doCancelWork() {
                if (VERBOSE) {
                    Log.v(TAG, "requesting bitmap load cancel");
                }
                mCancelInitiationTime = System.currentTimeMillis();
                mOptions.requestCancelDecode();
                return true;
            }

            public Bitmap get() {
                try {
                    Bitmap b = makeBitmap(mTargetWidthOrHeight,
                            fullSizeImageUri(), mPfdInput, mOptions);
                    if (b == null && mCancelInitiationTime != 0) {
                        if (VERBOSE) {
                            Log.v(TAG, "cancel returned null bitmap -- took "
                                    + (System.currentTimeMillis()
                                    - mCancelInitiationTime));
                        }
                    }
                    if (VERBOSE) Log.v(TAG, "b is " + b);
                    return b;
                } catch (Exception ex) {
                    return null;
                } finally {
                    acknowledgeCancel();
                }
            }
        }

        public IGetBitmapCancelable fullSizeBitmapCancelable(
                int targetWidthOrHeight) {
            try {
                ParcelFileDescriptor pfdInput = getPFD();
                if (pfdInput == null) return null;
                if (VERBOSE) Log.v(TAG, "inputStream is " + pfdInput);
                return new LoadBitmapCancelable(pfdInput, targetWidthOrHeight);
            } catch (UnsupportedOperationException ex) {
                return null;
            }
        }

        @Override
        public Uri fullSizeImageUri() {
            return mUri;
        }

        @Override
        public InputStream fullSizeImageData() {
            return getInputStream();
        }

        public long imageId() {
            return 0;
        }

        public Bitmap miniThumbBitmap() {
            return thumbBitmap();
        }

        @Override
        public String getTitle() {
            return mUri.toString();
        }

        @Override
        public String getDisplayName() {
            return getTitle();
        }

        @Override
        public String getDescription() {
            return "";
        }


        public Bitmap thumbBitmap() {
            Bitmap b = fullSizeBitmap(THUMBNAIL_TARGET_SIZE);
            if (b != null) {
                Matrix m = new Matrix();
                float scale = Math.min(
                        1F, THUMBNAIL_TARGET_SIZE / (float) b.getWidth());
                m.setScale(scale, scale);
                Bitmap scaledBitmap = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                return scaledBitmap;
            } else {
                return null;
            }
        }

        private BitmapFactory.Options snifBitmapOptions() {
            ParcelFileDescriptor input = getPFD();
            if (input == null) return null;
            try {
                Uri uri = fullSizeImageUri();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(
                        input.getFileDescriptor(), null, options);
                return options;
            } finally {
                Util.closeSiliently(input);
            }
        }

        @Override
        public String getMimeType() {
            BitmapFactory.Options options = snifBitmapOptions();
            return (options != null) ? options.outMimeType : "";
        }

        @Override
        public int getHeight() {
            BitmapFactory.Options options = snifBitmapOptions();
            return (options != null) ? options.outHeight : 0;
        }

        @Override
        public int getWidth() {
            BitmapFactory.Options options = snifBitmapOptions();
            return (options != null) ? options.outWidth : 0;
        }
    }

    public SingleImageList(ContentResolver cr, Uri uri) {
        super(null, cr, uri, ImageManager.SORT_ASCENDING, null);
        mSingleImage = new UriImage();
    }

    public HashMap<String, String> getBucketIds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deactivate() {
        // nothing to do here
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public IImage getImageAt(int i) {
        return i == 0 ? mSingleImage : null;
    }

    @Override
    public IImage getImageForUri(Uri uri) {
        return uri.equals(mUri) ? mSingleImage : null;
    }

    public IImage getImageWithId(long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int indexOrientation() {
        return -1;
    }

    @Override
    protected int indexDateTaken() {
        return -1;
    }

    @Override
    protected int indexMimeType() {
        return -1;
    }

    @Override
    protected int indexDescription() {
        return -1;
    }

    @Override
    protected int indexId() {
        return -1;
    }

    @Override
    protected int indexData() {
        return -1;
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
        return -1;
    }

    @Override
    protected int indexPicasaWeb() {
        return -1;
    }

    @Override
    protected int indexPrivate() {
        return -1;
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
        return -1;
    }

    @Override
    protected Bitmap makeBitmap(int targetWidthHeight, Uri uri,
            ParcelFileDescriptor pfdInput, BitmapFactory.Options options) {
        Bitmap b = null;
        try {
            if (options == null) options = new BitmapFactory.Options();
            options.inSampleSize = 1;

            if (targetWidthHeight != -1) {
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(
                        pfdInput.getFileDescriptor(), null, options);
                options.inSampleSize =
                        Util.computeSampleSize(options, targetWidthHeight);
                options.inJustDecodeBounds = false;
            }
            b = BitmapFactory.decodeFileDescriptor(
                    pfdInput.getFileDescriptor(), null, options);
            if (VERBOSE) {
                Log.v(TAG, "C: got bitmap " + b + " with sampleSize "
                        + options.inSampleSize);
            }
        } catch (OutOfMemoryError ex) {
            if (VERBOSE) Log.v(TAG, "got oom exception " + ex);
            return null;
        } finally {
            Util.closeSiliently(pfdInput);
        }
        return b;
    }
}
