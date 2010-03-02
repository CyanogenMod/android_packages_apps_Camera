package com.android.camera.ui;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.opengl.GLUtils;

import com.android.camera.Util;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

public abstract class Texture {

    @SuppressWarnings("unused")
    private static final String TAG = "Texture";
    protected static final int UNSPECIFIED = -1;

    public static final int STATE_UNLOADED = 0;
    public static final int STATE_LOADED = 1;
    public static final int STATE_ERROR = -1;

    private GL11 mGL;

    private int mId;
    private int mState;

    protected int mWidth = UNSPECIFIED;
    protected int mHeight = UNSPECIFIED;

    private float mTexCoordWidth = 1.0f;
    private float mTexCoordHeight = 1.0f;

    protected Texture(GL11 gl, int id, int state) {
        mGL = gl;
        mId = id;
        mState = state;
    }

    protected Texture() {
        this(null, 0, STATE_UNLOADED);
    }

    protected void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    protected void setTexCoordSize(float width, float height) {
        mTexCoordWidth = width;
        mTexCoordHeight = height;
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

    protected abstract Bitmap getBitmap();

    protected abstract void freeBitmap(Bitmap bitmap);

    public void deleteFromGL(GL11 gl) {
        gl.glDeleteTextures(1, new int[]{mId}, 0);
        mState = STATE_UNLOADED;
    }

    private void uploadToGL(GL11 gl) throws GLOutOfMemoryException {
        Bitmap bitmap = getBitmap();
        int glError = GL11.GL_NO_ERROR;
        if (bitmap != null) {
            int[] textureId = new int[1];
            try {
                // Define a vertically flipped crop rectangle for
                // OES_draw_texture.
                int[] cropRect = {0,  mHeight, mWidth, - mHeight};

                // Upload the bitmap to a new texture.
                gl.glGenTextures(1, textureId, 0);
                gl.glBindTexture(GL11.GL_TEXTURE_2D, textureId[0]);
                gl.glTexParameteriv(GL11.GL_TEXTURE_2D,
                        GL11Ext.GL_TEXTURE_CROP_RECT_OES, cropRect, 0);
                gl.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
                gl.glTexParameterf(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                gl.glTexParameterf(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GLUtils.texImage2D(GL11.GL_TEXTURE_2D, 0, bitmap, 0);
            } finally {
                freeBitmap(bitmap);
            }
            if (glError == GL11.GL_OUT_OF_MEMORY) {
                throw new GLOutOfMemoryException();
            }
            if (glError != GL11.GL_NO_ERROR) {
                mId = 0;
                mState = STATE_UNLOADED;
                throw new RuntimeException(
                        "Texture upload fail, glError " + glError);
            } else {
                // Update texture state.
                mGL = gl;
                mId = textureId[0];
                mState = Texture.STATE_LOADED;
            }
        } else {
            mState = STATE_ERROR;
            throw new RuntimeException("Texture load fail, no bitmap");
        }
    }

    public void draw(GLRootView root, int x, int y) {
        draw(root, x, y, getWidth(), getHeight());
    }

    public void draw(GLRootView root,
            int x, int y, int width, int height) {
        root.draw2D(x, y, width, height);
    }

    protected boolean bind(GLRootView glRootView, GL11 gl) {
        if (mState == Texture.STATE_UNLOADED || mGL != gl) {
            mState = Texture.STATE_UNLOADED;
            try {
                uploadToGL(gl);
            } catch (GLOutOfMemoryException e) {
                glRootView.handleLowMemory();
                return false;
            }
        } else {
            gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
        }

        float w = mTexCoordWidth;
        float h = mTexCoordHeight;
        glRootView.setTexCoords(0, 0, w, 0, 0, h, w, h);
        return true;
    }

    protected Bitmap generateGLCompatibleBitmap(int width, int height) {
        int newWidth = Util.nextPowerOf2(width);
        int newHeight = Util.nextPowerOf2(height);
        mTexCoordWidth = (float) width / newWidth;
        mTexCoordHeight = (float) height / newHeight;
        return Bitmap.createBitmap(newWidth, newHeight, Config.ARGB_8888);
    }
}
