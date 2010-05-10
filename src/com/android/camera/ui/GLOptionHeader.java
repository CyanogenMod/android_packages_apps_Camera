package com.android.camera.ui;

import static com.android.camera.ui.GLRootView.dpToPixel;
import android.content.Context;
import android.graphics.Rect;

import com.android.camera.R;

import javax.microedition.khronos.opengles.GL11;

class GLOptionHeader extends GLView {
    private static final int FONT_COLOR = 0xFF979797;
    private static final float FONT_SIZE = 12;
    private static final int HORIZONTAL_PADDINGS = 4;
    private static final int VERTICAL_PADDINGS = 2;

    private static int sHorizontalPaddings = -1;
    private static int sVerticalPaddings;

    private final StringTexture mTitle;
    private NinePatchTexture mBackground;

    private static void initializeStaticVariables(Context context) {
        if (sHorizontalPaddings >= 0) return;
        sHorizontalPaddings = dpToPixel(context, HORIZONTAL_PADDINGS);
        sVerticalPaddings = dpToPixel(context, VERTICAL_PADDINGS);
    }

    public GLOptionHeader(Context context, String title) {
        initializeStaticVariables(context);

        float fontSize = GLRootView.dpToPixel(context, FONT_SIZE);
        mTitle = StringTexture.newInstance(title, fontSize, FONT_COLOR);
        setBackground(new NinePatchTexture(
                context, R.drawable.optionheader_background));
        setPaddings(sHorizontalPaddings,
                sVerticalPaddings, sHorizontalPaddings, sVerticalPaddings);
    }

    public void setBackground(NinePatchTexture background) {
        if (mBackground == background) return;
        mBackground = background;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        new MeasureHelper(this)
                .setPreferredContentSize(mTitle.getWidth(), mTitle.getHeight())
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        if (mBackground != null) {
            mBackground.setSize(getWidth(), getHeight());
            mBackground.draw(root, 0, 0);
        }
        Rect p = mPaddings;
        mTitle.draw(root, p.left, p.top);
    }
}
