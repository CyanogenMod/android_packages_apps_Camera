/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.camera;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.Config;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;


public class HighlightView
{
    private static final String TAG = "CropImage";
    View mContext;
    Path mPath;
    Rect mViewDrawingRect = new Rect();

    int mMotionMode;
    
    public static final int GROW_NONE        = (1 << 0);
    public static final int GROW_LEFT_EDGE   = (1 << 1);
    public static final int GROW_RIGHT_EDGE  = (1 << 2);
    public static final int GROW_TOP_EDGE    = (1 << 3);
    public static final int GROW_BOTTOM_EDGE = (1 << 4);
    public static final int MOVE             = (1 << 5);
    
    public HighlightView(View ctx) 
    {
        super();
        mContext = ctx;
        mPath = new Path();
    }

    private void initHighlightView() {
        android.content.res.Resources resources = mContext.getResources();
        mResizeDrawableWidth  = resources.getDrawable(R.drawable.camera_crop_width);
        mResizeDrawableHeight = resources.getDrawable(R.drawable.camera_crop_height);
        mResizeDrawableDiagonal = resources.getDrawable(R.drawable.indicator_autocrop);
    }
    
    boolean mIsFocused;
    boolean mHidden;
    
    public boolean hasFocus() {
        return mIsFocused;
    }
    
    public void setFocus(boolean f) {
        mIsFocused = f;
    }
    
    public void setHidden(boolean hidden) {
        mHidden = hidden;
    }
    
    protected void draw(Canvas canvas) {
        if (mHidden)
            return;
        
        canvas.save();
        mPath.reset();
        if (!hasFocus()) {
            mOutlinePaint.setColor(0xFF000000);
            canvas.drawRect(mDrawRect, mOutlinePaint);
        } else {
            mContext.getDrawingRect(mViewDrawingRect);
            if (mCircle) {
                float width  = mDrawRect.width()  - (getPaddingLeft() + getPaddingRight());
                float height = mDrawRect.height() - (getPaddingTop()  + getPaddingBottom());
                mPath.addCircle(
                        mDrawRect.left + getPaddingLeft() + (width  / 2), 
                        mDrawRect.top  + getPaddingTop()  + (height / 2),
                        width / 2, 
                        Path.Direction.CW);
                mOutlinePaint.setColor(0xFFEF04D6);
            } else {
                mPath.addRect(new RectF(mDrawRect), Path.Direction.CW);
                mOutlinePaint.setColor(0xFFFF8A00);
            }
            canvas.clipPath(mPath, Region.Op.DIFFERENCE);
            canvas.drawRect(mViewDrawingRect, hasFocus() ? mFocusPaint : mNoFocusPaint);

            canvas.restore();
            canvas.drawPath(mPath, mOutlinePaint);

            if (mMode == ModifyMode.Grow) {
                if (mCircle) {
                    int width  = mResizeDrawableDiagonal.getIntrinsicWidth();
                    int height = mResizeDrawableDiagonal.getIntrinsicHeight();
                    
                    int d  = (int) Math.round(Math.cos(/*45deg*/Math.PI/4D) * (mDrawRect.width() / 2D));
                    int x  = mDrawRect.left + (mDrawRect.width() / 2) + d - width/2;
                    int y  = mDrawRect.top  + (mDrawRect.height() / 2) - d - height/2;
                    mResizeDrawableDiagonal.setBounds(x, y, x + mResizeDrawableDiagonal.getIntrinsicWidth(), y + mResizeDrawableDiagonal.getIntrinsicHeight());
                    mResizeDrawableDiagonal.draw(canvas);
                } else {
                    int left    = mDrawRect.left   + 1;
                    int right   = mDrawRect.right  + 1;
                    int top     = mDrawRect.top    + 4;
                    int bottom  = mDrawRect.bottom + 3;

                    int widthWidth   = mResizeDrawableWidth.getIntrinsicWidth() / 2;
                    int widthHeight  = mResizeDrawableWidth.getIntrinsicHeight()/ 2;
                    int heightHeight = mResizeDrawableHeight.getIntrinsicHeight()/ 2;
                    int heightWidth  = mResizeDrawableHeight.getIntrinsicWidth()/ 2;

                    int xMiddle = mDrawRect.left + ((mDrawRect.right  - mDrawRect.left) / 2);
                    int yMiddle = mDrawRect.top  + ((mDrawRect.bottom - mDrawRect.top ) / 2);

                    mResizeDrawableWidth.setBounds(left-widthWidth, yMiddle-widthHeight, left+widthWidth, yMiddle+widthHeight);
                    mResizeDrawableWidth.draw(canvas);

                    mResizeDrawableWidth.setBounds(right-widthWidth, yMiddle-widthHeight, right+widthWidth, yMiddle+widthHeight);
                    mResizeDrawableWidth.draw(canvas);

                    mResizeDrawableHeight.setBounds(xMiddle-heightWidth, top-heightHeight, xMiddle+heightWidth, top+heightHeight); 
                    mResizeDrawableHeight.draw(canvas);

                    mResizeDrawableHeight.setBounds(xMiddle-heightWidth, bottom-heightHeight, xMiddle+heightWidth, bottom+heightHeight); 
                    mResizeDrawableHeight.draw(canvas);
                }
            }
        }
    
    }
    
