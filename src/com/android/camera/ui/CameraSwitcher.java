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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.camera.R;
import com.android.gallery3d.common.ApiHelper;

public class CameraSwitcher extends RotateImageView
        implements OnClickListener, OnTouchListener {

    private static final String TAG = "CAM_Switcher";
    private static final int SWITCHER_POPUP_ANIM_DURATION = 200;

    public interface CameraSwitchListener {
        public void onCameraSelected(int i);
        public void onShowSwitcherPopup();
    }

    private CameraSwitchListener mListener;
    private int mCurrentIndex;
    private int[] mDrawIds;
    private int mItemSize;
    private View mPopup;
    private View mParent;
    private boolean mShowingPopup;
    private boolean mNeedsAnimationSetup;
    private Drawable mIndicator;

    private float mTranslationX = 0;
    private float mTranslationY = 0;

    private AnimatorListener mHideAnimationListener;
    private AnimatorListener mShowAnimationListener;

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
        mIndicator = context.getResources().getDrawable(R.drawable.ic_switcher_menu_indicator);
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
        mListener.onShowSwitcherPopup();
    }

    private void onCameraSelected(int ix) {
        hidePopup();
        if ((ix != mCurrentIndex) && (mListener != null)) {
            setCurrentIndex(ix);
            mListener.onCameraSelected(ix);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mIndicator.setBounds(getDrawable().getBounds());
        mIndicator.draw(canvas);
    }

    private void initPopup() {
        mParent = LayoutInflater.from(getContext()).inflate(R.layout.switcher_popup,
                (ViewGroup) getParent());
        LinearLayout content = (LinearLayout) mParent.findViewById(R.id.content);
        mPopup = content;
        mPopup.setVisibility(View.INVISIBLE);
        mNeedsAnimationSetup = true;
        for (int i = mDrawIds.length - 1; i >= 0; i--) {
            RotateImageView item = new RotateImageView(getContext());
            item.setImageResource(mDrawIds[i]);
            item.setBackgroundResource(R.drawable.bg_pressed);
            final int index = i;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onCameraSelected(index);
                }
            });
            switch (mDrawIds[i]) {
                case R.drawable.ic_switch_camera:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_camera));
                    break;
                case R.drawable.ic_switch_video:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_video));
                    break;
                case R.drawable.ic_switch_pan:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_panorama));
                    break;
                case R.drawable.ic_switch_photosphere:
                    item.setContentDescription(getContext().getResources().getString(
                            R.string.accessibility_switch_to_new_panorama));
                    break;
                default:
                    break;
            }
            content.addView(item, new LinearLayout.LayoutParams(mItemSize, mItemSize));
        }
    }

    public boolean showsPopup() {
        return mShowingPopup;
    }

    public boolean isInsidePopup(MotionEvent evt) {
        if (!showsPopup()) return false;
        return evt.getX() >= mPopup.getLeft()
                && evt.getX() < mPopup.getRight()
                && evt.getY() >= mPopup.getTop()
                && evt.getY() < mPopup.getBottom();
    }

    private void hidePopup() {
        mShowingPopup = false;
        setVisibility(View.VISIBLE);
        if (mPopup != null && !animateHidePopup()) {
            mPopup.setVisibility(View.INVISIBLE);
        }
        mParent.setOnTouchListener(null);
    }

    private void showSwitcher() {
        mShowingPopup = true;
        if (mPopup == null) {
            initPopup();
        }
        mPopup.setVisibility(View.VISIBLE);
        if (!animateShowPopup()) {
            setVisibility(View.INVISIBLE);
        }
        mParent.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        closePopup();
        return true;
    }

    public void closePopup() {
        if (showsPopup()) {
            hidePopup();
        }
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

    private void updateInitialTranslations() {
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            mTranslationX = -getWidth() / 2 ;
            mTranslationY = getHeight();
        } else {
            mTranslationX = getWidth();
            mTranslationY = getHeight() / 2;
        }
    }
    private void popupAnimationSetup() {
        if (!ApiHelper.HAS_VIEW_PROPERTY_ANIMATOR) {
            return;
        }
        updateInitialTranslations();
        mPopup.setScaleX(0.3f);
        mPopup.setScaleY(0.3f);
        mPopup.setTranslationX(mTranslationX);
        mPopup.setTranslationY(mTranslationY);
        mNeedsAnimationSetup = false;
    }

    private boolean animateHidePopup() {
        if (!ApiHelper.HAS_VIEW_PROPERTY_ANIMATOR) {
            return false;
        }
        if (mHideAnimationListener == null) {
            mHideAnimationListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Verify that we weren't canceled
                    if (!showsPopup()) {
                        mPopup.setVisibility(View.INVISIBLE);
                    }
                }
            };
        }
        mPopup.animate()
                .alpha(0f)
                .scaleX(0.3f).scaleY(0.3f)
                .translationX(mTranslationX)
                .translationY(mTranslationY)
                .setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(mHideAnimationListener);
        animate().alpha(1f).setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(null);
        return true;
    }

    private boolean animateShowPopup() {
        if (!ApiHelper.HAS_VIEW_PROPERTY_ANIMATOR) {
            return false;
        }
        if (mNeedsAnimationSetup) {
            popupAnimationSetup();
        }
        if (mShowAnimationListener == null) {
            mShowAnimationListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Verify that we weren't canceled
                    if (showsPopup()) {
                        setVisibility(View.INVISIBLE);
                    }
                }
            };
        }
        mPopup.animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .translationX(0)
                .translationY(0)
                .setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(null);
        animate().alpha(0f).setDuration(SWITCHER_POPUP_ANIM_DURATION)
                .setListener(mShowAnimationListener);
        return true;
    }
}
