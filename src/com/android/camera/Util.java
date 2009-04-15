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

package com.android.camera;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.FileDescriptor;

/**
 * Collection of utility functions used in this package.
 */
public class Util {
    private static final boolean VERBOSE = false;
    private static final String TAG = "db.Util";

    private Util() {
    }

    // Rotates the bitmap by the specified degree.
    // If a new bitmap is created, the original bitmap is recycled.
    public static Bitmap rotate(Bitmap b, int degrees) {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees,
                    (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }
        return b;
    }

    /*
     * Compute the sample size as a function of the image size and the target.
     * Scale the image down so that both the width and height are just above the
     * target. If this means that one of the dimension goes from above the
     * target to below the target (e.g. given a width of 480 and an image width
     * of 600 but sample size of 2 -- i.e. new width 300 -- bump the sample size
     * down by 1.
     */
    public static int computeSampleSize(
            BitmapFactory.Options options, int target) {
        int w = options.outWidth;
        int h = options.outHeight;

        int candidateW = w / target;
        int candidateH = h / target;
        int candidate = Math.max(candidateW, candidateH);

        if (candidate == 0) return 1;

        if (candidate > 1) {
            if ((w > target) && (w / candidate) < target) candidate -= 1;
        }

        if (candidate > 1) {
            if ((h > target) && (h / candidate) < target) candidate -= 1;
        }

        return candidate;
    }

    /**
     * Creates a centered bitmap of the desired size. Recycles the input.
     * @param source
     */
    public static Bitmap extractMiniThumb(
            Bitmap source, int width, int height) {
        return Util.extractMiniThumb(source, width, height, true);
    }

    public static Bitmap extractMiniThumb(
            Bitmap source, int width, int height, boolean recycle) {
        if (source == null) {
            return null;
        }

        float scale;
        if (source.getWidth() < source.getHeight()) {
            scale = width / (float) source.getWidth();
        } else {
            scale = height / (float) source.getHeight();
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        Bitmap miniThumbnail = ImageLoader.transform(matrix, source,
                width, height, false);

        if (recycle && miniThumbnail != source) {
            source.recycle();
        }
        return miniThumbnail;
    }

    /**
     * Creates a byte[] for a given bitmap of the desired size. Recycles the
     * input bitmap.
     */
    public static byte[] miniThumbData(Bitmap source) {
        if (source == null) return null;

        Bitmap miniThumbnail = extractMiniThumb(
                source, ImageManager.MINI_THUMB_TARGET_SIZE,
                ImageManager.MINI_THUMB_TARGET_SIZE);

        ByteArrayOutputStream miniOutStream = new ByteArrayOutputStream();
        miniThumbnail.compress(Bitmap.CompressFormat.JPEG, 75, miniOutStream);
        miniThumbnail.recycle();

        try {
            miniOutStream.close();
            byte [] data = miniOutStream.toByteArray();
            return data;
        } catch (java.io.IOException ex) {
            Log.e(TAG, "got exception ex " + ex);
        }
        return null;
    }

    /**
     * @return true if the mimetype is a video mimetype.
     */
    public static boolean isVideoMimeType(String mimeType) {
        return mimeType.startsWith("video/");
    }

    /**
     * Create a video thumbnail for a video. May return null if the video is
     * corrupt.
     *
     * @param filePath
     */
    public static Bitmap createVideoThumbnail(String filePath) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setMode(MediaMetadataRetriever.MODE_CAPTURE_FRAME_ONLY);
            retriever.setDataSource(filePath);
            bitmap = retriever.captureFrame();
        } catch (IllegalArgumentException ex) {
            // Assume this is a corrupt video file
        } catch (RuntimeException ex) {
            // Assume this is a corrupt video file.
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                // Ignore failures while cleaning up.
            }
        }
        return bitmap;
    }

    public static int indexOf(String [] array, String s) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(s)) {
                return i;
            }
        }
        return -1;
    }

    public static void closeSiliently(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable t) {
            // do nothing
        }
    }

    public static void closeSiliently(ParcelFileDescriptor c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Throwable t) {
            // do nothing
        }
    }

    /**
     * Make a bitmap from a given Uri.
     *
     * @param uri
     */
    public static Bitmap makeBitmap(int targetWidthOrHeight, Uri uri,
            ContentResolver cr) {
        ParcelFileDescriptor input = null;
        try {
            input = cr.openFileDescriptor(uri, "r");
            return makeBitmap(targetWidthOrHeight, uri, cr, input, null);
        } catch (IOException ex) {
            return null;
        } finally {
            closeSiliently(input);
        }
    }

    public static Bitmap makeBitmap(int targetWidthHeight, Uri uri,
            ContentResolver cr, ParcelFileDescriptor pfd,
            BitmapFactory.Options options) {
        Bitmap b = null;
        try {
            if (pfd == null) pfd = makeInputStream(uri, cr);
            if (pfd == null) return null;
            if (options == null) options = new BitmapFactory.Options();

            FileDescriptor fd = pfd.getFileDescriptor();
            options.inSampleSize = 1;
            if (targetWidthHeight != -1) {
                options.inJustDecodeBounds = true;
                BitmapManager.instance().decodeFileDescriptor(
                        fd, null, options);
                if (options.mCancel || options.outWidth == -1
                        || options.outHeight == -1) {
                    return null;
                }
                options.inSampleSize =
                        computeSampleSize(options, targetWidthHeight);
                options.inJustDecodeBounds = false;
            }

            options.inDither = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            b = BitmapManager.instance()
                    .decodeFileDescriptor(fd, null, options);
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Got oom exception ", ex);
            return null;
        } finally {
            closeSiliently(pfd);
        }
        return b;
    }

    private static ParcelFileDescriptor makeInputStream(
            Uri uri, ContentResolver cr) {
        try {
            return cr.openFileDescriptor(uri, "r");
        } catch (IOException ex) {
            return null;
        }
    }
}