    float getPaddingTop() { return 0F; }
    float getPaddingBottom() { return 0F; }
    float getPaddingLeft() { return 0F; }
    float getPaddingRight() { return 0F; }

    public ModifyMode getMode() {
        return mMode;
    }
    
    public void setMode(ModifyMode mode)
    {
        if (mode != mMode) {
            mMode = mode;
            mContext.invalidate();
        }
    }
    
    public int getHit(float x, float y) {
        Rect r = computeLayout();
        final float hysteresis = 20F;
        int retval = GROW_NONE;
        
        if (mCircle) {
            float distX = x - r.centerX();
            float distY = y - r.centerY();
            int distanceFromCenter = (int) Math.sqrt(distX*distX + distY*distY);
            int radius  = (int) (mDrawRect.width() - getPaddingLeft()) / 2;
            int delta = distanceFromCenter - radius;
            if (Math.abs(delta) <= hysteresis) {
                if (Math.abs(distY) > Math.abs(distX)) {
                    if (distY < 0)
                        retval = GROW_TOP_EDGE;
                    else
                        retval = GROW_BOTTOM_EDGE;
                } else {
                    if (distX < 0)
                        retval = GROW_LEFT_EDGE;
                    else
                        retval = GROW_RIGHT_EDGE;
                }
            } else if (distanceFromCenter < radius) {
                retval = MOVE;
            } else {
                retval = GROW_NONE;
            }
//          Log.v(TAG, "radius: " + radius + "; touchRadius: " + distanceFromCenter + "; distX: " + distX + "; distY: " + distY + "; retval: " + retval); 
        } else {
            boolean verticalCheck = (y >= r.top - hysteresis) && (y < r.bottom + hysteresis);
            boolean horizCheck = (x >= r.left - hysteresis) && (x < r.right + hysteresis);

            if ((Math.abs(r.left - x)     < hysteresis)  &&  verticalCheck)
                retval |= GROW_LEFT_EDGE;
            if ((Math.abs(r.right - x)    < hysteresis)  &&  verticalCheck)
                retval |= GROW_RIGHT_EDGE;
            if ((Math.abs(r.top - y)      < hysteresis)  &&  horizCheck)
                retval |= GROW_TOP_EDGE;
            if ((Math.abs(r.bottom - y)   < hysteresis)  &&  horizCheck)
                retval |= GROW_BOTTOM_EDGE;

            if (retval == GROW_NONE && r.contains((int)x, (int)y))
                retval = MOVE;
        }
        return retval;
    }

    void handleMotion(int edge, float dx, float dy) {
        Rect r = computeLayout();
        if (edge == GROW_NONE) {
            return;
        } else if (edge == MOVE) {
            moveBy(dx * (mCropRect.width() / r.width()),
                   dy * (mCropRect.height() / r.height()));
        } else {
            if (((GROW_LEFT_EDGE | GROW_RIGHT_EDGE) & edge) == 0)
                dx = 0;

            if (((GROW_TOP_EDGE | GROW_BOTTOM_EDGE) & edge) == 0)
                dy = 0;

            float xDelta = dx * (mCropRect.width() / r.width());
            float yDelta = dy * (mCropRect.height() / r.height());
            growBy((((edge & GROW_LEFT_EDGE) != 0) ? -1 : 1) * xDelta,
                    (((edge & GROW_TOP_EDGE) != 0) ? -1 : 1) * yDelta);
                   
        }
//        Log.v(TAG, "ratio is now " + this.mCropRect.width() / this.mCropRect.height());
    }
    
    void moveBy(float dx, float dy) {
        Rect invalRect = new Rect(mDrawRect);

        mCropRect.offset(dx, dy);
        mCropRect.offset(
                Math.max(0, mImageRect.left - mCropRect.left), 
                Math.max(0, mImageRect.top  - mCropRect.top));

        mCropRect.offset(
                Math.min(0, mImageRect.right  - mCropRect.right), 
                Math.min(0, mImageRect.bottom - mCropRect.bottom));                

        mDrawRect = computeLayout();
        invalRect.union(mDrawRect);
        invalRect.inset(-10, -10);
        mContext.invalidate(invalRect);
    }
    
    private void shift(RectF r, float dx, float dy) {
        r.left   += dx;
        r.right  += dx;
        r.top    += dy;
        r.bottom += dy;
    }

