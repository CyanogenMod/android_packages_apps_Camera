package com.android.camera.ui;

import android.content.Context;
import android.graphics.Color;

import com.android.camera.R;

import com.android.camera.ui.ZoomController.ZoomListener;

import java.text.DecimalFormat;

public class ZoomIndicator extends AbstractIndicator {
    private static final DecimalFormat sZoomFormat = new DecimalFormat("#.#x");
    private static final float FONT_SIZE = 18;
    private static final int FONT_COLOR = Color.WHITE;

    protected static final String TAG = "ZoomIndicator";

    private final float mFontSize;

    private ZoomController mZoomController;
    private LinearLayout mPopupContent;
    private ZoomListener mZoomListener;
    private int mZoomIndex = 0;
    private float mZoomRatios[];

    private StringTexture mTitle;
    private float mZoom = 1.0f;

    public ZoomIndicator(Context context) {
        super(context);
        mFontSize = GLRootView.dpToPixel(context, FONT_SIZE);
        mTitle = StringTexture.newInstance(
                sZoomFormat.format(mZoom), mFontSize, FONT_COLOR);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        new MeasureHelper(this)
                .setPreferredContentSize(mTitle.getWidth(), mTitle.getHeight())
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected Texture getIcon() {
        return mTitle;
    }

    @Override
    public GLView getPopupContent() {
        if (mZoomController == null) {
            Context context = getGLRootView().getContext();
            mZoomController = new ZoomController(context);
            mZoomController.setAvailableZoomRatios(mZoomRatios);
            mZoomController.setPaddings(15, 6, 15, 6);

            mPopupContent = new LinearLayout();
            GLOptionHeader header = new GLOptionHeader(context,
                    context.getString(R.string.zoom_control_title));
            header.setBackground(new NinePatchTexture(
                    context, R.drawable.optionheader_background));
            header.setPaddings(6, 3, 6, 3);
            mPopupContent.addComponent(header);
            mPopupContent.addComponent(mZoomController);

            mZoomController.setZoomListener(new MyZoomListener());
            mZoomController.setZoomIndex(mZoomIndex);
        }
        return mPopupContent;
    }

    @Override
    public void overrideSettings(String key, String settings) {
        // do nothing
    }

    public void setZoomRatios(float[] ratios) {
        mZoomRatios = ratios;
    }

    private class MyZoomListener implements ZoomController.ZoomListener {
        public void onZoomChanged(int index, float value, boolean isMoving) {
            if (mZoomListener != null) {
                mZoomListener.onZoomChanged(index, value, isMoving);
            }
            if (mZoom != value) {
                mZoom = value;
                mTitle = StringTexture.newInstance(
                        sZoomFormat.format(value), mFontSize, Color.WHITE);
                invalidate();
            }
        }
    }

    public void setZoomListener(ZoomListener listener) {
        mZoomListener = listener;
    }

    public void setZoomIndex(int index) {
        if (mZoomController != null) {
            mZoomController.setZoomIndex(index);
        }
        mZoomIndex = index;
    }
}
