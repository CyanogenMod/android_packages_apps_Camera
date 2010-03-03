package com.android.camera.ui;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL11;

public class GLView {
    GLRootView mRootView;

    public static final int STATE_EMPTY = 0;
    public static final int STATE_PRESSED = 1;

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 1;

    public static final int FLAG_INVISIBLE = 1;
    public static final int FLAG_SET_MEASURED_SIZE = 2;
    public static final int FLAG_LAYOUT_REQUESTED = 4;

    private static final String TAG = "GLView";

    protected final Rect mBounds = new Rect();
    protected final Rect mPaddings = new Rect();

    private GLView mParent;
    private ArrayList<GLView> mComponents;

    private OnTouchListener mOnTouchListener;
    private Animation mAnimation;

    protected int mViewState = STATE_EMPTY;
    protected int mViewFlags = 0;

    protected int mMeasuredWidth = 0;
    protected int mMeasuredHeight = 0;

    private int mLastWidthSpec = -1;
    private int mLastHeightSpec = -1;

    protected int mScrollY = 0;
    protected int mScrollX = 0;
    protected int mScrollHeight = 0;
    protected int mScrollWidth = 0;

    protected void onStateChanged(int oldState, int newState) {
    }

    protected void addStates(int states) {
        int newState = (mViewState | states);
        if (newState != mViewState) {
            onStateChanged(mViewState, newState);
            mViewState = newState;
        }
    }

    protected void removeStates(int states) {
        int newState = (mViewState & ~states);
        if (newState != mViewState) {
            onStateChanged(mViewState, newState);
            mViewState = newState;
        }
    }

    public void startAnimation(Animation animation) {
        GLRootView root = getGLRootView();
        if (root == null) throw new IllegalStateException();

        mAnimation = animation;
        animation.initialize(getWidth(),
                getHeight(), mParent.getWidth(), mParent.getHeight());
        mAnimation.start();
        root.registerLaunchedAnimation(animation);
        invalidate();
    }

    public void setVisibility(int visibility) {
        if (visibility == getVisibility()) return;
        if (visibility == VISIBLE) {
            mViewFlags &= ~FLAG_INVISIBLE;
        } else {
            mViewFlags |= FLAG_INVISIBLE;
        }
        invalidate();
    }

    public int getVisibility() {
        return (mViewFlags & FLAG_INVISIBLE) == 0 ? VISIBLE : INVISIBLE;
    }

    public static interface OnTouchListener {
        public boolean onTouch(GLView view, MotionEvent event);
    }

    public boolean setBounds(int left, int top, int right, int bottom) {
        boolean sizeChanged = (right - left) != (mBounds.right - mBounds.left)
                || (bottom - top) != (mBounds.bottom - mBounds.top);
        mBounds.set(left, top, right, bottom);
        return sizeChanged;
    }

    protected void onAddToParent(GLView parent) {
        mParent = parent;
    }

    public void clearComponents() {
        if (mComponents != null) mComponents.clear();
    }

    public int getComponentCount() {
        return mComponents == null ? 0 : mComponents.size();
    }

    public GLView getComponent(int index) {
        if (mComponents == null) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mComponents.get(index);
    }

    public void addComponent(GLView component) {
        if (mComponents == null) {
            mComponents = new ArrayList<GLView>();
        }
        mComponents.add(component);
        component.onAddToParent(this);
    }

    public Rect bounds() {
        return mBounds;
    }

    public int getWidth() {
        return mBounds.right - mBounds.left;
    }

    public int getHeight() {
        return mBounds.bottom - mBounds.top;
    }

    public GLRootView getGLRootView() {
        if (mRootView == null && mParent != null) {
            mRootView = mParent.getGLRootView();
        }
        return mRootView;
    }

    public void setOnTouchListener(OnTouchListener listener) {
        mOnTouchListener = listener;
    }

    public void invalidate() {
        GLRootView root = getGLRootView();
        if (root != null) root.requestRender();
    }

    public void requestLayout() {
        mViewFlags |= FLAG_LAYOUT_REQUESTED;
        if (mParent != null) {
            mParent.requestLayout();
        } else {
            // Is this a content pane ?
            GLRootView root = getGLRootView();
            if (root != null) root.requestLayoutContentPane();
        }
    }

