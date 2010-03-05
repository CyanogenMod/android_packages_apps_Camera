package com.android.camera.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;

import javax.microedition.khronos.opengles.GL11;

public class ZoomIndicator extends GLView {

    private static final int FONT_SIZE = 16;

    private final StringTexture mTitle;

    public ZoomIndicator(Context context, String title) {
        float fontSize = GLRootView.dpToPixel(context, FONT_SIZE);
        mTitle = StringTexture.newInstance(title, fontSize, Color.WHITE);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        new MeasureHelper(this)
                .setPreferredContentSize(mTitle.getWidth(), mTitle.getHeight())
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
