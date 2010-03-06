package com.android.camera;

import android.app.Activity;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.widget.FrameLayout;

import com.android.camera.ui.GLRootView;
import com.android.camera.ui.HeadUpDisplay;
import com.google.android.camera.R;

public class Menu3DTest extends Activity {

    private static String TAG = "Menu3DTest";

    private GLRootView mRootView;
    private OrientationEventListener mOrientationListener;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mRootView = new GLRootView(this);

        // set background as 18% gray :D
        mRootView.setBackgroundColor(0xffb7b7b7);
        final HeadUpDisplay hud = new HeadUpDisplay(this);
        mRootView.setContentPane(hud);

        PreferenceInflater inflater = new PreferenceInflater(this);
        PreferenceGroup preferenceGroup =
                (PreferenceGroup) inflater.inflate(R.xml.camera_preferences);
        hud.initialize(this, preferenceGroup);
        hud.overrideSettings(CameraSettings.KEY_FOCUS_MODE, "macro");

        setContentView(mRootView);

        mOrientationListener = new OrientationEventListener(this) {
            private int mLastOrientation = -1;
            @Override
            public void onOrientationChanged(int orientation) {
                // We keep the last known orientation. So if the user
                // first orient the camera then point the camera to
                if (orientation != ORIENTATION_UNKNOWN) {
                    orientation += 90;
                }
                final int finalOrientation =
                        ImageManager.roundOrientation(orientation);
                if (mLastOrientation != finalOrientation) {
                    mLastOrientation = finalOrientation;
                    mRootView.queueEvent(new Runnable() {
                        public void run() {
                            hud.setOrientation(finalOrientation);
                        }
                    });
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        mRootView.onResume();
        mOrientationListener.enable();
    }

    @Override
    public void onPause() {
        super.onPause();
        mRootView.onPause();
        mOrientationListener.disable();
    }

    @Override
    protected void onDestroy() {
        mRootView = null;
        setContentView(new FrameLayout(this));
        super.onDestroy();
    }
}
