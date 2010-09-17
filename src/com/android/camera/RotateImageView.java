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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A @{code ImageView} which can rotate it's content.
 */
public class RotateImageView extends ImageView {

    @SuppressWarnings("unused")
    private static final String TAG = "RotateImageView";

    private static final int ANIMATION_SPEED = 180; // 180 deg/sec

    private int mCurrentDegree = 0; // [0, 359]
    private int mStartDegree = 0;
    private int mTargetDegree = 0;

    private boolean mClockwise = false, mEnableAnimation = true;

    private long mAnimationStartTime = 0;
    private long mAnimationEndTime = 0;

    private Uri mUri;

    public RotateImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void enableAnimation(boolean enable) {
        mEnableAnimation = enable;
    }

    public void setDegree(int degree) {
        // make sure in the range of [0, 359]
        degree = degree >= 0 ? degree % 360 : degree % 360 + 360;
        if (degree == mTargetDegree) return;

        mTargetDegree = degree;
        mStartDegree = mCurrentDegree;
        mAnimationStartTime = AnimationUtils.currentAnimationTimeMillis();

        int diff = mTargetDegree - mCurrentDegree;
        diff = diff >= 0 ? diff : 360 + diff; // make it in range [0, 359]

        // Make it in range [-179, 180]. That's the shorted distance between the
        // two angles
        diff = diff > 180 ? diff - 360 : diff;

        mClockwise = diff >= 0;
        mAnimationEndTime = mAnimationStartTime
                + Math.abs(diff) * 1000 / ANIMATION_SPEED;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable drawable = getDrawable();
        if (drawable == null) return;

        Rect bounds = drawable.getBounds();
        int w = bounds.right - bounds.left;
        int h = bounds.bottom - bounds.top;

        if (w == 0 || h == 0) return; // nothing to draw

        if (mCurrentDegree != mTargetDegree) {
            long time = AnimationUtils.currentAnimationTimeMillis();
            if (time < mAnimationEndTime) {
                int deltaTime = (int)(time - mAnimationStartTime);
                int degree = mStartDegree + ANIMATION_SPEED
                        * (mClockwise ? deltaTime : -deltaTime) / 1000;
                degree = degree >= 0 ? degree % 360 : degree % 360 + 360;
                mCurrentDegree = degree;
                invalidate();
            } else {
                mCurrentDegree = mTargetDegree;
            }
        }

        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getPaddingRight();
        int bottom = getPaddingBottom();
        int width = getWidth() - left - right;
        int height = getHeight() - top - bottom;

        int saveCount = canvas.getSaveCount();
        canvas.translate(left + width / 2, top + height / 2);
        canvas.rotate(-mCurrentDegree);
        canvas.translate(-w / 2, -h / 2);
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    private Bitmap mThumb;
    private Drawable[] mThumbs;
    private TransitionDrawable mThumbTransition;

    public void setData(Uri uri, Bitmap original) {
        // Make sure uri and original are consistently both null or both
        // non-null.
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
            MenuHelper.closeSilently(f);
            MenuHelper.closeSilently(b);
            MenuHelper.closeSilently(d);
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
            MenuHelper.closeSilently(f);
            MenuHelper.closeSilently(b);
            MenuHelper.closeSilently(d);
        }
        return true;
    }

    private void updateThumb(Bitmap original) {
        if (original == null) {
            mThumb = null;
            mThumbs = null;
            setImageDrawable(null);
            return;
        }

        LayoutParams param = getLayoutParams();
        final int miniThumbWidth = param.width
                - getPaddingLeft() - getPaddingRight();
        final int miniThumbHeight = param.height
                - getPaddingTop() - getPaddingBottom();
        mThumb = ThumbnailUtils.extractThumbnail(
                original, miniThumbWidth, miniThumbHeight);
        Drawable drawable;
        if (mThumbs == null || !mEnableAnimation) {
            mThumbs = new Drawable[2];
            mThumbs[1] = new BitmapDrawable(getContext().getResources(), mThumb);
            setImageDrawable(mThumbs[1]);
        } else {
            mThumbs[0] = mThumbs[1];
            mThumbs[1] = new BitmapDrawable(getContext().getResources(), mThumb);
            mThumbTransition = new TransitionDrawable(mThumbs);
            setImageDrawable(mThumbTransition);
            mThumbTransition.startTransition(500);
        }
    }

    public boolean isUriValid() {
        if (mUri == null) {
            return false;
        }
        try {
            ParcelFileDescriptor pfd =
                    getContext().getContentResolver().openFileDescriptor(mUri, "r");
            if (pfd == null) {
                Log.e(TAG, "Fail to open URI. URI=" + mUri);
                return false;
            }
            pfd.close();
        } catch (IOException ex) {
            return false;
        }
        return true;
    }
}
