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

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ResourceCursorAdapter;
import android.util.Log;

public class ThumbnailAdapter extends ResourceCursorAdapter {
    private final String TAG = "ThumbnailAdapter";
    private int idIndex;
    private boolean mIsImage;
    public ThumbnailAdapter(Context context, int layout, Cursor c,
            boolean isImage) {
        super(context, layout, c, false);
        mIsImage = isImage;
        if (mIsImage) {
            idIndex = c.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID);
        } else {
            idIndex = c.getColumnIndexOrThrow(MediaStore.Video.Thumbnails._ID);
        }
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        int id = cursor.getInt(idIndex);
        Bitmap b;
        Uri uri;
        RotateImageView v = (RotateImageView) view;
        if (mIsImage) {
            uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "" + id);
            if (!uri.equals(v.getUri())) {
                b = MediaStore.Images.Thumbnails.getThumbnail(
                        context.getContentResolver(), id,
                        MediaStore.Images.Thumbnails.MINI_KIND, null);
                v.setData(uri, b);
            }
        } else {
            uri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id);
            if (!uri.equals(v.getUri())) {
                b = MediaStore.Video.Thumbnails.getThumbnail(
                        context.getContentResolver(), id,
                        MediaStore.Video.Thumbnails.MINI_KIND, null);
                v.setData(uri, b);
            }
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = super.newView(context, cursor, parent);
        ((RotateImageView)view).enableAnimation(false);
        return view;
    }
}
