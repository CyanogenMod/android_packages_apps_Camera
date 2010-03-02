package com.android.camera.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.animation.Transformation;

import com.android.camera.R;

import javax.microedition.khronos.opengles.GL11;

public class GLOptionItem extends GLView {
    private static final int FONT_COLOR = Color.WHITE;
    private static final int MINIMAL_WIDTH = 180;
    private static final int MINIMAL_HEIGHT = 48;
    private static final int NO_ICON_LEADING_SPACE = 15;
    private static final int TEXT_LEFT_PADDING = 9;
    private static final int TEXT_RIGHT_PADDING = 15;
    private static final float ENABLED_ALPHA = 1f;
    private static final float DISABLED_ALPHA = 0.3f;

    private static ResourceTexture sCheckOn;
    private static ResourceTexture sCheckOff;

    private final ResourceTexture mIcon;
    private final StringTexture mText;
    private boolean mEnabled = true;

    private ResourceTexture mCheckBox;

    private static void initCheckIcons(Context context) {
        if (sCheckOn != null) return;
        sCheckOn = new ResourceTexture(context, R.drawable.ic_menuselect_on);
        sCheckOff = new ResourceTexture(context, R.drawable.ic_menuselect_off);
    }

    public GLOptionItem(Context context, int iconId, String title) {
        initCheckIcons(context);
        mIcon = iconId == 0 ? null : new ResourceTexture(context, iconId);
        mText = StringTexture.newInstance(title, 26, FONT_COLOR);
        mCheckBox = sCheckOff;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = (mIcon == null ? NO_ICON_LEADING_SPACE : mIcon.getWidth())
                + mText.getWidth() + mCheckBox.getWidth()
                + TEXT_RIGHT_PADDING + TEXT_LEFT_PADDING;
        int height = Math.max(Math.max(mIcon == null ? 0 : mIcon.getHeight(),
                mText.getHeight()), mCheckBox.getHeight());

        width = Math.max(MINIMAL_WIDTH, width);
        height = Math.max(MINIMAL_HEIGHT, height);

        new MeasureHelper(this)
                .setPreferedContentSize(width, height)
                .measure(widthSpec, heightSpec);
    }

    @Override
    protected void render(GLRootView root, GL11 gl) {
        Rect p = mPaddings;

        int width = getWidth() - p.left - p.right;
        int height = getHeight() - p.top - p.bottom;

        int xoffset = p.left;

        Transformation trans = root.getTransformation();
        float oldAlpha = trans.getAlpha();
        trans.setAlpha(oldAlpha * (mEnabled ? ENABLED_ALPHA : DISABLED_ALPHA));

        ResourceTexture icon = mIcon;
        if (icon != null) {
            if (icon.bind(root, gl)) {
                icon.draw(root, xoffset,
                        p.top + (height - icon.getHeight()) / 2);
            }
            xoffset += icon.getWidth();
        } else {
            xoffset += NO_ICON_LEADING_SPACE;
        }

        StringTexture title = mText;
        xoffset += TEXT_LEFT_PADDING;
        if (title.bind(root, gl)) {
            int yoffset = p.top + (height - title.getHeight()) / 2;
            //TODO: cut the text if it is too long
            title.draw(root, xoffset, yoffset);
        }

        ResourceTexture checkbox = mCheckBox;
        if (checkbox.bind(root, gl)) {
            int yoffset = p.top + (height - checkbox.getHeight()) / 2;
            checkbox.draw(root, width - checkbox.getWidth(), yoffset);
        }
        trans.setAlpha(oldAlpha);
    }

    public void setChecked(boolean checked) {
        mCheckBox = checked ? sCheckOn : sCheckOff;
        invalidate();
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        invalidate();
    }
}
