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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DrmStore;

/**
 * Represents an ordered collection of Image objects from the DRM provider.
 */
public class DrmImageList extends ImageList implements IImageList {

    private static final String[] DRM_IMAGE_PROJECTION = new String[] {
        DrmStore.Audio._ID,
        DrmStore.Audio.DATA,
        DrmStore.Audio.MIME_TYPE,
    };

    public DrmImageList(Context ctx, ContentResolver cr, Uri imageUri,
            int sort, String bucketId) {
        super(ctx, cr, imageUri, null, sort, bucketId);
    }

    @Override
    protected Cursor createCursor() {
        return mContentResolver.query(
                mBaseUri, DRM_IMAGE_PROJECTION, null, null, sortOrder());
    }

    @Override
    public void checkThumbnails(
            IImageList.ThumbCheckCallback cb, int totalCount) {
        // do nothing
    }

    @Override
    public long checkThumbnail(BaseImage existingImage, Cursor c, int i) {
        return 0;
    }

    private class DrmImage extends Image {

        protected DrmImage(long id, ContentResolver cr,
                BaseImageList container, int cursorRow) {
            super(id, 0, cr, container, cursorRow, 0);
        }

        @Override
        public boolean isDrm() {
            return true;
        }

        @Override
        public boolean isReadonly() {
            return true;
        }

        @Override
        public Bitmap miniThumbBitmap() {
            return fullSizeBitmap(ImageManager.MINI_THUMB_TARGET_SIZE);
        }

        @Override
        public Bitmap thumbBitmap() {
            return fullSizeBitmap(ImageManager.THUMBNAIL_TARGET_SIZE);
        }

        @Override
        public String getDisplayName() {
            return getTitle();
        }
    }

    @Override
    protected IImage make(long id, long miniThumbId, ContentResolver cr,
            IImageList list, long timestamp, int index, int rotation) {
        return new DrmImage(id, mContentResolver, this, index);
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
    protected int indexDescription() {
        return -1;
    }

    @Override
    protected int indexMimeType() {
        return -1;
    }

    @Override
    protected int indexId() {
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

    // TODO: Review this probably should be based on DATE_TAKEN same as images
    private String sortOrder() {
        String ascending =
                mSort == ImageManager.SORT_ASCENDING ? " ASC" : " DESC";
        return DrmStore.Images.TITLE  + ascending + "," + DrmStore.Images._ID;
    }
}