
package com.android.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class FocusRectangle extends View {

    @SuppressWarnings("unused")
    private static final String TAG = "FocusRectangle";

    private int xActual, yActual;

    public FocusRectangle(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void setDrawable(int resid) {
        setBackgroundDrawable(getResources().getDrawable(resid));
    }

    public void showStart() {
        setDrawable(R.drawable.sk_auto_focusing);
    }

    public void showSuccess() {
        setDrawable(R.drawable.sk_auto_focused);
    }

    public void showFail() {
        setDrawable(R.drawable.sk_auto_focused);
    }

    public void clear() {
        setBackgroundDrawable(null);
    }

    public void setPosition(int x, int y) {
        if (x >= 0 && y >= 0) {
            xActual = x;
            yActual = y;
            int size = getWidth() / 2;
            this.layout(x - size, y - size, x + size, y + size);
        }
    }

    public int getTouchIndexX() {
        return xActual;
    }

    public int getTouchIndexY() {
        return yActual;
    }
}
