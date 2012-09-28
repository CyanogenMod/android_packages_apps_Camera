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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.camera.ui.CameraSwitcher;
import com.android.camera.ui.RotateImageView;
import com.android.gallery3d.util.LightCycleHelper;

public class CameraActivity extends ActivityBase
        implements CameraSwitcher.CameraSwitchListener {
    public static final int VIDEO_MODULE_INDEX = 0;
    public static final int PHOTO_MODULE_INDEX = 1;
    public static final int PANORAMA_MODULE_INDEX = 2;
    public static final int LIGHTCYCLE_MODULE_INDEX = 3;

    CameraModule mCurrentModule;
    private FrameLayout mFrame;
    private ShutterButton mShutter;
    private RotateImageView mShutterIcon;
    private CameraSwitcher mSwitcher;
    private Drawable[] mDrawables;
    private int mSelectedModule;
    private Dispatcher mDispatcher;

    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    // The orientation compensation for icons. Eg: if the value
    // is 90, the UI components should be rotated 90 degrees counter-clockwise.
    private int mOrientationCompensation = 0;

    private static final String TAG = "CAM_activity";

    private static final int[] DRAW_IDS = {
            R.drawable.ic_switch_video,
            R.drawable.ic_switch_camera,
            R.drawable.ic_switch_pan,
            R.drawable.ic_switch_photo_pan_holo_light
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mDispatcher = new Dispatcher();
        setContentView(R.layout.camera_main);
        mFrame =(FrameLayout) findViewById(R.id.main_content);
        mDrawables = new Drawable[DRAW_IDS.length];
        for (int i = 0; i < DRAW_IDS.length; i++) {
            mDrawables[i] = getResources().getDrawable(DRAW_IDS[i]);
        }
        init();
        mSwitcher.setSwitchListener(this);
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            mCurrentModule = new VideoModule();
            mSelectedModule = VIDEO_MODULE_INDEX;
        } else {
            mCurrentModule = new PhotoModule();
            mSelectedModule = PHOTO_MODULE_INDEX;
        }
        mCurrentModule.init(this, mFrame, true);
        mSwitcher.animateToModule(mSelectedModule);
        mOrientationListener = new MyOrientationEventListener(this);
    }

    public void init() {
        mShutter = (ShutterButton) findViewById(R.id.shutter_button);
        mShutterIcon = (RotateImageView) findViewById(R.id.shutter_overlay);
        mShutterIcon.enableFilter(false);
        mSwitcher = (CameraSwitcher) findViewById(R.id.camera_switcher);
        for (int i = 0; i < mDrawables.length; i++) {
            if (i == LIGHTCYCLE_MODULE_INDEX && !LightCycleHelper.hasLightCycleCapture(this)) {
                continue; // not enabled, so don't add to UI
            }
            RotateImageView iv = new RotateImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setImageDrawable(mDrawables[i]);
            iv.enableFilter(false);
            mSwitcher.add(iv, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            final int index = i;
            iv.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    mSwitcher.animateToModule(index);
                }
            });
        }
    }

    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation =
                    (mOrientation + Util.getDisplayRotation(CameraActivity.this)) % 360;
            // Rotate camera mode icons in the switcher
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                if(mSwitcher != null) {
                    mSwitcher.rotateIcons(mOrientationCompensation, true);
                }
            }
            mCurrentModule.onOrientationChanged(orientation);
        }
    }
    @Override
    public void onScroll() {
    }

    @Override
    public void onCameraSelected(int i) {
        if (i != mSelectedModule) {
            mPaused = true;
            boolean wasPanorama = isPanoramaActivity();
            closeModule(mCurrentModule);
            mSelectedModule = i;
            switch (i) {
                case VIDEO_MODULE_INDEX:
                    mCurrentModule = new VideoModule();
                    break;
                case PHOTO_MODULE_INDEX:
                    mCurrentModule = new PhotoModule();
                    break;
                case PANORAMA_MODULE_INDEX:
                    mCurrentModule = new PanoramaModule();
                    break;
                case LIGHTCYCLE_MODULE_INDEX:
                    mCurrentModule = LightCycleHelper.createPanoramaModule();
                    break;
            }
            openModule(mCurrentModule, wasPanorama);
        }
    }

    private void openModule(CameraModule module, boolean wasPanorama) {
        module.init(this, mFrame, !(wasPanorama || isPanoramaActivity()));
        mPaused = false;
        module.onResumeBeforeSuper();
        module.onResumeAfterSuper();
    }

    private void closeModule(CameraModule module) {
        module.onPauseBeforeSuper();
        module.onPauseAfterSuper();
        mFrame.removeAllViews();
    }

    private void showShutterIcon(boolean show) {
        showShutterIcon(show, DRAW_IDS[mSelectedModule]);
    }

    private void showShutterIcon(boolean show, int resid) {
        if (show) {
            mShutterIcon.setImageResource(resid);
            mShutterIcon.setOrientation(mOrientationCompensation, false);
        }
        mShutterIcon.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public ShutterButton getShutterButton() {
        return mShutter;
    }

    public void hideUI() {
        hideSwitcher();
        mShutter.setVisibility(View.GONE);
        mShutterIcon.setVisibility(View.GONE);
    }

    public void showUI() {
        showSwitcher();
        mShutter.setVisibility(View.VISIBLE);
    }

    // hide the switcher and show the given shutter icon
    public void hideSwitcher(int resid) {
        mSwitcher.setVisibility(View.GONE);
        showShutterIcon(true, resid);
    }

    public void hideSwitcher() {
        mSwitcher.setVisibility(View.GONE);
        showShutterIcon(true);
    }

    public void showSwitcher() {
        if (mCurrentModule.needsSwitcher()) {
            mSwitcher.setVisibility(View.VISIBLE);
            showShutterIcon(false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);

        ViewGroup appRoot = (ViewGroup) findViewById(R.id.content);
        // remove old switcher, shutter and shutter icon
        View cameraControlsView = findViewById(R.id.camera_shutter_switcher);
        appRoot.removeView(cameraControlsView);

        // create new layout with the current orientation
        LayoutInflater inflater = getLayoutInflater();
        inflater.inflate(R.layout.camera_shutter_switcher, appRoot);
        init();

        mSwitcher.animateToModule(mSelectedModule);
        mSwitcher.setSwitchListener(this);
        if (mShowCameraAppView) {
            showUI();
        } else {
            hideUI();
        }
        mCurrentModule.onConfigurationChanged(config);
    }

    @Override
    public void onPause() {
        mPaused = true;
        mOrientationListener.disable();
        mCurrentModule.onPauseBeforeSuper();
        super.onPause();
        mCurrentModule.onPauseAfterSuper();
    }

    @Override
    public void onResume() {
        mPaused = false;
        mOrientationListener.enable();
        mCurrentModule.onResumeBeforeSuper();
        super.onResume();
        mCurrentModule.onResumeAfterSuper();
    }

    @Override
    protected void onFullScreenChanged(boolean full) {
        if (full) {
            showUI();
        } else {
            hideUI();
        }
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

    public void cancelActivityTouchHandling() {
        MotionEvent e = mDispatcher.getCancelEvent();
        if (e != null) {
            super.dispatchTouchEvent(e);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        boolean handled = false;
        if ((m.getActionMasked() == MotionEvent.ACTION_DOWN) || mDispatcher.isActive()) {
            mShutter.enableTouch(true);
            mSwitcher.enableTouch(true);
            handled = mDispatcher.dispatchTouchEvent(m);
            mShutter.enableTouch(false);
            mSwitcher.enableTouch(false);
        }
        return handled || mCurrentModule.dispatchTouchEvent(m);
    }

    public boolean superDispatchTouchEvent(MotionEvent m) {
        return super.dispatchTouchEvent(m);
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

    @Override
    public boolean isPanoramaActivity() {
        return (mCurrentModule instanceof PanoramaModule);
    }

    private class Dispatcher {

        private boolean mActive;
        private MotionEvent mDown;
        private int mSlop;
        private boolean mDownInShutter;

        public Dispatcher() {
            mSlop = ViewConfiguration.get(CameraActivity.this).getScaledTouchSlop();
            mActive = true;
        }

        public MotionEvent getCancelEvent() {
            if (mDown != null) {
                MotionEvent cancel = MotionEvent.obtain(mDown);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                return cancel;
            }
            return null;
        }

        public boolean isActive() {
            return mActive;
        }

        public boolean dispatchTouchEvent(MotionEvent m) {
            switch (m.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mActive = false;
                mDownInShutter = isInside(m, mShutter);
                mDown = m;
                if (mDownInShutter) {
                    sendTo(m, mShutter);
                    mActive = true;
                }
                if (isInside(m, mSwitcher)) {
                    sendTo(m, mSwitcher);
                    mActive = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mDownInShutter) {
                    if (Math.abs(m.getX() - mDown.getX()) > mSlop) {
                        // sliding switcher
                        mDownInShutter = false;
                        MotionEvent cancel = MotionEvent.obtain(m);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        sendTo(cancel, mShutter);
                        sendTo(m, mSwitcher);
                    } else {
                        sendTo(m, mShutter);
                        sendTo(m, mSwitcher);
                    }
                } else {
                    sendTo(m, mSwitcher);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mDownInShutter) {
                    sendTo(m, mShutter);
                    MotionEvent cancel = MotionEvent.obtain(m);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    sendTo(cancel, mSwitcher);
                } else {
                    sendTo(m, mSwitcher);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mDownInShutter) {
                    sendTo(m, mShutter);
                }
                sendTo(m, mSwitcher);
                break;
            }
            return mActive;
        }
    }

    private boolean sendTo(MotionEvent m, View v) {
        return v.dispatchTouchEvent(transformEvent(m, v));
    }

    private boolean isInside(MotionEvent evt, View v) {
        return (v.getVisibility() == View.VISIBLE
                && evt.getX() >= v.getLeft() && evt.getX() < v.getRight()
                && evt.getY() >= v.getTop() && evt.getY() < v.getBottom());
    }

    private MotionEvent transformEvent(MotionEvent m, View v) {
        MotionEvent r = MotionEvent.obtain(m);
        r.offsetLocation(- v.getLeft(), - v.getTop());
        return r;
    }

}
