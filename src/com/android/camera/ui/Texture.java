/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.ui;

import com.android.camera.Util;

import android.graphics.Bitmap;
import android.opengl.GLUtils;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

abstract class Texture {

    @SuppressWarnings("unused")
    private static final String TAG = "Texture";
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

    protected abstract Bitmap getBitmap();

    protected abstract void freeBitmap(Bitmap bitmap);

    public void deleteFromGL() {
        if (mState == STATE_LOADED) {
            mGL.glDeleteTextures(1, new int[]{mId}, 0);
        }
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
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] cropRect = {0,  height, width, -height};

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

                int widthExt = Util.nextPowerOf2(width);
                int heightExt = Util.nextPowerOf2(height);
                int format = GLUtils.getInternalFormat(bitmap);
                int type = GLUtils.getType(bitmap);

                mTextureWidth = widthExt;
                mTextureHeight = heightExt;
                gl.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format,
                        widthExt, heightExt, 0, format, type, null);
                GLUtils.texSubImage2D(
                        GL11.GL_TEXTURE_2D, 0, 0, 0, bitmap, format, type);
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
        root.drawTexture(this, x, y, mWidth, mHeight);
    }

    public void draw(GLRootView root, int x, int y, int w, int h, float alpha) {
        root.drawTexture(this, x, y, w, h, alpha);
    }

    protected boolean bind(GLRootView root, GL11 gl) {
        if (mState == Texture.STATE_UNLOADED || mGL != gl) {
            mState = Texture.STATE_UNLOADED;
            try {
                uploadToGL(gl);
            } catch (GLOutOfMemoryException e) {
                root.handleLowMemory();
                return false;
            }
        } else {
            gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
        }
        return true;
    }

    public void getTextureCoords(float coord[], int offset) {
        // Shrinks the texture coordinates inner by 0.5 pixel so that GL won't
        // sample on garbage data.
        float left = 0.5f / mTextureWidth;
        float right = (mWidth - 0.5f) / mTextureWidth;
        float top = 0.5f / mTextureHeight;
        float bottom = (mHeight - 0.5f) / mTextureHeight;

        coord[offset++] = left;
        coord[offset++] = top;
        coord[offset++] = right;
        coord[offset++] = top;
        coord[offset++] = left;
        coord[offset++] = bottom;
        coord[offset++] = right;
        coord[offset] = bottom;
    }
}
