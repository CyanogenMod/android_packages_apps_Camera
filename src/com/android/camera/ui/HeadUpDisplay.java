package com.android.camera.ui;

import static com.android.camera.ui.GLRootView.dpToPixel;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import com.android.camera.R;

import com.android.camera.CameraSettings;
import com.android.camera.IconListPreference;
import com.android.camera.ListPreference;
import com.android.camera.PreferenceGroup;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

// This is the UI for the on-screen settings. It mainly run in the GLThread. It
// will modify the shared-preferences. The concurrency rule is: The shared-
// preference will be updated in the GLThread. And an event will be trigger in
// the main UI thread so that the camera settings can be updated by reading the
// updated preferences. The two threads synchronize on the monitor of the
// default SharedPrefernce instance.
public class HeadUpDisplay extends GLView {
    private static final int INDICATOR_BAR_RIGHT_MARGIN = 10;
    private static final int POPUP_WINDOW_OVERLAP = 20;
    private static final int POPUP_TRIANGLE_OFFSET = 16;

    private static final float MAX_HEIGHT_RATIO = 0.8f;
    private static final float MAX_WIDTH_RATIO = 0.8f;

    private static final int DESELECT_INDICATOR = 0;
    private static final int DEACTIVATE_INDICATOR_BAR = 1;

    private static int sIndicatorBarRightMargin = -1;
    private static int sPopupWindowOverlap;
    private static int sPopupTriangleOffset;

    protected static final String TAG = "HeadUpDisplay";

    private IndicatorBar mIndicatorBar;
    private OtherSettingsIndicator mOtherSettings;
    private GpsIndicator mGpsIndicator;
    private ZoomIndicator mZoomIndicator;

    private PreferenceGroup mPreferenceGroup;
    private SharedPreferences mSharedPrefs;

    private PopupWindow mPopupWindow;

    private int mAnchorX;
    private int mAnchorY;
    private int mOrientation = 0;

    private Listener mListener;

    // TODO: move this part (handler) into GLSurfaceView
    private final HandlerThread mTimerThread = new HandlerThread("UI Timer");
    private final Handler mHandler;

