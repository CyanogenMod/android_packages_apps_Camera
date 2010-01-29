package com.android.camera.ui;

import android.graphics.Bitmap;

import javax.microedition.khronos.opengles.GL11;

public class RawTexture extends Texture {

    protected RawTexture(GL11 gl, int id,
            int width, int height, float widthf, float heightf) {
        super(gl, id, STATE_LOADED);
        super.setSize(width, height);
        super.setTexCoordSize(widthf, heightf);
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Bitmap getBitmap() {
        throw new UnsupportedOperationException();
    }

}
