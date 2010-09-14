/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.camera.CameraSettings;
import com.android.camera.R;
import com.android.camera.ui.SettingsController.ValueListener;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.Parameters;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SettingsIndicator extends AbstractIndicator {

    @SuppressWarnings("unused")
    private static final String TAG = "SettingsIndicator";

    private SettingsController mBrightnessController;

    private SettingsController mContrastController;

    private SettingsController mSaturationController;

    private SettingsController mSharpnessController;

    private List<String> mBrightnessValues = new ArrayList<String>();

    private List<String> mContrastValues = new ArrayList<String>();

    private List<String> mSaturationValues = new ArrayList<String>();

    private List<String> mSharpnessValues = new ArrayList<String>();

    private LinearLayout mPopupContent;

    private Context mContext;

    private ResourceTexture mIcon;

    private SharedPreferences mPreferences;

    private Parameters mParameters;

    public SettingsIndicator(Context context, Parameters params, SharedPreferences prefs) {
        super(context);
        mContext = context;
        mParameters = params;
        mPreferences = prefs;

        if (params.getMaxExposureCompensation() > 0) {
            for (float i = params.getMinExposureCompensation(); i <= params
                    .getMaxExposureCompensation(); i += params.getExposureCompensationStep()) {
                mBrightnessValues.add(String.valueOf(i));
            }
        }

        setupSlider(mContrastValues, params.getMaxContrast(), params.getDefaultContrast(),
                CameraSettings.KEY_CONTRAST, prefs);

        setupSlider(mSharpnessValues, params.getMaxSharpness(), params.getDefaultSharpness(),
                CameraSettings.KEY_SHARPNESS, prefs);

        setupSlider(mSaturationValues, params.getMaxSaturation(), params.getDefaultSaturation(),
                CameraSettings.KEY_SATURATION, prefs);
    }

    @Override
    protected Texture getIcon() {
        if (mIcon == null) {
            mIcon = new ResourceTexture(mContext, R.drawable.ic_viewfinder_sliders);
        }
        return mIcon;
    }

    private static void setupSlider(final List<String> values, final int maxValue,
            final int defaultValue, final String prefKey, SharedPreferences prefs) {
        if (maxValue > 0) {
            for (int i = 0; i <= maxValue; i++) {
                values.add(String.valueOf(i));
            }
        }
    }

    private void addSlider(SettingsController controller, final List<String> values,
            final String defaultValue, final int headerRes, final String prefKey) {

        if (controller == null && values.size() > 0) {
            Context context = getGLRootView().getContext();
            controller = new SettingsController(context);
            controller.setAvailableValues(values.toArray(new String[values.size()]));
            controller.setPaddings(3, 3, 3, 3);

            LinearLayout layout = new LinearLayout();
            GLOptionHeader header = new GLOptionHeader(context, context.getString(headerRes));
            header.setBackground(new NinePatchTexture(context, R.drawable.optionheader_background));
            header.setPaddings(3, 3, 3, 3);
            layout.addComponent(header);
            layout.addComponent(controller);

            mPopupContent.addComponent(layout);

            setSliderValue(controller, values, mPreferences.getString(prefKey, defaultValue));

            controller.setValueListener(new ValueListener() {
                public void onValueChanged(int index, String value, boolean isMoving) {
                    setPreference(prefKey, value);
                }
            });
        }
    }

    @Override
    public GLView getPopupContent() {

        mPopupContent = new LinearLayout();
        mPopupContent.setOrientation(LinearLayout.HORIZONTAL);

        addSlider(mBrightnessController, mBrightnessValues, CameraSettings.EXPOSURE_DEFAULT_VALUE,
                R.string.pref_camera_brightness_title, CameraSettings.KEY_EXPOSURE);

        addSlider(mContrastController, mContrastValues,
                String.valueOf(mParameters.getDefaultContrast()),
                R.string.pref_camera_contrast_title, CameraSettings.KEY_CONTRAST);

        addSlider(mSaturationController, mSaturationValues,
                String.valueOf(mParameters.getDefaultSaturation()),
                R.string.pref_camera_saturation_title, CameraSettings.KEY_SATURATION);

        addSlider(mSharpnessController, mSharpnessValues,
                String.valueOf(mParameters.getDefaultSharpness()),
                R.string.pref_camera_sharpness_title, CameraSettings.KEY_SHARPNESS);

        return mPopupContent;
    }

    private void setPreference(String key, String value) {
        Log.d("CameraSettings", "*** key: " + key + " value: " + value);
        Editor editor = mPreferences.edit();
        editor.putString(key, value);
        editor.commit();
    }

    @Override
    public void overrideSettings(String key, String settings) {
        // do nothing
    }

    @Override
    public void reloadPreferences() {
        setSliderValue(mBrightnessController, mBrightnessValues,
                CameraSettings.EXPOSURE_DEFAULT_VALUE);
        setSliderValue(mContrastController, mContrastValues,
                String.valueOf(mParameters.getDefaultContrast()));
        setSliderValue(mSaturationController, mSaturationValues,
                String.valueOf(mParameters.getDefaultSaturation()));
        setSliderValue(mSharpnessController, mSharpnessValues,
                String.valueOf(mParameters.getDefaultSharpness()));
    }

    private void setSliderValue(final SettingsController controller, final List<String> values,
            final String value) {
        if (controller != null) {
            controller.setValueIndex(values.indexOf(value));
        }
        invalidate();
    }

    public boolean isAvailable() {
        return !(mBrightnessValues.size() == 0 && mContrastValues.size() == 0
                && mSaturationValues.size() == 0 && mSharpnessValues.size() == 0);
    }

    public interface SettingsListener {
        public void onValueChanged(int type, int value, boolean isMoving);
    }
}
