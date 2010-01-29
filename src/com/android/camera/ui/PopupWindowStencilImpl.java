package com.android.camera.ui;

import android.graphics.Rect;

import javax.microedition.khronos.opengles.GL11;

public class PopupWindowStencilImpl extends PopupWindow {


    @Override
    protected void renderBackground(GLRootView rootView, GL11 gl) {
        int width = getWidth();
        int height = getHeight();
        int aWidth = mAnchor.getWidth();
        int aHeight = mAnchor.getHeight();

        Rect p = mPaddings;
        int aXoffset = width - aWidth;
        int aYoffset = Math.max(p.top, mAnchorPosition - aHeight / 2);
        aYoffset = Math.min(aYoffset, height - p.bottom - aHeight);

        if (mAnchor != null) {
            gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            gl.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
            if (mAnchor.bind(rootView, gl)) {
                rootView.draw2D(aXoffset, aYoffset, aWidth, aHeight);
            }
            gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
            gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }

        if (mBackground != null) {
            mBackground.setSize(width - aWidth + mAnchorOffset, height);
            if (mBackground.bind(rootView, gl)) {
                rootView.draw2D(
                        0, 0, mBackground.getWidth(), mBackground.getHeight());
            }
        }
    }

}
