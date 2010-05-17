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


import javax.microedition.khronos.opengles.GL11;

class RawTexture extends BasicTexture {

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
    protected boolean bind(GLRootView glRootView, GL11 gl) {
        if (mGL == gl) {
            gl.glBindTexture(GL11.GL_TEXTURE_2D, getId());
            return true;
        }
        return false;
    }

    public void drawBack(GLRootView root, int x, int y, int w, int h) {
        root.drawTexture(this, x, y, w, h, 1f);
    }
}
