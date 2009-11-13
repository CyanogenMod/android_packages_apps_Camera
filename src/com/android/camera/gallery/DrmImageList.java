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
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DrmStore;

/**
 * Represents an ordered collection of Image objects from the DRM provider.
 */
public class DrmImageList extends ImageList implements IImageList {

    // TODO: get other field from database too ?
    private static final String[] DRM_IMAGE_PROJECTION = new String[] {
        DrmStore.Images._ID,
        DrmStore.Images.DATA,
        DrmStore.Images.MIME_TYPE,
    };

    private static final int INDEX_ID = 0;
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_MIME_TYPE = 2;

    public DrmImageList(ContentResolver resolver, Uri imageUri, int sort,
            String bucketId) {
        super(resolver, imageUri, null, sort, bucketId);
    }

    @Override
    protected String sortOrder() {
        // We have no date information in DrmStore, so we just sort by _id.
        return "_id ASC";
    }

    @Override
    protected Cursor createCursor() {
        return mContentResolver.query(
                mBaseUri, DRM_IMAGE_PROJECTION, null, null, sortOrder());
    }

    private static class DrmImage extends Image {

        protected DrmImage(BaseImageList container, ContentResolver cr,
                long id, int index, Uri uri, String dataPath,
                long miniThumbMagic, String mimeType, long dateTaken,
                String title, String displayName, int rotation) {
            super(container, cr, id, index, uri, dataPath, miniThumbMagic,
                    mimeType, dateTaken, title, displayName, rotation);
        }

        @Override
        public int getDegreesRotated() {
            return 0;
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
            return fullSizeBitmap(IImage.MINI_THUMB_TARGET_SIZE,
                    IImage.MINI_THUMB_MAX_NUM_PIXELS);
        }

        @Override
        public Bitmap thumbBitmap(boolean rotateAsNeeded) {
            return fullSizeBitmap(IImage.THUMBNAIL_TARGET_SIZE,
                    IImage.THUMBNAIL_MAX_NUM_PIXELS);
        }

        @Override
        public String getDisplayName() {
            return getTitle();
        }
    }

    @Override
    protected BaseImage loadImageFromCursor(Cursor cursor) {
        long id = cursor.getLong(INDEX_ID);
        String dataPath = cursor.getString(INDEX_DATA_PATH);
        String mimeType = cursor.getString(INDEX_MIME_TYPE);
        return new DrmImage(this, mContentResolver, id, cursor.getPosition(),
                contentUri(id), dataPath, 0, mimeType, 0, "DrmImage-" + id,
                "DrmImage-" + id, 0);
    }
}
