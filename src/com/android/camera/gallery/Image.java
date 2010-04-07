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
import android.net.Uri;

/**
 * The class for normal images in gallery.
 */
public class Image extends BaseImage implements IImage {
    private static final String TAG = "BaseImage";

    private final int mRotation;

    public Image(ContentResolver cr,
            long id, Uri uri, long miniThumbMagic,
            long dateTaken,
            int rotation) {
        super(cr, id, uri, miniThumbMagic,
                dateTaken);
        mRotation = rotation;
    }

    @Override
    public int getDegreesRotated() {
        return mRotation;
    }
}
