package com.android.camera.ui;

import android.graphics.Color;
import android.graphics.Rect;

import javax.microedition.khronos.opengles.GL11;

public class GLZoomIndicator extends GLView {

    private final StringTexture mTitle;

    public GLZoomIndicator(String title) {
        mTitle = StringTexture.newInstance(title, 24, Color.WHITE);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        new MeasureHelper(this)
                .setPreferedContentSize(mTitle.getWidth(), mTitle.getHeight())
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        if (mTitle.bind(root, gl)) {
            Rect p = mPaddings;
            int width = getWidth() - p.left - p.right;
            int height = getHeight() - p.top - p.bottom;
            mTitle.draw(root,
                    p.left + (width - mTitle.getWidth()) / 2,
                    p.top + (height - mTitle.getHeight()) / 2);
        }
    }
}
