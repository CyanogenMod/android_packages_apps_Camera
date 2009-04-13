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

import android.net.Uri;

import java.io.InputStream;

/**
 * A simple version of <code>BaseImage</code>.
 */
public abstract class SimpleBaseImage implements IImage {
    public void commitChanges() {
        throw new UnsupportedOperationException();
    }

    public InputStream fullSizeImageData() {
        throw new UnsupportedOperationException();
    }

    public long fullSizeImageId() {
        return 0;
    }

    public Uri fullSizeImageUri() {
        throw new UnsupportedOperationException();
    }

    public IImageList getContainer() {
        return null;
    }

    public long getDateTaken() {
        return 0;
    }

    public String getMimeType() {
        throw new UnsupportedOperationException();
    }

    public double getLatitude() {
        return 0D;
    }

    public double getLongitude() {
        return 0D;
    }

    public String getTitle() {
        throw new UnsupportedOperationException();
    }

    public String getDisplayName() {
        throw new UnsupportedOperationException();
    }

    public int getRow() {
        throw new UnsupportedOperationException();
    }

    public int getHeight() {
        return 0;
    }

    public int getWidth() {
        return 0;
    }

    public boolean hasLatLong() {
        return false;
    }

    public boolean isReadonly() {
        return true;
    }

    public boolean isDrm() {
        return false;
    }

    public void onRemove() {
        throw new UnsupportedOperationException();
    }

    public boolean rotateImageBy(int degrees) {
        return false;
    }

    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    public Uri thumbUri() {
        throw new UnsupportedOperationException();
    }
}