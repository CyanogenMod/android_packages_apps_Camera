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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera.ui.CameraSwitcher;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.LightCycleHelper;

public class CameraActivity extends ActivityBase
        implements CameraSwitcher.CameraSwitchListener {
    public static final int PHOTO_MODULE_INDEX = 0;
    public static final int VIDEO_MODULE_INDEX = 1;
    public static final int PANORAMA_MODULE_INDEX = 2;
    public static final int LIGHTCYCLE_MODULE_INDEX = 3;

    CameraModule mCurrentModule;
    private FrameLayout mFrame;
    private ShutterButton mShutter;
    private CameraSwitcher mSwitcher;
    private View mShutterSwitcher;
    private View mControlsBackground;
    private Drawable[] mDrawables;
    private int mCurrentModuleIndex;
    private MotionEvent mDown;

    private MyOrientationEventListener mOrientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mLastRawOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private static final String TAG = "CAM_activity";

    private static final int[] DRAW_IDS = {
            R.drawable.ic_switch_camera,
            R.drawable.ic_switch_video,
            R.drawable.ic_switch_pan,
            R.drawable.ic_switch_photosphere
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.camera_main);
        mFrame = (FrameLayout) findViewById(R.id.main_content);
        mDrawables = new Drawable[DRAW_IDS.length];
        for (int i = 0; i < DRAW_IDS.length; i++) {
            mDrawables[i] = getResources().getDrawable(DRAW_IDS[i]);
        }
        init();
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            mCurrentModule = new VideoModule();
            mCurrentModuleIndex = VIDEO_MODULE_INDEX;
        } else {
            mCurrentModule = new PhotoModule();
            mCurrentModuleIndex = PHOTO_MODULE_INDEX;
        }
        mCurrentModule.init(this, mFrame, true);
        mSwitcher.setCurrentIndex(mCurrentModuleIndex);
        mOrientationListener = new MyOrientationEventListener(this);
    }

    public void init() {
        mControlsBackground = findViewById(R.id.controls);
        mShutterSwitcher = findViewById(R.id.camera_shutter_switcher);
        mShutter = (ShutterButton) findViewById(R.id.shutter_button);
        mSwitcher = (CameraSwitcher) findViewById(R.id.camera_switcher);
        int totaldrawid = (LightCycleHelper.hasLightCycleCapture(this)
                                ? DRAW_IDS.length : DRAW_IDS.length - 1);
        if (!ApiHelper.HAS_OLD_PANORAMA) totaldrawid--;

        int[] drawids = new int[totaldrawid];
        int[] moduleids = new int[totaldrawid];
        int ix = 0;
        for (int i = 0; i < mDrawables.length; i++) {
            if (i == PANORAMA_MODULE_INDEX && !ApiHelper.HAS_OLD_PANORAMA) {
                continue; // not enabled, so don't add to UI
            }
            if (i == LIGHTCYCLE_MODULE_INDEX && !LightCycleHelper.hasLightCycleCapture(this)) {
                continue; // not enabled, so don't add to UI
            }
            moduleids[ix] = i;
            drawids[ix++] = DRAW_IDS[i];
        }
        mSwitcher.setIds(moduleids, drawids);
        mSwitcher.setSwitchListener(this);
        mSwitcher.setCurrentIndex(mCurrentModuleIndex);
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
            mLastRawOrientation = orientation;
            mCurrentModule.onOrientationChanged(orientation);
        }
    }

    @Override
    public void onCameraSelected(int i) {
        if (mPaused) return;
        if (i != mCurrentModuleIndex) {
            mPaused = true;
            boolean canReuse = canReuseScreenNail();
            CameraHolder.instance().keep();
            closeModule(mCurrentModule);
            mCurrentModuleIndex = i;
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
            openModule(mCurrentModule, canReuse);
            mCurrentModule.onOrientationChanged(mLastRawOrientation);
        }
    }

    @Override
    public void onShowSwitcherPopup() {
        mCurrentModule.onShowSwitcherPopup();
    }

    private void openModule(CameraModule module, boolean canReuse) {
        module.init(this, mFrame, canReuse && canReuseScreenNail());
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

    public void hideUI() {
        mControlsBackground.setVisibility(View.INVISIBLE);
        hideSwitcher();
        mShutter.setVisibility(View.GONE);
    }

    public void showUI() {
        mControlsBackground.setVisibility(View.VISIBLE);
        showSwitcher();
        mShutter.setVisibility(View.VISIBLE);
        // Force a layout change to show shutter button
        mShutter.requestLayout();
    }

    public void hideSwitcher() {
        mSwitcher.closePopup();
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void showSwitcher() {
        if (mCurrentModule.needsSwitcher()) {
            mSwitcher.setVisibility(View.VISIBLE);
        }
    }

    public boolean isInCameraApp() {
        return mShowCameraAppView;
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
        getStateManager().clearTasks();
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
        // Only PhotoPage understands ProxyLauncher.RESULT_USER_CANCELED
        if (resultCode == ProxyLauncher.RESULT_USER_CANCELED
                && !(getStateManager().getTopState() instanceof PhotoPage)) {
            resultCode = RESULT_CANCELED;
        }
        super.onActivityResult(requestCode, resultCode, data);
        // Unmap cancel vs. reset
        if (resultCode == ProxyLauncher.RESULT_USER_CANCELED) {
            resultCode = RESULT_CANCELED;
        }
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
        if (mDown != null) {
            MotionEvent cancel = MotionEvent.obtain(mDown);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (m.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mDown = m;
        }
        if ((mSwitcher != null) && mSwitcher.showsPopup() && !mSwitcher.isInsidePopup(m)) {
            return mSwitcher.onTouch(null, m);
        } else {
            return mShutterSwitcher.dispatchTouchEvent(m)
                    || mCurrentModule.dispatchTouchEvent(m);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        Intent proxyIntent = new Intent(this, ProxyLauncher.class);
        proxyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        proxyIntent.putExtra(Intent.EXTRA_INTENT, intent);
        super.startActivityForResult(proxyIntent, requestCode);
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
    public void onCaptureTextureCopied() {
        mCurrentModule.onCaptureTextureCopied();
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

    private boolean canReuseScreenNail() {
        return mCurrentModuleIndex == PHOTO_MODULE_INDEX
                || mCurrentModuleIndex == VIDEO_MODULE_INDEX
                || mCurrentModuleIndex == LIGHTCYCLE_MODULE_INDEX;
    }

    @Override
    public boolean isPanoramaActivity() {
        return (mCurrentModuleIndex == PANORAMA_MODULE_INDEX);
    }

    // Accessor methods for getting latency times used in performance testing
    public long getAutoFocusTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mAutoFocusTime : -1;
    }

    public long getShutterLag() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterLag : -1;
    }

    public long getShutterToPictureDisplayedTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterToPictureDisplayedTime : -1;
    }

    public long getPictureDisplayedToJpegCallbackTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mPictureDisplayedToJpegCallbackTime : -1;
    }

    public long getJpegCallbackFinishTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mJpegCallbackFinishTime : -1;
    }

    public long getCaptureStartTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mCaptureStartTime : -1;
    }

    public boolean isRecording() {
        return (mCurrentModule instanceof VideoModule) ?
                ((VideoModule) mCurrentModule).isRecording() : false;
    }

    public CameraScreenNail getCameraScreenNail() {
        return (CameraScreenNail) mCameraScreenNail;
    }
}
