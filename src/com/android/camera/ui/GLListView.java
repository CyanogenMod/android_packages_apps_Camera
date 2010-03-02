package com.android.camera.ui;

import static android.view.View.MeasureSpec.makeMeasureSpec;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;

import javax.microedition.khronos.opengles.GL11;

public class GLListView extends GLView {
    private static final String TAG = "GLListView";

    private static final int MOTION_THRESHOLD = 15;
    private static final int INDEX_NONE = -1;
    private static final int MOTION_NONE = -1;

    private Model mModel;

    private int mHighlightIndex = INDEX_NONE;
    private GLView mHighlightView;

    private NinePatchTexture mHighLight;
    private NinePatchTexture mScrollbar;

    private int mVisibleStart = 0; // inclusive
    private int mVisibleEnd = 0; // exclusive

    private int mMotionStartY = MOTION_NONE;
    private boolean mHasMeasured = false;
    private boolean mHasMoved = false;

    private OnItemSelectedListener mOnItemSelectedListener;

    static public interface Model {
        public int size();
        public GLView getView(int index);
        public boolean isSelectable(int index);
    }

    static public interface OnItemSelectedListener {
        public void onItemSelected(GLView view, int position);
    }

    public void setHighLight(NinePatchTexture highLight) {
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

    @Override
    protected void render(GLRootView root, GL11 gl) {
        root.clipRect(0, 0, getWidth(), getHeight());
        if (mHighlightIndex != INDEX_NONE) {
            GLView view = mModel.getView(mHighlightIndex);
            Rect bounds = view.bounds();
            if (mHighLight != null) {
                int width = bounds.width();
                int height = bounds.height();
                mHighLight.setSize(width, height);
                if (mHighLight.bind(root, gl)) {
                    root.draw2D(bounds.left - mScrollX,
                            bounds.top - mScrollY, width, height);
                }
            }
        }
        super.render(root, gl);
        root.clearClip();

        if (mScrollbar != null && mScrollHeight > getHeight()) {
            int width = this.mScrollbar.getIntrinsicWidth();
            int height = getHeight() * getHeight() / mScrollHeight;
            mScrollbar.setSize(width, height);
            if (mScrollbar.bind(root, gl)) {
                int yoffset = mScrollY * getHeight() / mScrollHeight;
                root.draw2D(getWidth() - width, yoffset, width, height);
            }
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {

        int heightMode = MeasureSpec.getMode(heightSpec);
        int widthMode = MeasureSpec.getMode(widthSpec);

        // first get the total height
        int height = 0;
        int maxWidth = 0;
        for (int i = 0, n = mModel.size(); i < n; ++i) {
            GLView view = mModel.getView(i);
            view.measure(widthSpec, MeasureSpec.UNSPECIFIED);
            height += view.getMeasuredHeight();
            maxWidth = Math.max(maxWidth, view.getMeasuredWidth());
        }

        // if we need to show the scroll bar ...
        if ((mScrollbar != null && (heightMode == MeasureSpec.AT_MOST
                || heightMode == MeasureSpec.EXACTLY))
                && height > MeasureSpec.getSize(heightSpec)) {
            if (widthMode != MeasureSpec.UNSPECIFIED) {
                int cWidthSpec = widthSpec - mScrollbar.getIntrinsicWidth();
                height = 0;
                for (int i = 0, n = mModel.size(); i < n; ++i) {
                    GLView view = mModel.getView(i);
                    view.measure(cWidthSpec, MeasureSpec.UNSPECIFIED);
                    height += view.getMeasuredHeight();
                    maxWidth = Math.max(maxWidth, view.getMeasuredWidth());
                }
            }
            maxWidth += mScrollbar.getIntrinsicWidth();
        }

        mScrollHeight = height;
        mHasMeasured = true;

        new MeasureHelper(this)
                .setPreferedContentSize(maxWidth, height)
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

        int width = mScrollHeight > (bottom - top) && mScrollbar != null
                ? right - left - mScrollbar.getIntrinsicWidth()
                : right - left;
        int yoffset = 0;

        for (int i = 0, n = mModel.size(); i < n; ++i) {
            GLView item = mModel.getView(i);
            int nextOffset = yoffset + item.getMeasuredHeight();
            item.layout(0, yoffset, width, nextOffset);
            yoffset = nextOffset;
        }
        setScrollPosition(mScrollY, true);
    }

    private void setScrollPosition(int position, boolean force) {
        int height = getHeight();

        position = Math.max(0, position);
        position = Math.min(mScrollHeight - height, position);

        if (!force && position == mScrollY) return;
        mScrollY = position;

        int n = mModel.size();

        int start = 0;
        int end = 0;
        for (start = 0; start < n; ++start) {
            if (position < mModel.getView(start).mBounds.bottom) break;
        }

        position += height;
        for (end = start; end < n; ++ end) {
            if (position <= mModel.getView(end).mBounds.top) break;
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
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if ((mMotionStartY != MOTION_NONE) && (mHasMoved
                        || Math.abs(y - mMotionStartY) > MOTION_THRESHOLD)) {
                    setHighlightItem(null, INDEX_NONE);
                    setScrollPosition(mScrollY + mMotionStartY - y, false);
                    mMotionStartY = y;
                    mHasMoved = true;
                } else {
                    findAndSetHighlightItem(y);
                }
                break;
            case MotionEvent.ACTION_DOWN:
                mHasMoved = false;
                mMotionStartY = mScrollHeight > getHeight() ? y : MOTION_NONE;
                break;
            case MotionEvent.ACTION_UP:
                if (!mHasMoved) {
                    if (mOnItemSelectedListener != null && mHighlightView != null) {
                        mOnItemSelectedListener
                                .onItemSelected(mHighlightView, mHighlightIndex);
                    }
                    mHasMoved = false;
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
}
