/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.camera.ui.PopupManager;
import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.TwoStateImageView;

/**
 * A widget that includes three mode selections {@code RotateImageView}'s and
 * a current mode indicator.
 */
public class ModePicker extends RelativeLayout implements View.OnClickListener,
    PopupManager.OnOtherPopupShowedListener, Rotatable {
    public static final int MODE_CAMERA = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_PANORAMA = 2;

    // Total mode number
    private static final int MODE_NUM = 3;

    /** A callback to be called when the user wants to switch activity. */
    public interface OnModeChangeListener {
        public void onModeChanged(int newMode);
    }

    private final int DISABLED_COLOR;

    private OnModeChangeListener mListener;
    private View mModeSelectionFrame;
    private RotateImageView mModeSelectionIcon[];
    private View mCurrentModeFrame;
    private RotateImageView mCurrentModeIcon[];
    private View mCurrentModeBar;
    private boolean mSelectionEnabled;
    private boolean mModeChanged;


    private int mCurrentMode = 0;
    private Animation mFadeIn, mFadeOut;

    public ModePicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        DISABLED_COLOR = context.getResources().getColor(R.color.icon_disabled_color);
        mFadeIn = AnimationUtils.loadAnimation(
                context, R.anim.mode_selection_fade_in);
        mFadeOut = AnimationUtils.loadAnimation(
                context, R.anim.mode_selection_fade_out);
        mFadeOut.setAnimationListener(mAnimationListener);
        PopupManager.getInstance(context).setOnOtherPopupShowedListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mModeSelectionFrame = findViewById(R.id.mode_selection);
        mModeSelectionIcon = new RotateImageView[MODE_NUM];
        mModeSelectionIcon[MODE_PANORAMA] =
                (RotateImageView) findViewById(R.id.mode_panorama);
        mModeSelectionIcon[MODE_VIDEO] =
                (RotateImageView) findViewById(R.id.mode_video);
        mModeSelectionIcon[MODE_CAMERA] =
                (RotateImageView) findViewById(R.id.mode_camera);

        // The current mode frame is for Phone UI only.
        mCurrentModeFrame = findViewById(R.id.current_mode);
        if (mCurrentModeFrame != null) {
            mCurrentModeIcon = new RotateImageView[MODE_NUM];
            mCurrentModeIcon[0] = (RotateImageView) findViewById(R.id.mode_0);
            mCurrentModeIcon[1] = (RotateImageView) findViewById(R.id.mode_1);
            mCurrentModeIcon[2] = (RotateImageView) findViewById(R.id.mode_2);
        } else {
            // current_mode_bar is only for tablet.
            mCurrentModeBar = findViewById(R.id.current_mode_bar);
            enableModeSelection(true);
        }
        registerOnClickListener();
    }

    private void registerOnClickListener() {
        if (mCurrentModeFrame != null) {
            mCurrentModeFrame.setOnClickListener(this);
        }
        for (int i = 0; i < MODE_NUM; ++i) {
            mModeSelectionIcon[i].setOnClickListener(this);
        }
    }

    @Override
    public void onOtherPopupShowed() {
        dismissModeSelection();
    }

    private AnimationListener mAnimationListener = new AnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            if (mModeChanged) {
                // There is a new activity created when changing mode. Fade-in animation at
                // old activity would be interrupted and new mode picker appears.
                // It looks weird so fade-in animation is not used here.
                mCurrentModeFrame.setVisibility(View.VISIBLE);
            } else {
                Util.fadeIn(mCurrentModeFrame, 0.3F, 1F, 100);
            }
            mModeSelectionFrame.setVisibility(View.GONE);
            changeToSelectedMode();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    };

    private void enableModeSelection(boolean enabled) {
        if (mCurrentModeFrame != null) {
            mSelectionEnabled = enabled;
            // Animation is applied on Phone UI only.
            mModeSelectionFrame.clearAnimation();
            mModeSelectionFrame.startAnimation(enabled ? mFadeIn : mFadeOut);
            if (enabled) {
                mModeSelectionFrame.setVisibility(View.VISIBLE);
                // Make sure the animation is stopped. It looks like the view
                // will still be drawn during animation even though the
                // visibility has been set to gone.
                mCurrentModeFrame.clearAnimation();
                mCurrentModeFrame.setVisibility(View.GONE);
            }
        }
        updateModeState();
    }

    private void changeToSelectedMode() {
        if (mListener != null) mListener.onModeChanged(mCurrentMode);
        mModeChanged = false;
    }

    @Override
    public void onClick(View view) {
        if (view == mCurrentModeFrame) {
            PopupManager.getInstance(getContext()).notifyShowPopup(this);
            enableModeSelection(true);
        } else {
            // Set the selected mode as the current one and switch to it.
            for (int i = 0; i < MODE_NUM; ++i) {
                if (view == mModeSelectionIcon[i] && (mCurrentMode != i)) {
                    setCurrentMode(i);
                    mModeChanged = true;
                    break;
                }
            }
            if (mCurrentModeBar == null) {
                enableModeSelection(false);
            } else {
                changeToSelectedMode();
            }
        }
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        mListener = listener;
    }

    public void setCurrentMode(int mode) {
        mCurrentMode = mode;

        // Do not grey out the icons when taking a picture.
        boolean enbaled = true;
        if (mCurrentMode == MODE_CAMERA) enbaled = false;
        if (mCurrentModeFrame != null) {
            for (TwoStateImageView v: mCurrentModeIcon) v.enableFilter(enbaled);
        }
        for (TwoStateImageView v: mModeSelectionIcon) v.enableFilter(enbaled);

        updateModeState();
    }

    public boolean onModeChanged(int mode) {
        setCurrentMode(mode);
        return true;
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        for (int i = 0; i < MODE_NUM; ++i) {
            mModeSelectionIcon[i].setOrientation(orientation, animation);
            if (mCurrentModeFrame != null) {
                mCurrentModeIcon[i].setOrientation(orientation, animation);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        // Enable or disable the frames.
        if (mCurrentModeFrame != null) mCurrentModeFrame.setEnabled(enabled);
        mModeSelectionFrame.setEnabled(enabled);

        // Enable or disable the icons.
        for (int i = 0; i < MODE_NUM; ++i) {
            mModeSelectionIcon[i].setEnabled(enabled);
            if (mCurrentModeFrame != null) mCurrentModeIcon[i].setEnabled(enabled);
        }
        if (enabled) updateModeState();
    }

    private void highlightView(ImageView view, boolean enabled) {
        if (enabled) {
            view.clearColorFilter();
        } else {
            view.setColorFilter(DISABLED_COLOR, PorterDuff.Mode.SRC_ATOP);
        }
    }

    private void updateModeState() {
        // Grey-out the unselected icons for Phone UI.
        if (mCurrentModeFrame != null) {
            for (int i = 0; i < MODE_NUM; ++i) {
                highlightView(mModeSelectionIcon[i], (i == mCurrentMode));
            }
        }

        // Update the current mode icons on the Phone UI. The selected mode
        // should be in the center of the current mode icon bar.
        if (mCurrentModeFrame != null) {
            for (int i = 0, j = 0; i < MODE_NUM; ++i) {
                int target;
                if (i == 1) {
                    // The second icon is always the selected mode.
                    target = mCurrentMode;
                } else {
                    // Set the icons in order of camera, video and panorama.
                    if (j == mCurrentMode) j++;
                    target = j++;
                }
                mCurrentModeIcon[i].setImageDrawable(
                        mModeSelectionIcon[target].getDrawable());
            }
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Layout the current mode indicator bar, which is only for tablet.
        if (mCurrentModeBar != null) {
            if (getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                // tablet in landscape orientation
                int viewWidth = mModeSelectionIcon[MODE_CAMERA].getWidth();
                int iconWidth = ((ImageView) mModeSelectionIcon[MODE_CAMERA])
                        .getDrawable().getIntrinsicWidth();
                int padding = (viewWidth - iconWidth) / 2;
                int l = mModeSelectionFrame.getLeft() + mCurrentMode * viewWidth;
                mCurrentModeBar.layout((l + padding),
                        (bottom - top - mCurrentModeBar.getHeight()),
                        (l + padding + iconWidth),
                        (bottom - top));
            } else {
                // tablet in portrait orientation
                int viewHeight = mModeSelectionIcon[MODE_CAMERA].getHeight();
                int iconHeight = ((ImageView) mModeSelectionIcon[MODE_CAMERA])
                        .getDrawable().getIntrinsicHeight();
                int padding = (viewHeight - iconHeight) / 2;
                int l = mModeSelectionFrame.getTop() + mCurrentMode * viewHeight;
                mCurrentModeBar.layout(0,
                        (l + padding),
                        mCurrentModeBar.getWidth(),
                        (l + padding + iconHeight));
            }
        }
    }

    public boolean dismissModeSelection() {
        // Dismiss the selection if exists.
        if (mSelectionEnabled) {
            enableModeSelection(false);
            return true;
        }
        return false;
    }
}
