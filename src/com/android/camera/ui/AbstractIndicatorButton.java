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

package com.android.camera.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.android.camera.R;

// This is an indicator button and pressing it opens a popup window. Ex: flash or other settings.
public abstract class AbstractIndicatorButton extends RotateImageView implements
        PopupManager.OnOtherPopupShowedListener {
    @SuppressWarnings("unused")
    private static final String TAG = "AbstractIndicatorButton";
    protected Animation mFadeIn, mFadeOut;
    protected final int HIGHLIGHT_COLOR;
    protected AbstractSettingPopup mPopup;
    protected Handler mHandler = new MainHandler();
    private final int MSG_DISMISS_POPUP = 0;
    private IndicatorChangeListener mListener;

    public static interface IndicatorChangeListener {
        public void onShowIndicator(View view, boolean showed);
    }

    public AbstractIndicatorButton(Context context) {
        super(context);
        mFadeIn = AnimationUtils.loadAnimation(context, R.anim.setting_popup_grow_fade_in);
        mFadeOut = AnimationUtils.loadAnimation(context, R.anim.setting_popup_shrink_fade_out);
        HIGHLIGHT_COLOR = context.getResources().getColor(R.color.review_control_pressed_color);
        setScaleType(ImageView.ScaleType.CENTER);
        PopupManager.getInstance(context).setOnOtherPopupShowedListener(this);
        // Set the click listener to help the comprehension of the accessibility.
        setClickable(true);
    }

    @Override
    public void onOtherPopupShowed() {
        dismissPopup();
    }

    public void setIndicatorChangeListener(IndicatorChangeListener listener) {
        mListener = listener;
    }

    // Whether scene mode affects this indicator and it cannot be changed.
    public boolean isOverridden() {
        return false;
    }

    // Scene mode may override other settings like flash, white-balance, and focus.
    abstract public void overrideSettings(final String ... keyvalues);

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) return false;

        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN && !isOverridden()) {
            if (mPopup == null || mPopup.getVisibility() != View.VISIBLE) {
                showPopup();
                PopupManager.getInstance(getContext()).notifyShowPopup(this);
            } else {
                dismissPopup();
            }
            return true;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            dismissPopup();
            return true;
        }
        return false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Do not enable the button if it is overridden by scene mode.
        if (isOverridden()) {
            enabled = false;
        }

        // Don't do anything if state is not changed so not to interfere with
        // the "highlight" state.
        if (isEnabled() ^ enabled) {
            super.setEnabled(enabled);
        }
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        super.setOrientation(orientation, animation);
        if (mPopup != null) {
            mPopup.setOrientation(orientation, animation);
        }
    }

    abstract protected void initializePopup();

    private void showPopup() {
        setPressed(true);
        mHandler.removeMessages(MSG_DISMISS_POPUP);
        if (mPopup == null) initializePopup();

        mPopup.setVisibility(View.VISIBLE);
        mPopup.setOrientation(getDegree(), false);
        mPopup.clearAnimation();
        mPopup.startAnimation(mFadeIn);
        if (mListener != null) mListener.onShowIndicator(this, true);
    }

    public boolean dismissPopup() {
        setPressed(false);
        mHandler.removeMessages(MSG_DISMISS_POPUP);
        if (mPopup != null && mPopup.getVisibility() == View.VISIBLE) {
            mPopup.clearAnimation();
            mPopup.startAnimation(mFadeOut);
            mPopup.setVisibility(View.GONE);
            if (mListener != null) mListener.onShowIndicator(this, false);
            invalidate();
            // Indicator wheel needs to update the highlight indicator if this
            // is dismissed by MSG_DISMISS_POPUP.
            ((View) getParent()).invalidate();
            return true;
        }
        return false;
    }

    public AbstractSettingPopup getPopupWindow() {
        if (mPopup != null && mPopup.getVisibility() == View.VISIBLE) {
            return mPopup;
        } else {
            return null;
        }
    }

    public void removePopupWindow() {
        if (mPopup != null) {
            ViewGroup root = (ViewGroup) getRootView().findViewById(R.id.frame_layout);
            root.removeView(mPopup);
            mPopup = null;
        }
    }

    public void reloadPreference() {
        if (mPopup != null) mPopup.reloadPreference();
    }

    protected void dismissPopupDelayed() {
        if (!mHandler.hasMessages(MSG_DISMISS_POPUP)) {
            mHandler.sendEmptyMessage(MSG_DISMISS_POPUP);
        }
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DISMISS_POPUP:
                    dismissPopup();
                    break;
            }
        }
    }
}
