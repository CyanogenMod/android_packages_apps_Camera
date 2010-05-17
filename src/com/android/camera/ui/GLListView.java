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

import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Scroller;

import com.android.camera.Util;

import javax.microedition.khronos.opengles.GL11;

class GLListView extends GLView {
    @SuppressWarnings("unused")
    private static final String TAG = "GLListView";
    private static final int INDEX_NONE = -1;
    private static final int SCROLL_BAR_TIMEOUT = 2500;

    private static final int HIDE_SCROLL_BAR = 1;

    private Model mModel;
    private Handler mHandler;

    private int mHighlightIndex = INDEX_NONE;
    private GLView mHighlightView;

    private Texture mHighLight;
    private NinePatchTexture mScrollbar;

    private int mVisibleStart = 0; // inclusive
    private int mVisibleEnd = 0; // exclusive

    private boolean mHasMeasured = false;

    private boolean mScrollBarVisible = false;
    private Animation mScrollBarAnimation;
    private OnItemSelectedListener mOnItemSelectedListener;

    private GestureDetector mGestureDetector;
    private final Scroller mScroller;
    private boolean mScrollable;
    private boolean mIsPressed = false;

    static public interface Model {
        public int size();
        public GLView getView(int index);
        public boolean isSelectable(int index);
    }

    static public interface OnItemSelectedListener {
        public void onItemSelected(GLView view, int position);
    }

    public GLListView(Context context) {
        mScroller = new Scroller(context);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                GLRootView root = getGLRootView();
                if (root != null) {
                    synchronized (root) {
                        handleMessageLocked(msg);
                    }
                } else {
                    handleMessageLocked(msg);
                }
            }

