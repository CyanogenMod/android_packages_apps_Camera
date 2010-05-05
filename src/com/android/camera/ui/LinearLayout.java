package com.android.camera.ui;

import android.graphics.Rect;
import android.view.View.MeasureSpec;

class LinearLayout extends GLView {

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = 0;
        int height = 0;
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView view = getComponent(i);
            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            width = Math.max(width, view.getMeasuredWidth());
            height += view.getMeasuredHeight();
        }
        new MeasureHelper(this)
                .setPreferredContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Rect p = mPaddings;
        int offsetX = p.left;
        int width = (r - l) - p.left - p.right;
        int offsetY = p.top;
        for (int i = 0, n = getComponentCount(); i < n; ++i) {
            GLView view = getComponent(i);
            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            int nextOffsetY = offsetY + view.getMeasuredHeight();
            view.layout(offsetX, offsetY, offsetX + width, nextOffsetY);
            offsetY = nextOffsetY;
        }
    }

}
