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

import com.android.camera.ui.FocusRectangle;
import com.android.camera.ui.FaceView;

import android.graphics.Rect;
import android.hardware.Camera.Area;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

// A class that handles everything about focus in still picture mode.
// This also handles the metering area because it is the same as focus area.
public class FocusManager {
    private static final String TAG = "Focus";

    private static final int RESET_TOUCH_FOCUS = 0;
    private static final int FOCUS_BEEP_VOLUME = 100;
    private static final int RESET_TOUCH_FOCUS_DELAY = 3000;

    private int mState = STATE_IDLE;
    private static final int STATE_IDLE = 0; // Focus is not active.
    private static final int STATE_FOCUSING = 1; // Focus is in progress.
    // Focus is in progress and the camera should take a picture after focus finishes.
    private static final int STATE_FOCUSING_SNAP_ON_FINISH = 2;
    private static final int STATE_SUCCESS = 3; // Focus finishes and succeeds.
    private static final int STATE_FAIL = 4; // Focus finishes and fails.

    private boolean mInitialized;
    private boolean mFocusAreaSupported;
    private boolean mContinuousFocusFail;
    private ToneGenerator mFocusToneGenerator;
    private FocusRectangle mFocusRectangle;
    private View mPreviewFrame;
    private FaceView mFaceView;
    private List<Area> mTapArea;  // focus area in driver format
    private String mFocusMode;
    private String mDefaultFocusMode;
    private String mOverrideFocusMode;
    private Parameters mParameters;
    private ComboPreferences mPreferences;
    private Handler mHandler;
    Listener mListener;

