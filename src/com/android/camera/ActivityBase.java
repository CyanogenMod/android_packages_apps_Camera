/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.camera.ui.PopupManager;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Superclass of Camera and VideoCamera activities.
 */
abstract public class ActivityBase extends Activity {
    private static final String TAG = "ActivityBase";
    private static boolean LOGV = false;
    private int mResultCodeForTesting;
    private boolean mOnResumePending;
    private Intent mResultDataForTesting;
    protected Camera mCameraDevice;

    @Override
    public void onCreate(Bundle icicle) {
        if (Util.isTabletUI()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(icicle);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (LOGV) Log.v(TAG, "onWindowFocusChanged.hasFocus=" + hasFocus
                + ".mOnResumePending=" + mOnResumePending);
        if (hasFocus && mOnResumePending) {
            doOnResume();
            mOnResumePending = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Don't grab the camera if in use by lockscreen. For example, face
        // unlock may be using the camera. Camera may be already opened in
        // onCreate. doOnResume should continue if mCameraDevice != null.
        // Suppose camera app is in the foreground. If users turn off and turn
        // on the screen very fast, camera app can still have the focus when the
        // lock screen shows up. The keyguard takes input focus, so the caemra
        // app will lose focus when it is displayed.
        if (LOGV) Log.v(TAG, "onResume. hasWindowFocus()=" + hasWindowFocus());
        if (mCameraDevice == null && isKeyguardLocked()) {
            if (LOGV) Log.v(TAG, "onResume. mOnResumePending=true");
            mOnResumePending = true;
        } else {
            if (LOGV) Log.v(TAG, "onResume. mOnResumePending=false");
            doOnResume();
            mOnResumePending = false;
        }
    }

    @Override
    protected void onPause() {
        if (LOGV) Log.v(TAG, "onPause");
        super.onPause();
        mOnResumePending = false;
    }

    // Put the code of onResume in this method.
    abstract protected void doOnResume();

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Prevent software keyboard or voice search from showing up.
        if (keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.isLongPress()) return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    protected void setResultEx(int resultCode) {
        mResultCodeForTesting = resultCode;
        setResult(resultCode);
    }

    protected void setResultEx(int resultCode, Intent data) {
        mResultCodeForTesting = resultCode;
        mResultDataForTesting = data;
        setResult(resultCode, data);
    }

    public int getResultCode() {
        return mResultCodeForTesting;
    }

    public Intent getResultData() {
        return mResultDataForTesting;
    }

    @Override
    protected void onDestroy() {
        PopupManager.removeInstance(this);
        super.onDestroy();
    }

    private boolean isKeyguardLocked() {
        KeyguardManager kgm = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (LOGV) {
            if (kgm != null) {
                Log.v(TAG, "kgm.isKeyguardLocked()="+kgm.isKeyguardLocked()
                        + ". kgm.isKeyguardSecure()="+kgm.isKeyguardSecure());
            }
        }
        // isKeyguardSecure excludes the slide lock case.
        return (kgm != null) && kgm.isKeyguardLocked() && kgm.isKeyguardSecure();
    }
}
