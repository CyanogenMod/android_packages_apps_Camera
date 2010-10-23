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

import com.android.camera.R;

import android.graphics.Rect;
import android.view.View.MeasureSpec;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;

import javax.microedition.khronos.opengles.GL11;

public class PopupWindow extends GLView {

    protected Texture mAnchor;
    protected int mAnchorOffset;

    protected int mAnchorPosition;
    private final RotatePane mRotatePane = new RotatePane();
    private RawTexture mBackupTexture;

    protected FrameTexture mBackground;

    public PopupWindow() {
        super.addComponent(mRotatePane);
    }

    public void setBackground(FrameTexture background) {
        if (background == mBackground) return;
        mBackground = background;
        if (background != null) {
            setPaddings(mBackground.getPaddings());
        } else {
            setPaddings(0, 0, 0, 0);
        }
        invalidate();
    }

    public void setAnchor(Texture anchor, int offset) {
        mAnchor = anchor;
        mAnchorOffset = offset;
    }

    @Override
    public void addComponent(GLView component) {
        throw new UnsupportedOperationException("use setContent(GLView)");
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int widthMode = MeasureSpec.getMode(widthSpec);
        if (widthMode != MeasureSpec.UNSPECIFIED) {
            Rect p = mPaddings;
            int width = MeasureSpec.getSize(widthSpec);
            widthSpec = MeasureSpec.makeMeasureSpec(
                    Math.max(0, width - p.left - p.right
                    - mAnchor.getWidth() + mAnchorOffset), widthMode);
        }

        int heightMode = MeasureSpec.getMode(heightSpec);
        if (heightMode != MeasureSpec.UNSPECIFIED) {
            int height = MeasureSpec.getSize(widthSpec);
            widthSpec = MeasureSpec.makeMeasureSpec(Math.max(
                    0, height - mPaddings.top - mPaddings.bottom), heightMode);
        }

        Rect p = mPaddings;
        GLView child = mRotatePane;
        child.measure(widthSpec, heightSpec);
        setMeasuredSize(child.getMeasuredWidth()
                + p.left + p.right + mAnchor.getWidth() - mAnchorOffset,
                child.getMeasuredHeight() + p.top + p.bottom);
    }

    @Override
    protected void onLayout(
            boolean change, int left, int top, int right, int bottom) {
        Rect p = getPaddings();
        GLView view = mRotatePane;
        view.layout(p.left, p.top,
                getWidth() - p.right - mAnchor.getWidth() + mAnchorOffset,
                getHeight() - p.bottom);
    }

    public void setAnchorPosition(int yoffset) {
        mAnchorPosition = yoffset;
    }

    @Override
    protected void renderBackground(GLRootView root, GL11 gl) {
        int width = getWidth();
        int height = getHeight();
        int aWidth = mAnchor.getWidth();
        int aHeight = mAnchor.getHeight();

        Rect p = mPaddings;
        int aXoffset = width - aWidth;
        int aYoffset = Math.max(p.top, mAnchorPosition - aHeight / 2);
        aYoffset = Math.min(aYoffset, height - p.bottom - aHeight);

        if (mAnchor != null) {
            mAnchor.draw(root, aXoffset, aYoffset);
        }

        if (mBackupTexture == null || mBackupTexture.getBoundGL() != gl) {
            mBackupTexture = RawTexture.newInstance(gl);
        }

        RawTexture backup = mBackupTexture;
        try {
            // Copy the current drawing results of the triangle area into
            // "backup", so that we can restore the content after it is
            // overlaid by the background.
            root.copyTexture2D(backup, aXoffset, aYoffset, aWidth, aHeight);
        } catch (GLOutOfMemoryException e) {
            e.printStackTrace();
        }

        if (mBackground != null) {
            mBackground.setSize(width - aWidth + mAnchorOffset, height);
            mBackground.draw(root, 0, 0);
        }

        gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        backup.draw(root, aXoffset, aYoffset, aWidth, aHeight, 1);
        if (getGLRootView().getContext().getResources().getBoolean(R.bool.softwareGLOnly)) {
            gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        } else {
            gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    public void setContent(GLView content) {
        mRotatePane.setContent(content);
    }

    @Override
    public void clearComponents() {
        throw new UnsupportedOperationException();
    }

    public void popup() {
        setVisibility(GLView.VISIBLE);

        AnimationSet set = new AnimationSet(false);
        Animation scale = new ScaleAnimation(
                0.7f, 1f, 0.7f, 1f, getWidth(), mAnchorPosition);
        Animation alpha = new AlphaAnimation(0.5f, 1.0f);

        set.addAnimation(scale);
        set.addAnimation(alpha);
        scale.setDuration(150);
        alpha.setDuration(100);
        scale.setInterpolator(new OvershootInterpolator());
        startAnimation(set);
    }

    public void popoff() {
        setVisibility(GLView.INVISIBLE);
        Animation alpha = new AlphaAnimation(0.7f, 0.0f);
        alpha.setDuration(100);
        startAnimation(alpha);
    }

    public void setOrientation(int orientation) {
        switch (orientation) {
            case 90:
                mRotatePane.setOrientation(RotatePane.LEFT);
                break;
            case 180:
                mRotatePane.setOrientation(RotatePane.DOWN);
                break;
            case 270:
                mRotatePane.setOrientation(RotatePane.RIGHT);
                break;
            default:
                mRotatePane.setOrientation(RotatePane.UP);
                break;
        }
    }

}