    public interface Listener {
        public void autoFocus();
        public void cancelAutoFocus();
        public boolean capture();
        public void startFaceDetection();
        public void stopFaceDetection();
        public void setFocusParameters();
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESET_TOUCH_FOCUS: {
                    cancelAutoFocus();
                    mListener.startFaceDetection();
                    break;
                }
            }
        }
    }

    public FocusManager(ComboPreferences preferences, String defaultFocusMode, Parameters parameters) {
        mPreferences = preferences;
        mDefaultFocusMode = defaultFocusMode;
        mParameters = parameters;
        mFocusAreaSupported = (mParameters.getMaxNumFocusAreas() > 0
                && isSupported(Parameters.FOCUS_MODE_AUTO,
                        mParameters.getSupportedFocusModes()));
    }

    public void initialize(FocusRectangle focusRectangle, View previewFrame,
            FaceView faceView, Listener listener) {
        mFocusRectangle = focusRectangle;
        mPreviewFrame = previewFrame;
        mFaceView = faceView;
        mListener = listener;
        mHandler = new MainHandler();
        mInitialized = true;
    }

    public void doFocus(boolean pressed) {
        if (!mInitialized) return;

        if (!(getFocusMode().equals(Parameters.FOCUS_MODE_INFINITY)
                || getFocusMode().equals(Parameters.FOCUS_MODE_FIXED)
                || getFocusMode().equals(Parameters.FOCUS_MODE_EDOF))) {
            if (pressed) {  // Focus key down.
                // Do not focus if touch focus has been triggered.
                if (mState != STATE_SUCCESS && mState != STATE_FAIL) {
                    autoFocus();
                }
            } else {  // Focus key up.
                // User releases half-pressed focus key.
                if (mState == STATE_FOCUSING || mState == STATE_SUCCESS
                        || mState == STATE_FAIL) {
                    cancelAutoFocus();
                }
            }
        }
    }

    public void doSnap() {
        if (!mInitialized) return;

        // If the user has half-pressed the shutter and focus is completed, we
        // can take the photo right away. If the focus mode is infinity, we can
        // also take the photo.
        if (getFocusMode().equals(Parameters.FOCUS_MODE_INFINITY)
                || getFocusMode().equals(Parameters.FOCUS_MODE_FIXED)
                || getFocusMode().equals(Parameters.FOCUS_MODE_EDOF)
                || (mState == STATE_SUCCESS
                || mState == STATE_FAIL)) {
            capture();
        } else if (mState == STATE_FOCUSING) {
            // Half pressing the shutter (i.e. the focus button event) will
            // already have requested AF for us, so just request capture on
            // focus here.
            mState = STATE_FOCUSING_SNAP_ON_FINISH;
        } else if (mState == STATE_IDLE) {
            // Focus key down event is dropped for some reasons. Just ignore.
        }
    }

    public void onShutter() {
        resetTouchFocus();
        updateFocusUI();
        if (mFaceView != null) mFaceView.clearFaces();
    }

    public void onAutoFocus(boolean focused) {
        // Do a full autofocus if the scene is not focused in continuous
        // focus mode,
        if (getFocusMode().equals(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) && !focused) {
            mContinuousFocusFail = true;
            mListener.setFocusParameters();
            autoFocus();
            mContinuousFocusFail = false;
        } else if (mState == STATE_FOCUSING_SNAP_ON_FINISH) {
            // Take the picture no matter focus succeeds or fails. No need
            // to play the AF sound if we're about to play the shutter
            // sound.
            if (focused) {
                mState = STATE_SUCCESS;
            } else {
                mState = STATE_FAIL;
            }
            updateFocusUI();
            capture();
        } else if (mState == STATE_FOCUSING) {
            // This happens when (1) user is half-pressing the focus key or
            // (2) touch focus is triggered. Play the focus tone. Do not
            // take the picture now.
            if (focused) {
                mState = STATE_SUCCESS;
                if (mFocusToneGenerator != null) {
                    mFocusToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
                }
            } else {
                mState = STATE_FAIL;
            }
            updateFocusUI();
            // If this is triggered by touch focus, cancel focus after a
            // while.
            if (mTapArea != null) {
                mHandler.sendEmptyMessageDelayed(RESET_TOUCH_FOCUS, RESET_TOUCH_FOCUS_DELAY);
            }
        } else if (mState == STATE_IDLE) {
            // User has released the focus key before focus completes.
            // Do nothing.
        }
    }

    public boolean onTouch(MotionEvent e) {
        if (!mInitialized || mState == STATE_FOCUSING_SNAP_ON_FINISH) return false;

        // Let users be able to cancel previous touch focus.
        if ((mTapArea != null) && (e.getAction() == MotionEvent.ACTION_DOWN)
                && (mState == STATE_FOCUSING || mState == STATE_SUCCESS ||
                    mState == STATE_FAIL)) {
            cancelAutoFocus();
        }

        // Initialize variables.
        int x = Math.round(e.getX());
        int y = Math.round(e.getY());
        int focusWidth = mFocusRectangle.getWidth();
        int focusHeight = mFocusRectangle.getHeight();
        int previewWidth = mPreviewFrame.getWidth();
        int previewHeight = mPreviewFrame.getHeight();
        if (mTapArea == null) {
            mTapArea = new ArrayList<Area>();
            mTapArea.add(new Area(new Rect(), 1));
        }

        // Convert the coordinates to driver format. The actual focus area is two times bigger than
        // UI because a huge rectangle looks strange.
        int areaWidth = focusWidth * 2;
        int areaHeight = focusHeight * 2;
        int areaLeft = Util.clamp(x - areaWidth / 2, 0, previewWidth - areaWidth);
        int areaTop = Util.clamp(y - areaHeight / 2, 0, previewHeight - areaHeight);
        Rect rect = mTapArea.get(0).rect;
        convertToFocusArea(areaLeft, areaTop, areaWidth, areaHeight, previewWidth, previewHeight,
                mTapArea.get(0).rect);

        // Use margin to set the focus rectangle to the touched area.
        RelativeLayout.LayoutParams p =
                (RelativeLayout.LayoutParams) mFocusRectangle.getLayoutParams();
        int left = Util.clamp(x - focusWidth / 2, 0, previewWidth - focusWidth);
        int top = Util.clamp(y - focusHeight / 2, 0, previewHeight - focusHeight);
        p.setMargins(left, top, 0, 0);
        // Disable "center" rule because we no longer want to put it in the center.
        int[] rules = p.getRules();
        rules[RelativeLayout.CENTER_IN_PARENT] = 0;
        mFocusRectangle.requestLayout();

        // Stop face detection because we want to specify focus and metering area.
        mListener.stopFaceDetection();

        // Set the focus area and metering area.
        mListener.setFocusParameters();
        if (mFocusAreaSupported && (e.getAction() == MotionEvent.ACTION_UP)) {
            autoFocus();
        } else {  // Just show the rectangle in all other cases.
            updateFocusUI();
            // Reset the metering area in 3 seconds.
            mHandler.removeMessages(RESET_TOUCH_FOCUS);
            mHandler.sendEmptyMessageDelayed(RESET_TOUCH_FOCUS, RESET_TOUCH_FOCUS_DELAY);
        }

        return true;
    }

    public void onPreviewStarted() {
        mState = STATE_IDLE;
    }

    public void onPreviewStopped() {
        mState = STATE_IDLE;
        resetTouchFocus();
        // If auto focus was in progress, it would have been canceled.
        updateFocusUI();
    }

    public void onCameraReleased() {
        onPreviewStopped();
    }

    private void autoFocus() {
        Log.v(TAG, "Start autofocus.");
        mListener.autoFocus();
        mState = STATE_FOCUSING;
        // Pause the face view because the driver will keep sending face
        // callbacks after the focus completes.
        if (mFaceView != null) mFaceView.pause();
        updateFocusUI();
        mHandler.removeMessages(RESET_TOUCH_FOCUS);
    }

    private void cancelAutoFocus() {
        Log.v(TAG, "Cancel autofocus.");
        mListener.cancelAutoFocus();
        if (mFaceView != null) mFaceView.resume();
        mState = STATE_IDLE;
        resetTouchFocus();
        updateFocusUI();
        mHandler.removeMessages(RESET_TOUCH_FOCUS);
    }

    private void capture() {
        if (mListener.capture()) {
            mState = STATE_IDLE;
            mHandler.removeMessages(RESET_TOUCH_FOCUS);
        }
    }

    public void initializeToneGenerator() {
        // Initialize focus tone generator.
        try {
            mFocusToneGenerator = new ToneGenerator(
                    AudioManager.STREAM_SYSTEM, FOCUS_BEEP_VOLUME);
        } catch (Throwable ex) {
            Log.w(TAG, "Exception caught while creating tone generator: ", ex);
            mFocusToneGenerator = null;
        }
    }

    public void releaseToneGenerator() {
        if (mFocusToneGenerator != null) {
            mFocusToneGenerator.release();
            mFocusToneGenerator = null;
        }
    }

    public String getFocusMode() {
        if (mOverrideFocusMode != null) return mOverrideFocusMode;

        if ((mFocusAreaSupported && mTapArea != null) || mContinuousFocusFail) {
            // Always use autofocus in tap-to-focus or when continuous focus fails.
            mFocusMode = Parameters.FOCUS_MODE_AUTO;
        } else {
            // The default is continuous autofocus.
            mFocusMode = mPreferences.getString(
                    CameraSettings.KEY_FOCUS_MODE, mDefaultFocusMode);
        }
        if (!isSupported(mFocusMode, mParameters.getSupportedFocusModes())) {
            // For some reasons, the driver does not support the current
            // focus mode. Fall back to auto.
            if (isSupported(Parameters.FOCUS_MODE_AUTO,
                    mParameters.getSupportedFocusModes())) {
                mFocusMode = Parameters.FOCUS_MODE_AUTO;
            } else {
                mFocusMode = mParameters.getFocusMode();
            }
        }
        return mFocusMode;
    }

    public List<Area> getTapArea() {
        return mTapArea;
    }

    public void updateFocusUI() {
        if (!mInitialized) return;

        // Set the length of focus rectangle according to preview frame size.
        int len = Math.min(mPreviewFrame.getWidth(), mPreviewFrame.getHeight()) / 4;
        ViewGroup.LayoutParams layout = mFocusRectangle.getLayoutParams();
        layout.width = len;
        layout.height = len;

        if (mState == STATE_IDLE) {
            if (mTapArea == null) {
                mFocusRectangle.clear();
            } else {
                // Users touch on the preview and the rectangle indicates the
                // metering area. Either focus area is not supported or
                // autoFocus call is not required.
                mFocusRectangle.showStart();
            }
            return;
        }

        // Do not show focus rectangle if there is any face rectangle.
        if (mFaceView != null && mFaceView.faceExists()) return;

        if (mState == STATE_FOCUSING || mState == STATE_FOCUSING_SNAP_ON_FINISH) {
            mFocusRectangle.showStart();
        } else if (mState == STATE_SUCCESS) {
            mFocusRectangle.showSuccess();
        } else if (mState == STATE_FAIL) {
            mFocusRectangle.showFail();
        }
    }

    public void resetTouchFocus() {
        if (!mInitialized) return;

        // Put focus rectangle to the center.
        RelativeLayout.LayoutParams p =
                (RelativeLayout.LayoutParams) mFocusRectangle.getLayoutParams();
        int[] rules = p.getRules();
        rules[RelativeLayout.CENTER_IN_PARENT] = RelativeLayout.TRUE;
        p.setMargins(0, 0, 0, 0);

        mTapArea = null;
    }

    // Convert the touch point to the focus area in driver format.
    public static void convertToFocusArea(int left, int top, int focusWidth, int focusHeight,
            int previewWidth, int previewHeight, Rect rect) {
        rect.left = Math.round((float) left / previewWidth * 2000 - 1000);
        rect.top = Math.round((float) top / previewHeight * 2000 - 1000);
        rect.right = Math.round((float) (left + focusWidth) / previewWidth * 2000 - 1000);
        rect.bottom = Math.round((float) (top + focusHeight) / previewHeight * 2000 - 1000);
    }

    public boolean isFocusCompleted() {
        return mState == STATE_SUCCESS || mState == STATE_FAIL;
    }

    public void removeMessages() {
        mHandler.removeMessages(RESET_TOUCH_FOCUS);
    }

    public void overrideFocusMode(String focusMode) {
        mOverrideFocusMode = focusMode;
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }
}
