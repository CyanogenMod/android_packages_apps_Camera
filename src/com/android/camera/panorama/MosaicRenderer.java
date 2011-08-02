/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.camera.panorama;

/**
 * The Java interface to JNI calls regarding mosaic preview rendering.
 *
 */
public class MosaicRenderer
{
     static
     {
         System.loadLibrary("jni_mosaic");
     }

     /**
      * Function to be called in onSurfaceCreated() to initialize
      * the GL context, load and link the shaders and create the
      * program.
      */
     public static native void init();

     /**
      * Pass the drawing surface's width and height to initialize the
      * renderer viewports and FBO dimensions.
      *
      * @param width width of the drawing surface in pixels.
      * @param height height of the drawing surface in pixels.
      */
     public static native void reset(int width, int height);

     /**
      * Function to be called in onDrawFrame() to update the screen with
      * the new frame data.
      */
     public static native void step();

     /**
      * Call this function when a new low-res frame has been processed by
      * the mosaicing library. This will tell the renderer library to
      * update its texture and warping transformation. Any calls to step()
      * after this call will use the new image frame and transformation data.
      */
     public static native void ready();

     /**
      * This function allows toggling between showing the input image data
      * (without applying any warp) and the warped image data. This is more
      * for debugging purposes to see if the image data is being updated
      * correctly or not.
      */
     public static native void togglewarping();
}
