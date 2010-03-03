package com.android.camera.ui;

import android.graphics.Bitmap;

import javax.microedition.khronos.opengles.GL11;

public class RawTexture extends Texture {

    private RawTexture(GL11 gl, int id) {
        super(gl, id, STATE_LOADED);
    }

    public GL11 getBoundGL() {
        return mGL;
    }

    public static RawTexture newInstance(GL11 gl) {
        int[] textureId = new int[1];
        gl.glGenTextures(1, textureId, 0);
        int glError = gl.glGetError();
        if (glError != GL11.GL_NO_ERROR) {
            throw new RuntimeException("GL_ERROR: " + glError);
        }
        return new RawTexture(gl, textureId[0]);
    }

    @Override
    protected void freeBitmap(Bitmap bitmap) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Bitmap getBitmap() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean bind(GLRootView glRootView, GL11 gl) {
        if (mGL == gl) {
            gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
            return true;
        }
        return false;
    }
}