    protected void render(GLRootView view, GL11 gl) {
        renderBackground(view, gl);
        int n = getComponentCount();
        if (n == 0) return;
        for (int i = 0; i < n; ++i) {
            GLView component = getComponent(i);
            if (component.getVisibility() != GLView.VISIBLE
                    && component.mAnimation == null) continue;
            renderChild(view, gl, component);
        }
    }

    protected void renderBackground(GLRootView view, GL11 gl) {
    }

    protected void renderChild(GLRootView root, GL11 gl, GLView component) {
        int xoffset = component.mBounds.left - mScrollX;
        int yoffset = component.mBounds.top - mScrollY;

        Transformation transform = root.getTransformation();
        Matrix matrix = transform.getMatrix();
        matrix.preTranslate(xoffset, yoffset);

        Animation anim = component.mAnimation;
        if (anim != null) {
            long now = root.currentAnimationTimeMillis();
            Transformation temp = root.obtainTransformation();
            temp.clear();
            if (!anim.getTransformation(now, temp)) {
                component.mAnimation = null;
            }
            invalidate();
            root.pushTransform();
            transform.compose(temp);
            root.freeTransformation(temp);
        }
        component.render(root, gl);
        if (anim != null) root.popTransform();
        matrix.preTranslate(-xoffset, -yoffset);
    }

    protected boolean onTouch(MotionEvent event) {
        if (mOnTouchListener != null) {
            return mOnTouchListener.onTouch(this, event);
        }
        return false;
    }

    protected boolean dispatchTouchEvent(MotionEvent event) {
        if (mComponents != null) {
            int eventX = (int) event.getX();
            int eventY = (int) event.getY();
            for (int i = 0, n = getComponentCount(); i < n; ++i) {
                GLView component = getComponent(i);
                if (component.getVisibility() != GLView.VISIBLE) continue;
                Rect rect = new Rect(component.mBounds);
                if (rect.contains(eventX, eventY)) {
                    event.offsetLocation(-rect.left, -rect.top);
                    if (component.dispatchTouchEvent(event)) return true;
                    event.offsetLocation(rect.left, rect.top);
                }
            }
        }
        return onTouch(event);
    }

    public Rect getPaddings() {
        return mPaddings;
    }

    public void setPaddings(Rect paddings) {
        mPaddings.set(paddings);
    }

    public void setPaddings(int left, int top, int right, int bottom) {
        mPaddings.set(left, top, right, bottom);
    }

    public void layout(int left, int top, int right, int bottom) {
        boolean sizeChanged = setBounds(left, top, right, bottom);
        if (sizeChanged) {
            mViewFlags &= ~FLAG_LAYOUT_REQUESTED;
            onLayout(true, left, top, right, bottom);
        } else if ((mViewFlags & FLAG_LAYOUT_REQUESTED)!= 0) {
            mViewFlags &= ~FLAG_LAYOUT_REQUESTED;
            onLayout(false, left, top, right, bottom);
        }
    }

    public void measure(int widthSpec, int heightSpec) {
        if (widthSpec == mLastWidthSpec && heightSpec == mLastHeightSpec
                && (mViewFlags & FLAG_LAYOUT_REQUESTED) == 0) {
            return;
        }

        mLastWidthSpec = widthSpec;
        mLastHeightSpec = heightSpec;

        mViewFlags &= ~FLAG_SET_MEASURED_SIZE;
        onMeasure(widthSpec, heightSpec);
        if ((mViewFlags & FLAG_SET_MEASURED_SIZE) == 0) {
            throw new IllegalStateException(getClass().getName()
                    + " should call setMeasuredSize() in onMeasure()");
        }
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
    }

    protected void setMeasuredSize(int width, int height) {
        mViewFlags |= FLAG_SET_MEASURED_SIZE;
        mMeasuredWidth = width;
        mMeasuredHeight = height;
    }

    public int getMeasuredWidth() {
        return mMeasuredWidth;
    }

    public int getMeasuredHeight() {
        return mMeasuredHeight;
    }

    protected void onLayout(
            boolean changeSize, int left, int top, int right, int bottom) {
    }

    public boolean getBoundsOf(GLView child, Rect out) {
        int xoffset = 0;
        int yoffset = 0;
        GLView view = child;
        while (view != this) {
            if (view == null) return false;
            Rect bounds = view.mBounds;
            xoffset += bounds.left;
            yoffset += bounds.top;
            view = view.mParent;
        }
        out.set(xoffset, yoffset,
                xoffset + child.getWidth(), yoffset + child.getHeight());
        return true;
    }

}
