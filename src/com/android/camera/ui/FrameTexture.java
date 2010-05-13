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

import android.graphics.Rect;

import javax.microedition.khronos.opengles.GL11;

public abstract class FrameTexture extends Texture {

    public FrameTexture() {
    }

    public FrameTexture(GL11 gl, int id, int state) {
        super(gl, id, state);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
    }

    abstract public Rect getPaddings();
}
