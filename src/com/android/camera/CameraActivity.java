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

package com.android.camera;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.camera.ui.CameraSwitcher;

public class CameraActivity extends ActivityBase
        implements CameraSwitcher.CameraSwitchListener {

    CameraModule mCurrentModule;
    private FrameLayout mFrame;
    private ShutterButton mShutter;
    private CameraSwitcher mSwitcher;
    private Drawable[] mDrawables;
    private int mSelectedModule;

    private static final String TAG = "CAM_activity";

    private static final int[] DRAW_IDS = {
            R.drawable.ic_switch_video_holo_light,
            R.drawable.ic_switch_camera_holo_light,
            R.drawable.ic_switch_pan_holo_light
    };

    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.camera_main);
        mFrame =(FrameLayout) findViewById(R.id.main_content);
        mShutter = (ShutterButton) findViewById(R.id.shutter_button);
        mSwitcher = (CameraSwitcher) findViewById(R.id.camera_switcher);
        mDrawables = new Drawable[DRAW_IDS.length];
        for (int i = 0; i < DRAW_IDS.length; i++) {
            Drawable d = getResources().getDrawable(DRAW_IDS[i]);
            mDrawables[i] = d;
        }
        for (int i = 0; i < mDrawables.length; i++) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(mDrawables[i]);
            mSwitcher.add(iv, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
        }

        mSwitcher.setSwitchListener(this);
        mCurrentModule = new PhotoModule();
        mCurrentModule.init(this, mFrame);
        mSelectedModule = 1;
        mSwitcher.setCurrentModule(mSelectedModule);
    }

    @Override
    public void onScroll() {
    }

    @Override
    public void onCameraSelected(int i) {
        if (i != mSelectedModule) {
            mPaused = true;
            closeModule(mCurrentModule);
            mSelectedModule = i;
            switch (i) {
            case 0:
                mCurrentModule = new VideoModule();
                break;
            case 1:
                mCurrentModule = new PhotoModule();
                break;
            case 2:
                mCurrentModule = new VideoModule();
                break;
            }
            openModule(mCurrentModule);
        }
    }

    private void openModule(CameraModule module) {
        module.init(this, mFrame);
        mPaused = false;
        module.onResumeBeforeSuper();
        module.onResumeAfterSuper();
    }

    private void closeModule(CameraModule module) {
        module.onPauseBeforeSuper();
        module.onPauseAfterSuper();
        mFrame.removeAllViews();
    }

    public ShutterButton getShutterButton() {
        return mShutter;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mCurrentModule.onConfigurationChanged(config);
    }

    @Override
    public void onPause() {
        mPaused = true;
        mCurrentModule.onPauseBeforeSuper();
        super.onPause();
        mCurrentModule.onPauseAfterSuper();
    }

    @Override
    public void onResume() {
        mPaused = false;
        mCurrentModule.onResumeBeforeSuper();
        super.onResume();
        mCurrentModule.onResumeAfterSuper();
    }

    @Override
    protected void onFullScreenChanged(boolean full) {
        super.onFullScreenChanged(full);
        mCurrentModule.onFullScreenChanged(full);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mCurrentModule.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getStateManager().clearActivityResult();
    }

    @Override
    protected void installIntentFilter() {
        super.installIntentFilter();
        mCurrentModule.installIntentFilter();
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCurrentModule.onActivityResult(requestCode, resultCode, data);
    }

    // Preview area is touched. Handle touch focus.
    @Override
    protected void onSingleTapUp(View view, int x, int y) {
        mCurrentModule.onSingleTapUp(view, x, y);
    }

    @Override
    public void onBackPressed() {
        if (!mCurrentModule.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mCurrentModule.onKeyDown(keyCode,  event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mCurrentModule.onKeyUp(keyCode,  event)
                || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        return mCurrentModule.dispatchTouchEvent(m)
                || super.dispatchTouchEvent(m);
    }

    // Preview texture has been copied. Now camera can be released and the
    // animation can be started.
    @Override
    public void onPreviewTextureCopied() {
        mCurrentModule.onPreviewTextureCopied();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        mCurrentModule.onUserInteraction();
    }

    @Override
    protected boolean updateStorageHintOnResume() {
        return mCurrentModule.updateStorageHintOnResume();
    }

    @Override
    public void updateCameraAppView() {
        super.updateCameraAppView();
        mCurrentModule.updateCameraAppView();
    }

}