            private void handleMessageLocked(Message msg) {
                switch(msg.what) {
                    case HIDE_SCROLL_BAR:
                        setScrollBarVisible(false);
                        break;
                }
            }
        };
        mGestureDetector = new GestureDetector(
                context, new MyGestureListener(), mHandler);
    }

    @Override
    protected void onVisibilityChanged(int visibility) {
        super.onVisibilityChanged(visibility);
        if (visibility == GLView.VISIBLE && mScrollHeight > getHeight()) {
            setScrollBarVisible(true);
            mHandler.sendEmptyMessageDelayed(
                    HIDE_SCROLL_BAR, SCROLL_BAR_TIMEOUT);
        }
    }

    private void setScrollBarVisible(boolean visible) {
        if (mScrollBarVisible == visible || mScrollbar == null) return;
        mScrollBarVisible = visible;
        if (!visible) {
            mScrollBarAnimation = new AlphaAnimation(1, 0);
            mScrollBarAnimation.setDuration(300);
            mScrollBarAnimation.start();
        } else {
            mScrollBarAnimation = null;
        }
        invalidate();
    }

    public void setHighLight(Texture highLight) {
        mHighLight = highLight;
    }

    public void setDataModel(Model model) {
        mModel = model;
        mScrollY = 0;
        requestLayout();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        mOnItemSelectedListener = l;
    }

    private boolean drawWithAnimation(GLRootView root,
            Texture texture, int x, int y, int w, int h, Animation anim) {
        long now = root.currentAnimationTimeMillis();
        Transformation temp = root.obtainTransformation();
        boolean more = anim.getTransformation(now, temp);
        Transformation transformation = root.pushTransform();
        transformation.compose(temp);
        texture.draw(root, x, y, w, h);
        invalidate();
        root.popTransform();
        return more;
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        root.clipRect(0, 0, getWidth(), getHeight());
        if (mHighlightIndex != INDEX_NONE) {
            GLView view = mModel.getView(mHighlightIndex);
            Rect bounds = view.bounds();
            if (mHighLight != null) {
                int width = bounds.width();
                int height = bounds.height();
                mHighLight.draw(root,
                        bounds.left - mScrollX, bounds.top - mScrollY,
                        width, height);
            }
        }
        super.render(root, gl);
        root.clearClip();

        if (mScrollBarAnimation != null || mScrollBarVisible) {
            int width = mScrollbar.getWidth();
            int height = getHeight() * getHeight() / mScrollHeight;
            int yoffset = mScrollY * getHeight() / mScrollHeight;
            if (mScrollBarAnimation != null) {
                if (!drawWithAnimation(
                        root, mScrollbar, getWidth() - width, yoffset,
                        width, height, mScrollBarAnimation)) {
                    mScrollBarAnimation = null;
                }
            } else {
                mScrollbar.draw(
                        root, getWidth() - width, yoffset, width, height);
            }
        }
        if (mScroller.computeScrollOffset()) {
            setScrollPosition(mScroller.getCurrY(), false);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // first get the total height
        int height = 0;
        int maxWidth = 0;
        for (int i = 0, n = mModel.size(); i < n; ++i) {
            GLView view = mModel.getView(i);
            view.measure(widthSpec, MeasureSpec.UNSPECIFIED);
            height += view.getMeasuredHeight();
            maxWidth = Math.max(maxWidth, view.getMeasuredWidth());
        }
        mScrollHeight = height;
        mHasMeasured = true;
        new MeasureHelper(this)
                .setPreferredContentSize(maxWidth, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    public int getComponentCount() {
        return mVisibleEnd - mVisibleStart;
    }

    @Override
    public GLView getComponent(int index) {
        if (index < 0 || index >= mVisibleEnd - mVisibleStart) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mModel.getView(mVisibleStart + index);
    }

    @Override
    public void requestLayout() {
        mHasMeasured = false;
        super.requestLayout();
    }

    @Override
    protected void onLayout(
            boolean change, int left, int top, int right, int bottom) {

        if (!mHasMeasured || mMeasuredWidth != (right - left)) {
            measure(makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
                    makeMeasureSpec(bottom - top, MeasureSpec.EXACTLY));
        }

        mScrollable = mScrollHeight > (bottom - top);
        int width = right - left;
        int yoffset = 0;

        for (int i = 0, n = mModel.size(); i < n; ++i) {
            GLView item = mModel.getView(i);
            item.onAddToParent(this);
            int nextOffset = yoffset + item.getMeasuredHeight();
            item.layout(0, yoffset, width, nextOffset);
            yoffset = nextOffset;
        }
        setScrollPosition(mScrollY, true);
    }

    private void setScrollPosition(int position, boolean force) {
        int height = getHeight();

        position = Util.clamp(position, 0, mScrollHeight - height);

        if (!force && position == mScrollY) return;
        mScrollY = position;

        int n = mModel.size();

        int start = 0;
        int end = 0;
        for (start = 0; start < n; ++start) {
            if (position < mModel.getView(start).mBounds.bottom) break;
        }

        int bottom = position + height;
        for (end = start; end < n; ++ end) {
            if (bottom <= mModel.getView(end).mBounds.top) break;
        }
        setVisibleRange(start , end);
        invalidate();
    }

    private void setVisibleRange(int start, int end) {
        if (start == mVisibleStart && end == mVisibleEnd) return;
        mVisibleStart = start;
        mVisibleEnd = end;
    }

    @Override
    protected boolean dispatchTouchEvent(MotionEvent event) {
        return onTouch(event);
    }

    @Override @SuppressWarnings("fallthrough")
    protected boolean onTouch(MotionEvent event) {

        mGestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsPressed = true;
                mHandler.removeMessages(HIDE_SCROLL_BAR);
                setScrollBarVisible(mScrollHeight > getHeight());

                // fallthrough: we need to highlight the item which is pressed
            case MotionEvent.ACTION_MOVE:
                if (!mScrollable) {
                    findAndSetHighlightItem((int) event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                mIsPressed = false;
                if (mScrollBarVisible) {
                    mHandler.removeMessages(HIDE_SCROLL_BAR);
                    mHandler.sendEmptyMessageDelayed(
                            HIDE_SCROLL_BAR, SCROLL_BAR_TIMEOUT);
                }
                if (!mScrollable && mOnItemSelectedListener != null
                        && mHighlightView != null) {
                    mOnItemSelectedListener
                            .onItemSelected(mHighlightView, mHighlightIndex);
                }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                setHighlightItem(null, INDEX_NONE);
        }
        return true;
    }

    private void findAndSetHighlightItem(int y) {
        int position = y + mScrollY;
        for (int i = mVisibleStart, n = mVisibleEnd; i < n; ++i) {
            GLView child = mModel.getView(i);
            if (child.mBounds.bottom > position) {
                if (mModel.isSelectable(i)) {
                    setHighlightItem(child, i);
                    return;
                }
                break;
            }
        }
        setHighlightItem(null, INDEX_NONE);
    }

    private void setHighlightItem(GLView view, int index) {
        if (index == mHighlightIndex) return;
        mHighlightIndex = index;
        mHighlightView = view;
        if (mHighLight != null) invalidate();
    }

    public void setScroller(NinePatchTexture scrollbar) {
        this.mScrollbar = scrollbar;
        requestLayout();
    }

    private class MyGestureListener
            extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1,
                MotionEvent e2, float velocityX, float velocityY) {
            if (!mScrollable) return false;
            mScroller.fling(0, mScrollY,
                    0, -(int) velocityY, 0, 0, 0, mScrollHeight - getHeight());
            invalidate();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1,
                MotionEvent e2, float distanceX, float distanceY) {
            if (!mScrollable) return false;
            setHighlightItem(null, INDEX_NONE);
            setScrollPosition(mScrollY + (int) distanceY, false);
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            if (!mScrollable || !mIsPressed) return;
            findAndSetHighlightItem((int) e.getY());
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (!mScrollable) return false;
            findAndSetHighlightItem((int) e.getY());
            if (mOnItemSelectedListener != null && mHighlightView != null) {
                mOnItemSelectedListener
                        .onItemSelected(mHighlightView, mHighlightIndex);
            }
            setHighlightItem(null, INDEX_NONE);
            return true;
        }
    }
}
