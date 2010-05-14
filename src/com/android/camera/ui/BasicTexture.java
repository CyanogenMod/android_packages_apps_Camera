package com.android.camera.ui;

import javax.microedition.khronos.opengles.GL11;

abstract class BasicTexture implements Texture {

    protected static final int UNSPECIFIED = -1;

    public static final int STATE_UNLOADED = 0;
    public static final int STATE_LOADED = 1;
    public static final int STATE_ERROR = -1;

    protected GL11 mGL;

    protected int mId;
    protected int mState;

    protected int mWidth = UNSPECIFIED;
    protected int mHeight = UNSPECIFIED;

    protected int mTextureWidth;
    protected int mTextureHeight;

    protected BasicTexture(GL11 gl, int id, int state) {
        mGL = gl;
        mId = id;
        mState = state;
    }

    protected BasicTexture() {
        this(null, 0, STATE_UNLOADED);
    }

    protected void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * Sets the size of the texture. Due to the limit of OpenGL, the texture
     * size must be of power of 2, the size of the content may not be the size
     * of the texture.
     */
    protected void setTextureSize(int width, int height) {
        mTextureWidth = width;
        mTextureHeight = height;
    }

    public int getId() {
        return mId;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public void deleteFromGL() {
        if (mState == STATE_LOADED) {
            mGL.glDeleteTextures(1, new int[]{mId}, 0);
        }
        mState = STATE_UNLOADED;
    }

    public void draw(GLRootView root, int x, int y) {
        root.drawTexture(this, x, y, mWidth, mHeight);
    }

    public void draw(GLRootView root, int x, int y, int w, int h) {
        root.drawTexture(this, x, y, w, h);
    }

    abstract protected boolean bind(GLRootView root, GL11 gl);
}
