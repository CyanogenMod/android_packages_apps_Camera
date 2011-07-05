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

package com.android.camera.ui;

import com.android.camera.R;
import com.android.camera.Util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.PopupWindow;

// A popup window that contains a big thumbnail and a list of apps to share.
public class SharePopup extends PopupWindow implements View.OnClickListener,
        View.OnTouchListener {
    private Context mContext;
    private Uri mUri;

    // The maximum width of the thumbnail in landscape orientation.
    private final float mImageMaxWidthLandscape;
    // The maximum height of the thumbnail in landscape orientation.
    private final float mImageMaxHeightLandscape;
    // The maximum width of the thumbnail in portrait orientation.
    private final float mImageMaxWidthPortrait;
    // The maximum height of the thumbnail in portrait orientation.
    private final float mImageMaxHeightPortrait;

    private ImageView mThumbnail;
    private int mBitmapWidth, mBitmapHeight;
    private RotateLayout mRotateLayout;

    public SharePopup(Activity activity, Uri uri, Bitmap bitmap, int orientation, View anchor) {
        super(activity);

        // Initailize variables
        mContext = activity;
        mUri = uri;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View contentView = inflater.inflate(R.layout.share_popup, null, false);
        // This is required because popup window is full screen.
        contentView.setOnTouchListener(this);
        mRotateLayout = (RotateLayout) contentView.findViewById(R.id.rotate_layout);
        mThumbnail = (ImageView) contentView.findViewById(R.id.expanded_thumbnail);
        mThumbnail.setImageBitmap(bitmap);
        mThumbnail.setOnClickListener(this);
        mBitmapWidth = bitmap.getWidth();
        mBitmapHeight = bitmap.getHeight();
        Resources res = mContext.getResources();
        mImageMaxWidthLandscape = res.getDimension(R.dimen.share_image_max_width_landscape);
        mImageMaxHeightLandscape = res.getDimension(R.dimen.share_image_max_height_landscape);
        mImageMaxWidthPortrait = res.getDimension(R.dimen.share_image_max_width_portrait);
        mImageMaxHeightPortrait = res.getDimension(R.dimen.share_image_max_height_portrait);

        // Initialize popup window
        setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        setHeight(WindowManager.LayoutParams.MATCH_PARENT);
        setBackgroundDrawable(new ColorDrawable());
        setContentView(contentView);
        setOrientation(orientation);

        // Initialize view location
        int location[] = new int[2];
        anchor.getLocationOnScreen(location);
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        MarginLayoutParams params = (MarginLayoutParams) mRotateLayout.getLayoutParams();
        params.topMargin = location[1] / 2;
        params.rightMargin = (metrics.widthPixels - location[0] - anchor.getWidth()) / 2;
        mRotateLayout.setLayoutParams(params);

        // Start animation
        Animation fadeIn = AnimationUtils.loadAnimation(mContext, R.anim.grow_fade_in_from_right);
        mRotateLayout.startAnimation(fadeIn);
    }

    public void setOrientation(int orientation) {
        // Calculate the width and the height of the thumbnail.
        float width, height;
        if (orientation == 90 || orientation == 270) {
            width = mImageMaxWidthPortrait;
            height = mImageMaxHeightPortrait;
        } else {
            width = mImageMaxWidthLandscape;
            height = mImageMaxHeightLandscape;
        }
        LayoutParams params = mThumbnail.getLayoutParams();
        if (width * mBitmapHeight > height * mBitmapWidth) {
            params.width = Math.round(mBitmapWidth * height / mBitmapHeight);
            params.height = Math.round(height);
        } else {
            params.width = Math.round(width);
            params.height = Math.round(mBitmapHeight * params.width / mBitmapWidth);
        }
        mThumbnail.setLayoutParams(params);
        mRotateLayout.setOrientation(orientation);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.expanded_thumbnail:
                Util.viewUri(mUri, mContext);
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            dismiss();
            return true;
        }
        return false;
    }
}
