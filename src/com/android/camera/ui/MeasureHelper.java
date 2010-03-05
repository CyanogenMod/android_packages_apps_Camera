package com.android.camera.ui;

import android.graphics.Rect;
import android.view.View.MeasureSpec;

public class MeasureHelper {

    private final GLView mComponent;
    private int mPreferredWidth;
    private int mPreferredHeight;

    public MeasureHelper(GLView component) {
        mComponent = component;
    }

    public MeasureHelper setPreferredContentSize(int width, int height) {
        mPreferredWidth = width;
        mPreferredHeight = height;
        return this;
    }

    public void measure(int widthSpec, int heightSpec) {
        Rect p = mComponent.getPaddings();
        setMeasuredSize(
                getLength(widthSpec, mPreferredWidth + p.left + p.right),
                getLength(heightSpec, mPreferredHeight + p.top + p.bottom));
    }

    private static int getLength(int measureSpec, int prefered) {
        int specLength = MeasureSpec.getSize(measureSpec);
        switch(MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.EXACTLY: return specLength;
            case MeasureSpec.AT_MOST: return Math.min(prefered, specLength);
            default: return prefered;
        }
    }

    protected void setMeasuredSize(int width, int height) {
        mComponent.setMeasuredSize(width, height);
    }

}
