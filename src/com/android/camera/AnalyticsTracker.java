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

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.graphics.Matrix;
import android.media.CamcorderProfile;
import android.util.Log;

public class AnalyticsTracker {
    private static final String TAG = "AnalyticsTracker";
    private static AnalyticsTracker sAnalyticsTracker;
    public static final String VALUE_ON = "on";
    public static final String VALUE_OFF = "off";

    private GoogleAnalyticsTracker mTracker;
    private boolean mStarted;
    private Context mContext;
    private String mCategory;

    // Track time period of taking picture.
    private long mPreviousTime;
    private int mPictureCaptureNumber;

    // Track video effect.
    private String mVideoEffectType = "None";

    // Track tap to focus.
    private int mTapToFocusX, mTapToFocusY;
    private Matrix mMatrix;
    private boolean mIsTapToFocus;

    public static AnalyticsTracker instance() {
        if (sAnalyticsTracker == null) {
            sAnalyticsTracker = new AnalyticsTracker();
        }
        return sAnalyticsTracker;
    }

    private AnalyticsTracker() {
        mTracker = GoogleAnalyticsTracker.getInstance();
    }

    public void setContext(Context context, String category) {
        mContext = context;
        mCategory = category;
    }

    public void start() {
        if (mStarted == true) return;
        if (mContext == null) return;

        mTracker.start("UA-27112885-5", 10, mContext);
        mPreviousTime = System.currentTimeMillis();
        mPictureCaptureNumber = 0;
        mStarted = true;
    }

    public void stop() {
        if (mStarted == false) return;

        mTracker.stopSession();
        mStarted = false;
        mIsTapToFocus = false;
    }

    public boolean isStarted() {
        return mStarted;
    }

    public void startAnalyticsTracker(ComboPreferences pref,
            RotateDialogController rotateDialog, Context context, String category) {
        setContext(context, category);
        // Show the confirmation dialog about analytics first time.
        // If user allow us to collect data, start analytics tracker.
        if (pref.getBoolean(CameraSettings.KEY_ANALYTICS_CONFIRMATION_SHOWN, true)) {
            AnalyticsDialogResponse okRunnable =
                    new AnalyticsDialogResponse(true, pref, this);
            AnalyticsDialogResponse noRunnable =
                    new AnalyticsDialogResponse(false, pref, this);
            rotateDialog.showAlertDialog(
                    context.getString(R.string.confirm_analytics_title),
                    context.getString(R.string.confirm_analytics_message),
                    context.getString(R.string.confirm_agree), okRunnable,
                    context.getString(R.string.confirm_disagree), noRunnable);
        } else {
            checkPermission(pref);
        }
    }

    public void checkPermission(ComboPreferences pref) {
        if (hasPermission(pref)) {
            start();
        } else {
            stop();
        }
    }

    public boolean hasPermission(ComboPreferences pref) {
        return VALUE_ON.equals(
            pref.getString(CameraSettings.KEY_ANALYTICS_PERMISSION, VALUE_OFF));
    }

    public void trackSettings(Parameters parameters, int cameraId, boolean recordLocation) {
        if (mStarted == false) return;

        trackEvent("Focus", parameters.getFocusMode());
        trackEvent("Flash", parameters.getFlashMode());
        trackEvent("WhiteBalance", parameters.getWhiteBalance());
        trackEvent("Scene", parameters.getSceneMode());
        trackEvent("Exposure", getExposure(parameters));
        trackEvent("PictureSize", getPictureSize(parameters));
        trackEvent("Facing", getFacing(cameraId));
        trackEvent("GPS", recordLocation ? "On" : "Off");
        if (parameters.isZoomSupported()) {
            trackEvent("Zoom", parameters.getZoom());
        }

        if (mIsTapToFocus) {
            mIsTapToFocus = false;
            float[] point = {(float)mTapToFocusX, (float)mTapToFocusY};
            mMatrix.mapPoints(point);
            trackEvent("TapToFocus", "(" + point[0] + ", " + point[1] + ")");
        }

        mPictureCaptureNumber++;
        long currentTime = System.currentTimeMillis();
        int timeInterval = (int)(currentTime - mPreviousTime);
        mPreviousTime = currentTime;
        trackEvent("TimeInterval", String.valueOf(mPictureCaptureNumber), timeInterval);
    }

    public void trackVideoSettings(CamcorderProfile profile, int timeLapse, String effect) {
        trackEvent("VideoSize", "" + profile.videoFrameWidth + "x" + profile.videoFrameHeight);
        trackEvent("TimeLapse", "" + timeLapse + "ms");
        trackEvent("EffectType", effect);
    }

    public void trackEvent(String action, int value) {
        trackEvent(action, "", value);
    }

    public void trackEvent(String action, String label) {
        trackEvent(action, label, 0);
    }

    public void trackEvent(String action, String label, int value) {
        if (mStarted == false) return;

        mTracker.trackEvent(mCategory, action, label, value);
        Log.d(TAG, mCategory + ":" + action + ":" + label + ":" + value);
    }

    public void trackTapToFocus(int x, int y, Matrix matrix) {
        mIsTapToFocus = true;
        mTapToFocusX = x;
        mTapToFocusY = y;
        mMatrix = matrix;
    }

    public void cancelTapToFocus() {
        mIsTapToFocus = false;
    }

    private String getExposure(Parameters p) {
        float value = p.getExposureCompensationStep() * p.getExposureCompensation();
        return Util.exposureFormatString(value);
    }

    private String getPictureSize(Parameters p) {
        Size size = p.getPictureSize();
        return String.format("%dx%d", size.width, size.height);
    }

    private String getFacing(int cameraId) {
        CameraInfo info = CameraHolder.instance().getCameraInfo()[cameraId];
        return (info.facing == CameraInfo.CAMERA_FACING_BACK) ? "Back" : "Front";
    }

    private static class AnalyticsDialogResponse implements Runnable {
        private Boolean mIsOk;
        private ComboPreferences mPreferences;
        private AnalyticsTracker mTracker;

        public AnalyticsDialogResponse(Boolean isOk, ComboPreferences pref,
                AnalyticsTracker tracker) {
            mIsOk = isOk;
            mPreferences = pref;
            mTracker = tracker;
        }

        public void run() {
            Editor editor = mPreferences.edit();
            String value = mIsOk ? VALUE_ON : VALUE_OFF;
            editor.putString(CameraSettings.KEY_ANALYTICS_PERMISSION, value);
            editor.putBoolean(CameraSettings.KEY_ANALYTICS_CONFIRMATION_SHOWN, false);
            editor.apply();

            if (mIsOk) mTracker.start();
        }
    }
}