    private final OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            new OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (mListener != null) {
                mListener.onSharedPreferencesChanged();
            }
        }
    };

    public HeadUpDisplay(Context context) {
        initializeStaticVariables(context);
        mTimerThread.setDaemon(true);
        mTimerThread.start();
        mHandler = new Handler(mTimerThread.getLooper()) {

            @Override
            public void handleMessage(Message msg) {
                GLRootView root = getGLRootView();
                FutureTask<Void> task = null;
                switch(msg.what) {
                    case DESELECT_INDICATOR:
                        task = new FutureTask<Void>(mDeselectIndicator);
                        break;
                    case DEACTIVATE_INDICATOR_BAR:
                        task = new FutureTask<Void>(mDeactivateIndicatorBar);
                        break;
                }

                if (task == null) return;
                try {
                    root.queueEvent(task);
                    task.get();
                } catch (Exception e) {
                    Log.e(TAG, "error in concurrent code", e);
                }
            }
        };
    }

    private static void initializeStaticVariables(Context context) {
        if (sIndicatorBarRightMargin >= 0) return;

        sIndicatorBarRightMargin = dpToPixel(context, INDICATOR_BAR_RIGHT_MARGIN);
        sPopupWindowOverlap = dpToPixel(context, POPUP_WINDOW_OVERLAP);
        sPopupTriangleOffset = dpToPixel(context, POPUP_TRIANGLE_OFFSET);
    }

    private final Callable<Void> mDeselectIndicator = new Callable<Void> () {
        public Void call() throws Exception {
            mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
            return null;
        }
    };

    private final Callable<Void> mDeactivateIndicatorBar = new Callable<Void> () {
        public Void call() throws Exception {
            if (mIndicatorBar != null) mIndicatorBar.setActivated(false);
            return null;
        }
    };

    /**
     * The callback interface. All the callbacks will be called from the
     * GLThread.
     */
    static public interface Listener {
        public void onPopupWindowVisibilityChanged(int visibility);
        public void onRestorePreferencesClicked();
        public void onSharedPreferencesChanged();
    }

    public void overrideSettings(final String ... keyvalues) {
        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        GLRootView root = getGLRootView();
        if (root != null) {
            root.queueEvent(new Runnable() {
                public void run() {
                    for (int i = 0, n = keyvalues.length; i < n; i += 2) {
                        mIndicatorBar.overrideSettings(
                                keyvalues[i], keyvalues[i + 1]);
                    }
                }
            });
        } else {
            for (int i = 0, n = keyvalues.length; i < n; i += 2) {
                mIndicatorBar.overrideSettings(keyvalues[i], keyvalues[i + 1]);
            }
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        mIndicatorBar.measure(
                MeasureSpec.makeMeasureSpec(width / 3, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        DisplayMetrics metrics = getGLRootView().getDisplayMetrics();
        int rightMargin = (int) (metrics.density * INDICATOR_BAR_RIGHT_MARGIN);

        mIndicatorBar.layout(
                width - mIndicatorBar.getMeasuredWidth() - rightMargin, 0,
                width - rightMargin, height);

        if(mPopupWindow != null
                && mPopupWindow.getVisibility() == GLView.VISIBLE) {
            layoutPopupWindow(mAnchorX, mAnchorY);
        }
    }

    public void initialize(Context context, PreferenceGroup preferenceGroup) {
        mPreferenceGroup = preferenceGroup;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mSharedPrefs.registerOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
        initializeIndicatorBar(context, preferenceGroup);
    }

    private void layoutPopupWindow(int anchorX, int anchorY) {
        mAnchorX = anchorX;
        mAnchorY = anchorY;

        int width = (int) (getWidth() * MAX_WIDTH_RATIO + .5);
        int height = (int) (getHeight() * MAX_HEIGHT_RATIO + .5);

        mPopupWindow.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        width = mPopupWindow.getMeasuredWidth();
        height = mPopupWindow.getMeasuredHeight();

        int xoffset = Math.max(anchorX - width, 0);
        int yoffset = Math.max(0, anchorY - height / 2);

        if (yoffset + height > getHeight()) {
            yoffset = getHeight() - height;
        }
        mPopupWindow.setAnchorPosition(anchorY - yoffset);
        mPopupWindow.layout(
                xoffset, yoffset, xoffset + width, yoffset + height);
    }

    private void showPopupWindow(int anchorX, int anchorY) {
        layoutPopupWindow(anchorX, anchorY);
        mPopupWindow.popup();
        mSharedPrefs.registerOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
        if (mListener != null) {
            mListener.onPopupWindowVisibilityChanged(GLView.VISIBLE);
        }
    }

    private void hidePopupWindow() {
        mPopupWindow.popoff();
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
        if (mListener != null) {
            mListener.onPopupWindowVisibilityChanged(GLView.INVISIBLE);
        }
    }

    private void scheduleDeactiviateIndicatorBar() {
        mHandler.removeMessages(DESELECT_INDICATOR);
        mHandler.sendEmptyMessageDelayed(DESELECT_INDICATOR, 3000);
        mHandler.removeMessages(DEACTIVATE_INDICATOR_BAR);
        mHandler.sendEmptyMessageDelayed(DEACTIVATE_INDICATOR_BAR, 4000);
    }

    public void deactivateIndicatorBar() {
        if (mIndicatorBar == null) return;
        mIndicatorBar.setActivated(false);
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
        if (mPopupWindow == null) return;
        if (mPopupWindow.getVisibility() == GLView.VISIBLE) {
            Animation alpha = new AlphaAnimation(0, 1);
            alpha.setDuration(250);
            mPopupWindow.startAnimation(alpha);
            scheduleDeactiviateIndicatorBar();
        }
        mPopupWindow.setOrientation(orientation);
    }

    private void initializePopupWindow(Context context) {
        mPopupWindow = new PopupWindow();
        mPopupWindow.setBackground(
                new NinePatchTexture(context, R.drawable.menu_popup));
        mPopupWindow.setAnchor(new ResourceTexture(
                context, R.drawable.menu_popup_triangle), sPopupTriangleOffset);
        mPopupWindow.setVisibility(GLView.INVISIBLE);
        mPopupWindow.setOrientation(mOrientation);
        addComponent(mPopupWindow);
    }

    @Override
    protected boolean dispatchTouchEvent(MotionEvent event) {
        if (super.dispatchTouchEvent(event)) {
            scheduleDeactiviateIndicatorBar();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        if (mPopupWindow == null
                || mPopupWindow.getVisibility() == GLView.INVISIBLE) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                hidePopupWindow();
                mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
                mIndicatorBar.setActivated(false);
                break;
        }
        return true;
    }

    private static ListPreference[] getListPreferences(
            PreferenceGroup group, String ... prefKeys) {
        ArrayList<ListPreference> list = new ArrayList<ListPreference>();
        for (String key : prefKeys) {
            ListPreference pref = group.findPreference(key);
            if (pref != null && pref.getEntries().length > 0) {
                list.add(pref);
            }
        }
        return list.toArray(new ListPreference[list.size()]);
    }

    private static BasicIndicator addIndicator(Context context,
            IndicatorBar indicatorBar, PreferenceGroup group, String key) {
        IconListPreference iconPref =
                (IconListPreference) group.findPreference(key);
        if (iconPref == null) return null;
        BasicIndicator indicator = new BasicIndicator(context, group, iconPref);
        indicatorBar.addComponent(indicator);
        return indicator;
    }

    private void initializeIndicatorBar(
            Context context, PreferenceGroup group) {

        mIndicatorBar = new IndicatorBar();

        mIndicatorBar.setBackground(new NinePatchTexture(
                context, R.drawable.ic_viewfinder_iconbar));
        mIndicatorBar.setHighlight(new NinePatchTexture(
                context, R.drawable.ic_viewfinder_iconbar_highlight));

        mOtherSettings = new OtherSettingsIndicator(
                context,
                getListPreferences(group,
                CameraSettings.KEY_FOCUS_MODE,
                CameraSettings.KEY_EXPOSURE,
                CameraSettings.KEY_SCENE_MODE,
                CameraSettings.KEY_PICTURE_SIZE,
                CameraSettings.KEY_JPEG_QUALITY,
                CameraSettings.KEY_COLOR_EFFECT));
        mOtherSettings.setOnRestorePreferencesClickedRunner(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onRestorePreferencesClicked();
                }
            }
        });
        mIndicatorBar.addComponent(mOtherSettings);

        GpsIndicator gpsIndicator = new GpsIndicator(
                context, group, (IconListPreference)
                group.findPreference(CameraSettings.KEY_RECORD_LOCATION));

        mGpsIndicator = gpsIndicator;
        mIndicatorBar.addComponent(gpsIndicator);

        addIndicator(context, mIndicatorBar, group,
                CameraSettings.KEY_WHITE_BALANCE);
        addIndicator(context, mIndicatorBar, group,
                CameraSettings.KEY_FLASH_MODE);

        mZoomIndicator = new ZoomIndicator(context);
        mIndicatorBar.addComponent(mZoomIndicator);

        addComponent(mIndicatorBar);
        mIndicatorBar.setOnItemSelectedListener(new IndicatorBarListener());
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

    private class IndicatorBarListener
            implements IndicatorBar.OnItemSelectedListener {

        public void onItemSelected(GLView view, int position) {
            Rect rect = new Rect();
            getBoundsOf(view, rect);
            int anchorX = rect.left + sPopupWindowOverlap;
            int anchorY = (rect.top + rect.bottom) / 2;

            AbstractIndicator indicator = (AbstractIndicator) view;
            if (mPopupWindow == null) {
                initializePopupWindow(getGLRootView().getContext());
            }
            mPopupWindow.setContent(indicator.getPopupContent());

            if (mPopupWindow.getVisibility() == GLView.VISIBLE) {
                layoutPopupWindow(anchorX, anchorY);
            } else {
                showPopupWindow(anchorX, anchorY);
            }
        }

        public void onNothingSelected() {
            hidePopupWindow();
        }
    }

    public void setZoomRatios(float[] zoomRatios) {
        mZoomIndicator.setZoomRatios(zoomRatios);
    }

    public void collapse() {
        getGLRootView().queueEvent(new Runnable() {
            public void run() {
                mIndicatorBar.setSelectedIndex(IndicatorBar.INDEX_NONE);
                mIndicatorBar.setActivated(false);
            }
        });
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void restorePreferences() {
        getGLRootView().queueEvent(new Runnable() {
            public void run() {
                OnSharedPreferenceChangeListener l =
                        mSharedPreferenceChangeListener;
                // Unregister the listener since "upgrade preference" will
                // change bunch of preferences. We can handle them with one
                // onSharedPreferencesChanged();
                mSharedPrefs.unregisterOnSharedPreferenceChangeListener(l);
                synchronized (mSharedPrefs) {
                    Editor editor = mSharedPrefs.edit();
                    editor.clear();
                    editor.commit();
                }
                CameraSettings.upgradePreferences(mSharedPrefs);
                mPreferenceGroup.reloadValue();
                mIndicatorBar.reloadPreferences();
                if (mListener != null) {
                    mListener.onSharedPreferencesChanged();
                }
                mSharedPrefs.registerOnSharedPreferenceChangeListener(l);
            }
        });
    }
}
