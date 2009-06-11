package com.android.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;


public class Switcher extends ImageView {

    private boolean mSwitch = false;

    public Switcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSwitch(boolean onOff) {
        this.mSwitch = onOff;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        Drawable drawable = getDrawable();
        int drawableHeight = drawable.getIntrinsicHeight();
        int drawableWidth = drawable.getIntrinsicWidth();

        if (drawable == null) {
            return; // couldn't resolve the URI
        }

        if (drawableWidth == 0 || drawableHeight == 0) {
            return;     // nothing to draw (empty bounds)
        }

        int offsetTop = mSwitch
                ? getHeight() - drawableHeight - mPaddingBottom
                : mPaddingTop;
        int offsetLeft = (getWidth()
                - drawableWidth - mPaddingLeft - mPaddingRight) / 2;
        int saveCount = canvas.getSaveCount();
        canvas.save();
        canvas.translate(offsetLeft, offsetTop);
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

}
