/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.camera;

import java.io.IOException;
import java.io.ByteArrayOutputStream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.renderscript.RenderScript;
import android.util.Log;

public class HdrSoftwareProcessor {
    public final static String TAG = "SW_HDR";

    private static class RGB {
        int r, g, b;

        public RGB(int color) {
            set(color);
        }

        public void set(int color) {
            r = Color.red(color);
            g = Color.green(color);
            b = Color.blue(color);
        }

        public void set(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    };

    // Method of average pixel computation (software)
    public final static int METHOD_AVERAGE_SW = 1;

    // Method of reinhard computation (software)
    public final static int METHOD_REINHARD_SW = 2;

    // Method of average pixel computation (RenderScript)
    public final static int METHOD_AVERAGE_RS = 3;

    // Method of reinhard computation (RenderScript)
    public final static int METHOD_REINHARD_RS = 4;

    private Bitmap[] mSourceBitmap;
    private Bitmap mOutputBitmap;
    private int mImageWidth;
    private int mImageHeight;
    private int mMethod;

    /**
     * Default constructor
     */
    public HdrSoftwareProcessor() {
        mMethod = METHOD_AVERAGE_RS;
    }

    /**
     * Prepare the processor with the provided source images paths
     * @param sourceImages Source images at different exposures
     */
    public void prepare(Context ctx, Uri[] sourceImages) throws IOException {
        // We load every source image in a Bitmap array to be able to read them later
        mSourceBitmap = new Bitmap[sourceImages.length];

        for (int i = 0; i < sourceImages.length; i++) {
            mSourceBitmap[i] = Media.getBitmap(ctx.getContentResolver(), sourceImages[i]);
        }

        Log.d(TAG, "Done prepare()");
    }

    /**
     * Set the computation method
     * @param method See METHOD_*
     */
    public void setMethod(int method) {
        mMethod = method;
    }

    /**
     * Compute the final image from the source bitmaps using the stored method,
     * and output a final JPEG file at the specified output path
     */
    public byte[] computeHDR(Context ctx) {
        if (mMethod == METHOD_AVERAGE_SW || mMethod == METHOD_REINHARD_SW) {
            computeHDRSoftware();
        } else {
            computeHDRRenderScript(ctx);
        }

        // Save image to memory - will be later fed into ImageSaver
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mOutputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return out.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return null;
    }

    /**
     * Computes the HDR image using renderscript
     */
    public void computeHDRRenderScript(Context ctx) {
        Log.d(TAG, "Starting HDR render (RS)");
        RenderScript rsRenderer = RenderScript.create(ctx);
        HdrSoftwareRS rsHost = new HdrSoftwareRS(rsRenderer, ctx.getResources(), R.raw.hdrsoftware);

        // set the input
        rsHost.setBitmapInput(mSourceBitmap[0], mSourceBitmap[1], mSourceBitmap[2]);

        // process and grab output
        rsHost.process();
        mOutputBitmap = rsHost.getOutput();
    }

    /**
     * Computes the HDR image using software/java
     */
    private void computeHDRSoftware() {
        final int nb_src_images = mSourceBitmap.length;

        // We prepare output variables. We assume all images are of the same size,
        // so we take the first one as reference.
        mImageWidth = mSourceBitmap[0].getWidth();
        mImageHeight = mSourceBitmap[0].getHeight();
        mOutputBitmap = Bitmap.createBitmap(mImageWidth, mImageHeight, mSourceBitmap[0].getConfig());

        RGB src_col[] = new RGB[nb_src_images];
        for (int i = 0; i < nb_src_images; i++) {
            src_col[i] = new RGB(0);
        }

        // It is faster to blit a pixel array to the final canvas, than
        // to paint on it pixel-per-pixel
        int pixels[] = new int[mImageWidth * mImageHeight];
        int curr_pixel = 0;
        int last_progress = -1;
        RGB out_col = new RGB(0);

        for (int y = 0; y < mImageHeight; y++) {
            /// DEBUG == DISPLAY PROGRESS IN LOGCAT
            int progress = y * 100 / mImageHeight;
            if (progress % 10 == 0 && last_progress != progress) {
                Log.e(TAG, "Progress: " + progress + "%");
                last_progress = progress;
            }

            for (int x = 0; x < mImageWidth; x++) {
                // Read input pixel of every source image
                for (int i = 0; i < nb_src_images; i++) {
                    src_col[i].set(mSourceBitmap[i].getPixel(x,y));
                }

                double sum_r = 0, sum_g = 0, sum_b = 0;
                for (int i = 0; i < nb_src_images; i++) {
                    sum_r += src_col[i].r;
                    sum_g += src_col[i].g;
                    sum_b += src_col[i].b;
                }

                if (mMethod == METHOD_AVERAGE_SW) {
                    computePixelAverage(sum_r, sum_g, sum_b, out_col);
                }
                else if (mMethod == METHOD_REINHARD_RS) {
                    computePixelReinhard(sum_r, sum_g, sum_b, out_col);
                }
                else {
                    Log.e(TAG, "Computation method invalid: " + mMethod + "! HDR aborted");
                    return;
                }

                pixels[curr_pixel] = Color.argb(255, out_col.r, out_col.g, out_col.b);
                curr_pixel++;
            }
        }

        Log.d(TAG, "Done rendering image to memory");

        // We have our final image. We blit it to a Bitmap via a Canvas.
        Canvas can = new Canvas();
        can.setBitmap(mOutputBitmap);
        can.drawBitmap(pixels, 0, mImageWidth, 0, 0, mImageWidth, mImageHeight, false, null);
    }

    /**
     * Compute the output pixel based on the average of the color of all pixels
     * @param r Sum of R component
     * @param g Sum of G component
     * @param b Sum of B component
     * @param out RGB Output structure
     */
    public void computePixelAverage(double r, double g, double b, RGB out) {
        final double nb_src_images = mSourceBitmap.length;
        out.set((int)(r / nb_src_images), (int)(g / nb_src_images), (int)(b / nb_src_images));
    }

    /**
     * Compute the output pixel based on Reinhard's algorithm for tone mapping
     * @param r Sum of R component
     * @param g Sum of G component
     * @param b Sum of B component
     * @param out RGB Output structure
     */
    public void computePixelReinhard(double r, double g, double b, RGB out) {
        // This is highly experimental and should be tuned :)
        r /= 255.0;
        g /= 255.0;
        b /= 255.0;

        // Compute pixel luminance
        double sum_L = 0.2126 * r + 0.7152 * g + 0.0722 * b;

        // The original (classic) reinhard formula uses (sum_L / (1.0 + sum_L)). This
        // has been proved to lost a lost of black details, hence the formula below.
        // I'm not sure however this applies fine to our sum of component.
        double nL = (sum_L * ( 1.0 + sum_L / 16.0 )) / ( 1.0 + sum_L );

        double scale = nL / sum_L;
        out.set((int)clampColor(r * scale * 255.0), (int)clampColor(g * scale * 255.0), (int)clampColor(b * scale * 255.0));
    }

    /**
     * Clamp a value between 0 and 255
     * @param val Value
     */
    private double clampColor(double val) {
        if (val > 255) val = 255;
        else if (val < 0) val = 0;
        return val;
    }
}
