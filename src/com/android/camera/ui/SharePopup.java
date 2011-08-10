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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// A popup window that contains a big thumbnail and a list of apps to share.
public class SharePopup extends PopupWindow implements View.OnClickListener,
        View.OnTouchListener, AdapterView.OnItemClickListener {
    private static final String TAG = "SharePopup";
    private Context mContext;
    private Uri mUri;
    private String mMimeType;
    private ImageView mThumbnail;
    private int mBitmapWidth;
    private int mBitmapHeight;
    // A view that contains a thumbnail and a share view.
    private ViewGroup mRootView;
    // A view that contains the title and the list of applications to share.
    private View mShareView;
    // The list of the applications to share.
    private ListView mShareList;
    // A rotated view that contains the share view.
    private RotateLayout mShareViewRotateLayout;
    // A rotated view that contains the thumbnail.
    private RotateLayout mThumbnailRotateLayout;
    private ArrayList<ComponentName> mComponent = new ArrayList<ComponentName>();

    // The maximum width of the thumbnail in landscape orientation.
    private final float mImageMaxWidthLandscape;
    // The maximum height of the thumbnail in landscape orientation.
    private final float mImageMaxHeightLandscape;
    // The maximum width of the thumbnail in portrait orientation.
    private final float mImageMaxWidthPortrait;
    // The maximum height of the thumbnail in portrait orientation.
    private final float mImageMaxHeightPortrait;
    // The width of the share list in landscape mode.
    private final float mShareListWidthLandscape;

    public SharePopup(Activity activity, Uri uri, Bitmap bitmap, String mimeType, int orientation,
            View anchor) {
        super(activity);

        // Initialize variables
        mContext = activity;
        mUri = uri;
        mMimeType = mimeType;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup sharePopup = (ViewGroup) inflater.inflate(R.layout.share_popup, null, false);
        // This is required because popup window is full screen.
        sharePopup.setOnTouchListener(this);
        mShareViewRotateLayout = (RotateLayout) sharePopup.findViewById(R.id.share_view_rotate_layout);
        mThumbnailRotateLayout = (RotateLayout) sharePopup.findViewById(R.id.thumbnail_rotate_layout);
        mShareList = (ListView) sharePopup.findViewById(R.id.share_list);
        mShareView = sharePopup.findViewById(R.id.share_view);
        mThumbnail = (ImageView) sharePopup.findViewById(R.id.thumbnail);
        mRootView = (ViewGroup) sharePopup.findViewById(R.id.root);
        mThumbnail.setImageBitmap(bitmap);
        mThumbnail.setOnClickListener(this);
        mBitmapWidth = bitmap.getWidth();
        mBitmapHeight = bitmap.getHeight();
        Resources res = mContext.getResources();
        mImageMaxWidthLandscape = res.getDimension(R.dimen.share_image_max_width_landscape);
        mImageMaxHeightLandscape = res.getDimension(R.dimen.share_image_max_height_landscape);
        mImageMaxWidthPortrait = res.getDimension(R.dimen.share_image_max_width_portrait);
        mImageMaxHeightPortrait = res.getDimension(R.dimen.share_image_max_height_portrait);
        mShareListWidthLandscape = res.getDimension(R.dimen.share_list_width_landscape);

        // Initialize popup window
        setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        setHeight(WindowManager.LayoutParams.MATCH_PARENT);
        setBackgroundDrawable(new ColorDrawable());
        setContentView(sharePopup);
        setOrientation(orientation);
        setFocusable(true);
        setAnimationStyle(R.style.AnimationPopup);

        initializeLocation(activity, anchor);

        createShareMenu();
    }

    private void initializeLocation(Activity activity, View anchor) {
        int location[] = new int[2];
        anchor.getLocationOnScreen(location);
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        MarginLayoutParams params = (MarginLayoutParams) mRootView.getLayoutParams();
        params.topMargin = location[1];
        params.rightMargin = (metrics.widthPixels - location[0] - anchor.getWidth());
        mRootView.setLayoutParams(params);
    }

    public void setOrientation(int orientation) {
        // Calculate the width and the height of the thumbnail.
        float maxWidth, maxHeight;
        if (orientation == 90 || orientation == 270) {
            maxWidth = mImageMaxWidthPortrait;
            maxHeight = mImageMaxHeightPortrait;
        } else {
            maxWidth = mImageMaxWidthLandscape;
            maxHeight = mImageMaxHeightLandscape;
        }
        float actualAspect = maxWidth / maxHeight;
        float desiredAspect = (float) mBitmapWidth / mBitmapHeight;

        LayoutParams params = mThumbnail.getLayoutParams();
        if (actualAspect > desiredAspect) {
            params.width = Math.round(maxHeight * desiredAspect);
            params.height = Math.round(maxHeight);
        } else {
            params.width = Math.round(maxWidth);
            params.height = Math.round(maxWidth / desiredAspect);
        }
        mThumbnail.setLayoutParams(params);

        // Calculate the width of the share application list.
        LayoutParams shareListParams = mShareView.getLayoutParams();
        if ((orientation == 90 || orientation == 270)) {
             shareListParams.width = params.width;
        } else {
             shareListParams.width = (int) mShareListWidthLandscape;
        }
        mShareView.setLayoutParams(shareListParams);
        if (mShareViewRotateLayout != null) mShareViewRotateLayout.setOrientation(orientation);
        if (mThumbnailRotateLayout != null) mThumbnailRotateLayout.setOrientation(orientation);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.thumbnail:
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

    public void createShareMenu() {
        PackageManager packageManager = mContext.getPackageManager();
        List<ResolveInfo> infos;
        infos = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_SEND).setType(mMimeType), 0);

        ArrayList<HashMap<String, Object>> listItem =
                new ArrayList<HashMap<String, Object>>();
        for(ResolveInfo info: infos) {
            String label = info.loadLabel(packageManager).toString();
            ComponentName component = new ComponentName(
                    info.activityInfo.packageName, info.activityInfo.name);
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("text", label);
            listItem.add(map);
            mComponent.add(component);
        }
        SimpleAdapter listItemAdapter = new SimpleAdapter(mContext, listItem,
                R.layout.share_item,
                new String[] {"text"},
                new int[] {R.id.text});
        mShareList.setAdapter(listItemAdapter);
        mShareList.setOnItemClickListener(this);
    }

    public Uri getUri() {
        return mUri;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mMimeType);
        intent.putExtra(Intent.EXTRA_STREAM, mUri);
        intent.setComponent(mComponent.get(index));
        mContext.startActivity(intent);
    }
}