    void growBy(float dx, float dy) {
//      Log.v(TAG, "growBy: " + dx + " " + dy + "; rect w/h is " + mCropRect.width() + " / " + mCropRect.height());
        if (mMaintainAspectRatio) {
            if (dx != 0) {
                dy = dx / mInitialAspectRatio;
            } else if (dy != 0) {
                dx = dy * mInitialAspectRatio;
            }
        }

        RectF r = new RectF(mCropRect);
        if (dx > 0F && r.width() + 2 * dx > mImageRect.width()) {
            float adjustment = (mImageRect.width() - r.width()) / 2F;
            dx = adjustment;
            if (mMaintainAspectRatio)
                dy = dx / mInitialAspectRatio;
        }
        if (dy > 0F && r.height() + 2 * dy > mImageRect.height()) {
            float adjustment = (mImageRect.height() - r.height()) / 2F;
            dy = adjustment;
            if (mMaintainAspectRatio)
                dx = dy * mInitialAspectRatio;
        }

        r.inset(-dx, -dy);
        
        float widthCap = 25F;
        if (r.width() < 25) {
            r.inset(-(25F-r.width())/2F, 0F);
        }
        float heightCap = mMaintainAspectRatio ? (widthCap / mInitialAspectRatio) : widthCap;
        if (r.height() < heightCap) {
            r.inset(0F, -(heightCap-r.height())/2F);
        }
        
        if (r.left < mImageRect.left) {
            shift(r, mImageRect.left - r.left, 0F);
        } else if (r.right > mImageRect.right) {
            shift(r, -(r.right - mImageRect.right), 0);
        }
        if (r.top < mImageRect.top) {
            shift(r, 0F, mImageRect.top - r.top);
        } else if (r.bottom > mImageRect.bottom) {
            shift(r, 0F, -(r.bottom - mImageRect.bottom));
        }
/*        
        RectF rCandidate = new RectF(r);
        r.intersect(mImageRect);
        if (mMaintainAspectRatio) {
            if (r.left != rCandidate.left) {
                Log.v(TAG, "bail 1");
                return;
            }
            if (r.right != rCandidate.right) {
                Log.v(TAG, "bail 2");
                return;
            }
            if (r.top != rCandidate.top) {
                Log.v(TAG, "bail 3");
                return;
            }
            if (r.bottom != rCandidate.bottom) {
                Log.v(TAG, "bail 4");
                return;
            }
        }
*/        
        mCropRect.set(r);
        mDrawRect = computeLayout();
        mContext.invalidate();
    }
    
    public Rect getCropRect() {
        return new Rect((int)mCropRect.left, (int)mCropRect.top, (int)mCropRect.right, (int)mCropRect.bottom);
    }
    
    private Rect computeLayout() {
        RectF r = new RectF(mCropRect.left, mCropRect.top, mCropRect.right, mCropRect.bottom);
        mMatrix.mapRect(r);
        return new Rect(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
    }
    
    public void invalidate() {
        mDrawRect = computeLayout();
    }
    
    public void setup(Matrix m, Rect imageRect, RectF cropRect, boolean circle, boolean maintainAspectRatio) {
        if (Config.LOGV) Log.v(TAG, "setup... " + imageRect + "; " + cropRect + "; maintain " + maintainAspectRatio + "; circle " + circle);
        if (circle)
            maintainAspectRatio = true;
        mMatrix = new Matrix(m);

        mCropRect = cropRect;
        mImageRect = new RectF(imageRect);
        mMaintainAspectRatio = maintainAspectRatio;
        mCircle = circle;

        mInitialAspectRatio = mCropRect.width() / mCropRect.height();
        mDrawRect = computeLayout();
        
        mFocusPaint.setARGB(125, 50, 50, 50);
        mNoFocusPaint.setARGB(125, 50, 50, 50);
        mOutlinePaint.setStrokeWidth(3F);
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setAntiAlias(true);

        mMode = ModifyMode.None;
        initHighlightView();
    }
    
    public void modify(int keyCode, long repeatCount)
    {
        float factor = Math.max(.01F, Math.min(.1F, repeatCount * .01F));  
        float widthUnits = factor * (float)mContext.getWidth();
        float heightUnits = widthUnits;

        switch (keyCode)
        {       
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (mMode == ModifyMode.Move)
                moveBy(-widthUnits, 0);
            else if (mMode == ModifyMode.Grow)
                growBy(-widthUnits, 0);
            break;
        
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if (mMode == ModifyMode.Move)
                moveBy(widthUnits, 0);
            else if (mMode == ModifyMode.Grow)
                growBy(widthUnits, 0);
            break;
    
        case KeyEvent.KEYCODE_DPAD_UP:
            if (mMode == ModifyMode.Move)
                moveBy(0, -heightUnits);
            else if (mMode == ModifyMode.Grow)
                growBy(0, -heightUnits);
            break;

        case KeyEvent.KEYCODE_DPAD_DOWN:
            if (mMode == ModifyMode.Move)
                moveBy(0, heightUnits);
            else if (mMode == ModifyMode.Grow)
                growBy(0, heightUnits);
            break;
        }
    }
    
    enum ModifyMode { None, Move,Grow };
    
    ModifyMode mMode = ModifyMode.None;
    
    Rect  mDrawRect;
    RectF mImageRect;
    RectF mCropRect;
    Matrix mMatrix;

    boolean mMaintainAspectRatio = false;
    float mInitialAspectRatio;
    boolean mCircle = false;
    
    Drawable mResizeDrawableWidth, mResizeDrawableHeight, mResizeDrawableDiagonal;

    Paint mFocusPaint = new Paint();
    Paint mNoFocusPaint = new Paint();
    Paint mOutlinePaint = new Paint();
}
