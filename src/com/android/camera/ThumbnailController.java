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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

// A controller shows thumbnail picture on a button. The thumbnail picture
// corresponds to a URI of the original picture/video. The thumbnail bitmap
// and the URI can be saved to a file (and later loaded from it).
//
//    public ThumbnailController(ImageView button)
//    public void setData(Uri uri, Bitmap original)
//    public void updateDisplayIfNeeded()
//    public Uri getUri()
//    public Bitmap getThumb()
//    public boolean storeData(String filePath)
//    public boolean loadData(String filePath)
//

public class ThumbnailController {
    private static final String TAG = "ThumbnailController";
    private ContentResolver mContentResolver;
    private Uri mUri;
    private Bitmap mThumb;
    private ImageView mButton;
    private Drawable mFrame;
    private Drawable[] mThumbs;
    private TransitionDrawable mThumbTransition;
    private boolean mShouldAnimateThumb;
    private Animation mShowButtonAnimation = new AlphaAnimation(0F, 1F);
    private boolean mShouldAnimateButton;

    // The "frame" is a drawable we want to put on top of the thumbnail.
    public ThumbnailController(ImageView button, Drawable frame,
                           ContentResolver contentResolver) {
        mButton = button;
        mFrame = frame;
        mContentResolver = contentResolver;
        mShowButtonAnimation.setDuration(500);
    }

    public void setData(Uri uri, Bitmap original) {
        // Make sure uri and original are consistently both null or both non-null.
        if (uri == null || original == null) {
            uri = null;
            original = null;
        }
        mUri = uri;
        updateThumb(original);
    }

    public Uri getUri() {
        return mUri;
    }

    public Bitmap getThumb() {
        return mThumb;
    }

    private static final int BUFSIZE = 4096;

    // Stores the data from the specified file.
    // Returns true for success.
    public boolean storeData(String filePath) {
        if (mUri == null) {
            return false;
        }

        FileOutputStream f = null;
        BufferedOutputStream b = null;
        DataOutputStream d = null;
        try {
            f = new FileOutputStream(filePath);
            b = new BufferedOutputStream(f, BUFSIZE);
            d = new DataOutputStream(b);
            d.writeUTF(mUri.toString());
            mThumb.compress(Bitmap.CompressFormat.PNG, 100, d);
            d.close();
        } catch (IOException e) {
            return false;
        } finally {
            closeSilently(f);
            closeSilently(b);
            closeSilently(d);
        }
        return true;
    }

    // Loads the data from the specified file.
    // Returns true for success.
    public boolean loadData(String filePath) {
        FileInputStream f = null;
        BufferedInputStream b = null;
        DataInputStream d = null;
        try {
            f = new FileInputStream(filePath);
            b = new BufferedInputStream(f, BUFSIZE);
            d = new DataInputStream(b);
            Uri uri = Uri.parse(d.readUTF());
            Bitmap thumb = BitmapFactory.decodeStream(d);
            setData(uri, thumb);
            d.close();
        } catch (IOException e) {
            return false;
        } finally {
            closeSilently(f);
            closeSilently(b);
            closeSilently(d);
        }
        return true;
    }

    private void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public void updateDisplayIfNeeded() {
        if (mUri == null) {
            mButton.setVisibility(View.INVISIBLE);
            return;
        }

        if (mShouldAnimateButton) {
            mButton.setVisibility(View.VISIBLE);
            mButton.startAnimation(mShowButtonAnimation);
            mShouldAnimateButton = false;
        }

        if (mShouldAnimateThumb) {
            mThumbTransition.startTransition(500);
            mShouldAnimateThumb = false;
        }
    }

    private void updateThumb(Bitmap original) {
        if (original == null) {
            mThumb = null;
            mThumbs = null;
            return;
        }

        // Make the mini-thumb size smaller than the button size so that the image corners
        // don't peek out from the rounded corners of the frame_thumb graphic:
        final int PADDING_WIDTH = 12;
        final int PADDING_HEIGHT = 12;
        LayoutParams layoutParams = mButton.getLayoutParams();
        final int miniThumbWidth = layoutParams.width - 2 * PADDING_WIDTH;
        final int miniThumbHeight = layoutParams.height - 2 * PADDING_HEIGHT;
        mThumb = ImageManager.extractMiniThumb(
                original, miniThumbWidth, miniThumbHeight, false);

        Drawable[] vignetteLayers = new Drawable[2];
        vignetteLayers[0] = mFrame;
        if (mThumbs == null) {
            mThumbs = new Drawable[2];
            mThumbs[1] = new BitmapDrawable(mThumb);
            vignetteLayers[1] = mThumbs[1];
            mShouldAnimateThumb = false;
        } else {
            mThumbs[0] = mThumbs[1];
            mThumbs[1] = new BitmapDrawable(mThumb);
            mThumbTransition = new TransitionDrawable(mThumbs);
            vignetteLayers[1] = mThumbTransition;
            mShouldAnimateThumb = true;
        }

        LayerDrawable mVignette = new LayerDrawable(vignetteLayers);
        mVignette.setLayerInset(1, PADDING_WIDTH, PADDING_HEIGHT,
                PADDING_WIDTH, PADDING_HEIGHT);
        mButton.setImageDrawable(mVignette);

        if (mButton.getVisibility() != View.VISIBLE) {
            mShouldAnimateButton = true;
        }
    }

    public boolean isUriValid() {
        if (mUri == null) return false;
        try {
            mContentResolver.openFileDescriptor(mUri, "r").close();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }
}
