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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.camera.ui.Rotatable;
import com.android.camera.ui.RotateLayout;

public class RotateDialogController implements Rotatable {

    @SuppressWarnings("unused")
    private static final String TAG = "RotateDialogController";
    private static final long ANIM_DURATION = 150;  // millis

    private Activity mActivity;
    private int mLayoutResourceID;
    private View mDialogRootLayout;
    private RotateLayout mRotateDialog;
    private View mRotateDialogTitleLayout;
    private View mRotateDialogButtonLayout;
    private TextView mRotateDialogTitle;
    private ProgressBar mRotateDialogSpinner;
    private TextView mRotateDialogText;
    private TextView mRotateDialogButton1;
    private TextView mRotateDialogButton2;

    private Animation mFadeInAnim, mFadeOutAnim;

    public RotateDialogController(Activity a, int layoutResource) {
        mActivity = a;
        mLayoutResourceID = layoutResource;
    }

    private void inflateDialogLayout() {
        if (mDialogRootLayout == null) {
            ViewGroup layoutRoot = (ViewGroup) mActivity.getWindow().getDecorView();
            LayoutInflater inflater = mActivity.getLayoutInflater();
            View v = inflater.inflate(mLayoutResourceID, layoutRoot);
            mDialogRootLayout = v.findViewById(R.id.rotate_dialog_root_layout);
            mRotateDialog = (RotateLayout) v.findViewById(R.id.rotate_dialog_layout);
            mRotateDialogTitleLayout = v.findViewById(R.id.rotate_dialog_title_layout);
            mRotateDialogButtonLayout = v.findViewById(R.id.rotate_dialog_button_layout);
            mRotateDialogTitle = (TextView) v.findViewById(R.id.rotate_dialog_title);
            mRotateDialogSpinner = (ProgressBar) v.findViewById(R.id.rotate_dialog_spinner);
            mRotateDialogText = (TextView) v.findViewById(R.id.rotate_dialog_text);
            mRotateDialogButton1 = (Button) v.findViewById(R.id.rotate_dialog_button1);
            mRotateDialogButton2 = (Button) v.findViewById(R.id.rotate_dialog_button2);

            mFadeInAnim = AnimationUtils.loadAnimation(
                    mActivity, android.R.anim.fade_in);
            mFadeOutAnim = AnimationUtils.loadAnimation(
                    mActivity, android.R.anim.fade_out);
            mFadeInAnim.setDuration(ANIM_DURATION);
            mFadeOutAnim.setDuration(ANIM_DURATION);
        }
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        inflateDialogLayout();
        mRotateDialog.setOrientation(orientation, animation);
    }

    public void resetRotateDialog() {
        inflateDialogLayout();
        mRotateDialogTitleLayout.setVisibility(View.GONE);
        mRotateDialogSpinner.setVisibility(View.GONE);
        mRotateDialogButton1.setVisibility(View.GONE);
        mRotateDialogButton2.setVisibility(View.GONE);
        mRotateDialogButtonLayout.setVisibility(View.GONE);
    }

    private void fadeOutDialog() {
        mDialogRootLayout.startAnimation(mFadeOutAnim);
        mDialogRootLayout.setVisibility(View.GONE);
    }

    private void fadeInDialog() {
        mDialogRootLayout.startAnimation(mFadeInAnim);
        mDialogRootLayout.setVisibility(View.VISIBLE);
    }

    public void dismissDialog() {
        if (mDialogRootLayout != null && mDialogRootLayout.getVisibility() != View.GONE) {
            fadeOutDialog();
        }
    }

    public void showAlertDialog(String title, String msg, String button1Text,
                final Runnable r1, String button2Text, final Runnable r2) {
        resetRotateDialog();

        if (title != null) {
            mRotateDialogTitle.setText(title);
            mRotateDialogTitleLayout.setVisibility(View.VISIBLE);
        }

        mRotateDialogText.setText(msg);

        if (button1Text != null) {
            mRotateDialogButton1.setText(button1Text);
            mRotateDialogButton1.setContentDescription(button1Text);
            mRotateDialogButton1.setVisibility(View.VISIBLE);
            mRotateDialogButton1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (r1 != null) r1.run();
                    dismissDialog();
                }
            });
            mRotateDialogButtonLayout.setVisibility(View.VISIBLE);
        }
        if (button2Text != null) {
            mRotateDialogButton2.setText(button2Text);
            mRotateDialogButton2.setContentDescription(button2Text);
            mRotateDialogButton2.setVisibility(View.VISIBLE);
            mRotateDialogButton2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (r2 != null) r2.run();
                    dismissDialog();
                }
            });
            mRotateDialogButtonLayout.setVisibility(View.VISIBLE);
        }

        fadeInDialog();
    }

    public void showWaitingDialog(String msg) {
        resetRotateDialog();

        mRotateDialogText.setText(msg);
        mRotateDialogSpinner.setVisibility(View.VISIBLE);

        fadeInDialog();
    }

}
