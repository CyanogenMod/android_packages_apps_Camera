/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2010 The CyanogenMod Project
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

import android.hardware.Camera.Parameters;

import java.util.List;

public abstract class BaseCamera extends NoSearchActivity {

    protected ComboPreferences mPreferences;

    protected Parameters mParameters;

    protected void setCommonParameters() {

        // Set color effect parameter.
        String colorEffect = mPreferences.getString(CameraSettings.KEY_COLOR_EFFECT,
                getString(R.string.pref_camera_coloreffect_default));
        if (isSupported(colorEffect, mParameters.getSupportedColorEffects())) {
            mParameters.setColorEffect(colorEffect);
        }

        // Set sharpness parameter.
        if (mParameters.getMaxSharpness() > 0) {
            String sharpness = mPreferences.getString(CameraSettings.KEY_SHARPNESS,
                    String.valueOf(mParameters.getDefaultSharpness()));
            mParameters.setSharpness(Integer.valueOf(sharpness));
        }

        // Set contrast parameter.
        if (mParameters.getMaxContrast() > 0) {
            String contrast = mPreferences.getString(CameraSettings.KEY_CONTRAST,
                    String.valueOf(mParameters.getDefaultContrast()));
            mParameters.setContrast(Integer.valueOf(contrast));
        }

        // Set saturation parameter.
        if (mParameters.getMaxSaturation() > 0) {
            String saturation = mPreferences.getString(CameraSettings.KEY_SATURATION,
                    String.valueOf(mParameters.getDefaultSaturation()));
            mParameters.setSaturation(Integer.valueOf(saturation));
        }
    }

    protected void setWhiteBalance() {
        String whiteBalance = mPreferences.getString(CameraSettings.KEY_WHITE_BALANCE,
                getString(R.string.pref_camera_whitebalance_default));
        if (isSupported(whiteBalance, mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }
    }

    protected static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

}
