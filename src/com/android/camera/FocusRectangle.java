
package com.android.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class FocusRectangle extends View {

    @SuppressWarnings("unused")
    private static final String TAG = "FocusRectangle";

    private static final int SIZE = 50;

    private int xActual, yActual;

    public FocusRectangle(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void setDrawable(int resid) {
        setBackgroundDrawable(getResources().getDrawable(resid));
    }

    public void showStart() {
        setDrawable(R.drawable.focus_focusing);
    }

    public void showSuccess() {
        setDrawable(R.drawable.focus_focused);
    }

    public void showFail() {
        setDrawable(R.drawable.focus_focus_failed);
    }

    public void clear() {
        setBackgroundDrawable(null);
    }

    public void setPosition(int x, int y) {
        if (x >= 0 && y >= 0) {
            xActual = x;
            yActual = y;
            this.layout(x - SIZE, y - SIZE, x + SIZE, y + SIZE);
        }
    }

    public int getTouchIndexX() {
        return xActual;
    }

    public int getTouchIndexY() {
        return yActual;
    }
}
