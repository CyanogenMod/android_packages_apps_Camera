/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.camera.R;

public class CameraSwitcher extends RotateImageView
        implements OnClickListener, OnTouchListener {

    private static final String TAG = "CAM_Switcher";

    public interface CameraSwitchListener {

        public void onCameraSelected(int i);
    }

    private CameraSwitchListener mListener;
    private int mCurrentIndex;
    private int[] mDrawIds;
    private int mItemSize;
    private View mPopup;
    private View mParent;

    public CameraSwitcher(Context context) {
        super(context);
        init(context);
    }

    public CameraSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mItemSize = context.getResources().getDimensionPixelSize(R.dimen.switcher_size);
        setOnClickListener(this);
    }

    public void setDrawIds(int[] drawids) {
        mDrawIds = drawids;
    }

    public void setCurrentIndex(int i) {
        mCurrentIndex = i;
        setImageResource(mDrawIds[i]);
    }

    public void setSwitchListener(CameraSwitchListener l) {
        mListener = l;
    }

    @Override
    public void onClick(View v) {
        showSwitcher();
    }

    private void onCameraSelected(int ix) {
        hidePopup();
        if ((ix != mCurrentIndex) && (mListener != null)) {
            setCurrentIndex(ix);
            mListener.onCameraSelected(ix);
        }
    }

    private void initPopup() {
        mParent = LayoutInflater.from(getContext()).inflate(R.layout.switcher_popup,
                (ViewGroup) getParent());
        LinearLayout content = (LinearLayout) mParent.findViewById(R.id.content);
        mPopup = content;
        mPopup.setVisibility(View.GONE);
        for (int i = 0; i < mDrawIds.length; i++) {
            RotateImageView item = new RotateImageView(getContext());
            item.setImageResource(mDrawIds[i]);
            final int index = i;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onCameraSelected(index);
                }
            });
            content.addView(item, new LinearLayout.LayoutParams(mItemSize, mItemSize));
        }
    }

    public boolean showsPopup() {
        return (mPopup != null) && (mPopup.getVisibility() == View.VISIBLE);
    }

    public boolean isInsidePopup(MotionEvent evt) {
        if (mPopup == null) return false;
        return (mPopup.getVisibility() == View.VISIBLE && evt.getX() >= mPopup.getLeft()
                && evt.getX() < mPopup.getRight() && evt.getY() >= mPopup.getTop()
                && evt.getY() < mPopup.getBottom());
    }

    private void hidePopup() {
        if (mPopup != null) {
            mPopup.setVisibility(View.GONE);
            setVisibility(View.VISIBLE);
        }
        mParent.setOnTouchListener(null);
    }

    private void showSwitcher() {
        if (mPopup == null) {
            initPopup();
        }
        setVisibility(View.GONE);
        mPopup.setVisibility(View.VISIBLE);
        mParent.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (showsPopup()) {
            hidePopup();
        }
        return true;
    }

    @Override
    public void setOrientation(int degree, boolean animate) {
        super.setOrientation(degree, animate);
        ViewGroup content = (ViewGroup) mPopup;
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            RotateImageView iv = (RotateImageView) content.getChildAt(i);
            iv.setOrientation(degree, animate);
        }
    }
}
