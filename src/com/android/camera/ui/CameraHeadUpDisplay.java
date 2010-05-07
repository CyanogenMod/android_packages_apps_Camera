package com.android.camera.ui;

import android.content.Context;

import com.android.camera.CameraSettings;
import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

public class CameraHeadUpDisplay extends HeadUpDisplay {

    private static final String TAG = "CamcoderHeadUpDisplay";

    private OtherSettingsIndicator mOtherSettings;
    private GpsIndicator mGpsIndicator;
    private ZoomIndicator mZoomIndicator;
    private Context mContext;

    public CameraHeadUpDisplay(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    protected void initializeIndicatorBar(
            Context context, PreferenceGroup group) {
        super.initializeIndicatorBar(context, group);

        ListPreference prefs[] = getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_COLOR_EFFECT);

        mOtherSettings = new OtherSettingsIndicator(context, prefs);
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        mGpsIndicator = new GpsIndicator(
                context, group, (IconListPreference)
                group.findPreference(CameraSettings.KEY_RECORD_LOCATION));
        mIndicatorBar.addComponent(mGpsIndicator);

        addIndicator(context, group, CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, group, CameraSettings.KEY_FLASH_MODE);
    }

    public void setZoomListener(ZoomController.ZoomListener listener) {
        mZoomIndicator.setZoomListener(listener);
    }

    public void setZoomIndex(int index) {
        mZoomIndicator.setZoomIndex(index);
    }

    public void setGpsHasSignal(final boolean hasSignal) {
        GLRootView root = getGLRootView();
        if (root != null) {
            root.queueEvent(new Runnable() {
                public void run() {
                    mGpsIndicator.setHasSignal(hasSignal);
                }
            });
        } else {
            mGpsIndicator.setHasSignal(hasSignal);
        }
    }

    /**
     * Sets the zoom rations the camera driver provides. This methods must be
     * called before <code>setZoomListener()</code> and
     * <code>setZoomIndex()</code>
     */
    public void setZoomRatios(float[] zoomRatios) {
        if (mZoomIndicator == null) {
            mZoomIndicator = new ZoomIndicator(mContext);
            mIndicatorBar.addComponent(mZoomIndicator);
        }
        mZoomIndicator.setZoomRatios(zoomRatios);
    }
}
