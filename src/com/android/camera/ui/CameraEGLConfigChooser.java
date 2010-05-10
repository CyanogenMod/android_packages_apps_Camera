/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.opengl.GLSurfaceView.EGLConfigChooser;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/*
 * The code is copied/adapted from
 * <code>android.opengl.GLSurfaceView.BaseConfigChooser</code>. Here we try to
 * choose a configuration that support RGBA_8888 format and if possible,
 * with stencil buffer, but is not required.
 */
class CameraEGLConfigChooser implements EGLConfigChooser {

    private static final int COLOR_BITS = 8;

    private int mStencilBits;

    private final int mConfigSpec[] = new int[] {
            EGL10.EGL_RED_SIZE, COLOR_BITS,
            EGL10.EGL_GREEN_SIZE, COLOR_BITS,
            EGL10.EGL_BLUE_SIZE, COLOR_BITS,
            EGL10.EGL_ALPHA_SIZE, COLOR_BITS,
            EGL10.EGL_NONE
    };

    public int getStencilBits() {
        return mStencilBits;
    }

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        int[] numConfig = new int[1];
        if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, numConfig)) {
            throw new RuntimeException("eglChooseConfig failed");
        }

        if (numConfig[0] <= 0) {
            throw new RuntimeException("No configs match configSpec");
        }

        EGLConfig[] configs = new EGLConfig[numConfig[0]];
        if (!egl.eglChooseConfig(display,
                mConfigSpec, configs, configs.length, numConfig)) {
            throw new RuntimeException();
        }

        return chooseConfig(egl, display, configs);
    }

    private EGLConfig chooseConfig(
            EGL10 egl, EGLDisplay display, EGLConfig configs[]) {

        EGLConfig result = null;
        int minStencil = Integer.MAX_VALUE;
        int value[] = new int[1];

        // Because we need only one bit of stencil, try to choose a config that
        // has stencil support but with smallest number of stencil bits. If
        // none is found, choose any one.
        for (int i = 0, n = configs.length; i < n; ++i) {
            if (egl.eglGetConfigAttrib(
                    display, configs[i], EGL10.EGL_STENCIL_SIZE, value)) {
                if (value[0] == 0) continue;
                if (value[0] < minStencil) {
                    minStencil = value[0];
                    result = configs[i];
                }
            } else {
                throw new RuntimeException(
                        "eglGetConfigAttrib error: " + egl.eglGetError());
            }
        }
        if (result == null) result = configs[0];
        egl.eglGetConfigAttrib(
                display, result, EGL10.EGL_STENCIL_SIZE, value);
        mStencilBits = value[0];
        return result;
    }
}
