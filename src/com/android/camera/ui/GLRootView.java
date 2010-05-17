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

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Stack;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

// The root component of all <code>GLView</code>s. The rendering is done in GL
// thread while the event handling is done in the main thread.  To synchronize
// the two threads, the entry points of this package need to synchronize on the
// <code>GLRootView</code> instance unless it can be proved that the rendering
// thread won't access the same thing as the method. The entry points include:
// (1) The public methods of HeadUpDisplay
// (2) The public methods of CameraHeadUpDisplay
// (3) The overridden methods in GLRootView.
public class GLRootView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    private static final String TAG = "GLRootView";

    private final boolean ENABLE_FPS_TEST = false;
    private int mFrameCount = 0;
    private long mFrameCountingStart = 0;

    // We need 16 vertices for a normal nine-patch image (the 4x4 vertices)
    private static final int VERTEX_BUFFER_SIZE = 16 * 2;

    // We need 22 indices for a normal nine-patch image
    private static final int INDEX_BUFFER_SIZE = 22;

    private static final int FLAG_INITIALIZED = 1;
    private static final int FLAG_NEED_LAYOUT = 2;

    private static boolean mTexture2DEnabled;

    private static float sPixelDensity = -1f;

    private GL11 mGL;
    private GLView mContentView;
    private DisplayMetrics mDisplayMetrics;

    private final ArrayList<Animation> mAnimations = new ArrayList<Animation>();

    private final Stack<Transformation> mFreeTransform =
            new Stack<Transformation>();

    private final Transformation mTransformation = new Transformation();
    private final Stack<Transformation> mTransformStack =
            new Stack<Transformation>();

    private float mLastAlpha = mTransformation.getAlpha();

    private final float mMatrixValues[] = new float[16];

    private final float mUvBuffer[] = new float[VERTEX_BUFFER_SIZE];
    private final float mXyBuffer[] = new float[VERTEX_BUFFER_SIZE];
    private final byte mIndexBuffer[] = new byte[INDEX_BUFFER_SIZE];

    private int mNinePatchX[] = new int[4];
    private int mNinePatchY[] = new int[4];
    private float mNinePatchU[] = new float[4];
    private float mNinePatchV[] = new float[4];

    private ByteBuffer mXyPointer;
    private ByteBuffer mUvPointer;
    private ByteBuffer mIndexPointer;

    private int mFlags = FLAG_NEED_LAYOUT;
    private long mAnimationTime;

    private CameraEGLConfigChooser mEglConfigChooser = new CameraEGLConfigChooser();

    public GLRootView(Context context) {
        this(context, null);
    }

    public GLRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    void registerLaunchedAnimation(Animation animation) {
        // Register the newly launched animation so that we can set the start
        // time more precisely. (Usually, it takes much longer for the first
        // rendering, so we set the animation start time as the time we
        // complete rendering)
        mAnimations.add(animation);
    }

    public long currentAnimationTimeMillis() {
        return mAnimationTime;
    }

    public synchronized static float dpToPixel(Context context, float dp) {
        if (sPixelDensity < 0) {
            DisplayMetrics metrics = new DisplayMetrics();
            ((Activity) context).getWindowManager()
                    .getDefaultDisplay().getMetrics(metrics);
            sPixelDensity =  metrics.density;
        }
        return sPixelDensity * dp;
    }

    public static int dpToPixel(Context context, int dp) {
        return (int)(dpToPixel(context, (float) dp) + .5f);
    }

    public Transformation obtainTransformation() {
        if (!mFreeTransform.isEmpty()) {
            Transformation t = mFreeTransform.pop();
            t.clear();
            return t;
        }
        return new Transformation();
    }

    public void freeTransformation(Transformation freeTransformation) {
        mFreeTransform.push(freeTransformation);
    }

    public Transformation getTransformation() {
        return mTransformation;
    }

    public Transformation pushTransform() {
        Transformation trans = obtainTransformation();
        trans.set(mTransformation);
        mTransformStack.push(trans);
        return mTransformation;
    }

    public void popTransform() {
        Transformation trans = mTransformStack.pop();
        mTransformation.set(trans);
        freeTransformation(trans);
    }

    public CameraEGLConfigChooser getEGLConfigChooser() {
        return mEglConfigChooser;
    }

    private static ByteBuffer allocateDirectNativeOrderBuffer(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private void initialize() {
        mFlags |= FLAG_INITIALIZED;
        setEGLConfigChooser(mEglConfigChooser);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);

        setRenderer(this);

        int size = VERTEX_BUFFER_SIZE * Float.SIZE / Byte.SIZE;
        mXyPointer = allocateDirectNativeOrderBuffer(size);
        mUvPointer = allocateDirectNativeOrderBuffer(size);
        mIndexPointer = allocateDirectNativeOrderBuffer(INDEX_BUFFER_SIZE);
    }

    public void setContentPane(GLView content) {
        mContentView = content;
        content.onAttachToRoot(this);

        // no parent for the content pane
        content.onAddToParent(null);
        requestLayoutContentPane();
    }

    public GLView getContentPane() {
        return mContentView;
    }

    void handleLowMemory() {
        //TODO: delete texture from GL
    }

    public synchronized void requestLayoutContentPane() {
        if (mContentView == null || (mFlags & FLAG_NEED_LAYOUT) != 0) return;

        // "View" system will invoke onLayout() for initialization(bug ?), we
        // have to ignore it since the GLThread is not ready yet.
        if ((mFlags & FLAG_INITIALIZED) == 0) return;

        mFlags |= FLAG_NEED_LAYOUT;
        requestRender();
    }

    private synchronized void layoutContentPane() {
        mFlags &= ~FLAG_NEED_LAYOUT;
        int width = getWidth();
        int height = getHeight();
        Log.v(TAG, "layout content pane " + width + "x" + height);
        if (mContentView != null && width != 0 && height != 0) {
            mContentView.layout(0, 0, width, height);
        }
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        if (changed) requestLayoutContentPane();
    }

    /**
     * Called when the context is created, possibly after automatic destruction.
     */
    // This is a GLSurfaceView.Renderer callback
    public void onSurfaceCreated(GL10 gl1, EGLConfig config) {
        GL11 gl = (GL11) gl1;
        if (mGL != null) {
            // The GL Object has changed
            Log.i(TAG, "GLObject has changed from " + mGL + " to " + gl);
        }
        mGL = gl;

        if (!ENABLE_FPS_TEST) {
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        } else {
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        // Disable unused state
        gl.glDisable(GL11.GL_LIGHTING);

        // Enable used features
        gl.glEnable(GL11.GL_BLEND);
        gl.glEnable(GL11.GL_SCISSOR_TEST);
        gl.glEnable(GL11.GL_STENCIL_TEST);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glEnable(GL11.GL_TEXTURE_2D);
        mTexture2DEnabled = true;

        gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

        // Set the background color
        gl.glClearColor(0f, 0f, 0f, 0f);
        gl.glClearStencil(0);

        gl.glVertexPointer(2, GL11.GL_FLOAT, 0, mXyPointer);
        gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, mUvPointer);

    }

    /**
     * Called when the OpenGL surface is recreated without destroying the
     * context.
     */
    // This is a GLSurfaceView.Renderer callback
    public void onSurfaceChanged(GL10 gl1, int width, int height) {
        Log.v(TAG, "onSurfaceChanged: " + width + "x" + height
                + ", gl10: " + gl1.toString());
        GL11 gl = (GL11) gl1;
        mGL = gl;
        gl.glViewport(0, 0, width, height);

        gl.glMatrixMode(GL11.GL_PROJECTION);
        gl.glLoadIdentity();

        GLU.gluOrtho2D(gl, 0, width, 0, height);
        Matrix matrix = mTransformation.getMatrix();
        matrix.reset();
        matrix.preTranslate(0, getHeight());
        matrix.preScale(1, -1);
    }

    private void setAlphaValue(float alpha) {
        if (mLastAlpha == alpha) return;

        GL11 gl = mGL;
        mLastAlpha = alpha;
        if (alpha >= 0.95f) {
            gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                    GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
        } else {
            gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                    GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            gl.glColor4f(alpha, alpha, alpha, alpha);
        }
    }

    public void drawRect(int x, int y, int width, int height) {
        float matrix[] = mMatrixValues;
        mTransformation.getMatrix().getValues(matrix);
        drawRect(x, y, width, height, matrix);
    }

    private static void putRectangle(float x, float y,
            float width, float height, float[] buffer, ByteBuffer pointer) {
        buffer[0] = x;
        buffer[1] = y;
        buffer[2] = x + width;
        buffer[3] = y;
        buffer[4] = x;
        buffer[5] = y + height;
        buffer[6] = x + width;
        buffer[7] = y + height;
        pointer.asFloatBuffer().put(buffer, 0, 8).position(0);
    }

    private void drawRect(
            int x, int y, int width, int height, float matrix[]) {
        GL11 gl = mGL;
        gl.glPushMatrix();
        gl.glMultMatrixf(toGLMatrix(matrix), 0);
        putRectangle(x, y, width, height, mXyBuffer, mXyPointer);
        gl.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        gl.glPopMatrix();
    }

    public void drawNinePatch(
            NinePatchTexture tex, int x, int y, int width, int height) {

        NinePatchChunk chunk = tex.getNinePatchChunk();

        // The code should be easily extended to handle the general cases by
        // allocating more space for buffers. But let's just handle the only
        // use case.
        if (chunk.mDivX.length != 2 || chunk.mDivY.length != 2) {
            throw new RuntimeException("unsupported nine patch");
        }
        if (!tex.bind(this, mGL)) {
            throw new RuntimeException("cannot bind" + tex.toString());
        }
        if (width <= 0 || height <= 0) return ;

        int divX[] = mNinePatchX;
        int divY[] = mNinePatchY;
        float divU[] = mNinePatchU;
        float divV[] = mNinePatchV;

        int nx = stretch(divX, divU, chunk.mDivX, tex.getWidth(), width);
        int ny = stretch(divY, divV, chunk.mDivY, tex.getHeight(), height);

        setAlphaValue(mTransformation.getAlpha());
        Matrix matrix = mTransformation.getMatrix();
        matrix.getValues(mMatrixValues);
        GL11 gl = mGL;
        gl.glPushMatrix();
        gl.glMultMatrixf(toGLMatrix(mMatrixValues), 0);
        gl.glTranslatef(x, y, 0);
        drawMesh(divX, divY, divU, divV, nx, ny);
        gl.glPopMatrix();
    }

    /**
     * Stretches the texture according to the nine-patch rules. It will
     * linearly distribute the strechy parts defined in the nine-patch chunk to
     * the target area.
     *
     * <pre>
     *                      source
     *          /--------------^---------------\
     *         u0    u1       u2  u3     u4   u5
     * div ---> |fffff|ssssssss|fff|ssssss|ffff| ---> u
     *          |    div0    div1 div2   div3  |
     *          |     |       /   /      /    /
     *          |     |      /   /     /    /
     *          |     |     /   /    /    /
     *          |fffff|ssss|fff|sss|ffff| ---> x
     *         x0    x1   x2  x3  x4   x5
     *          \----------v------------/
     *                  target
     *
     * f: fixed segment
     * s: stretchy segment
     * </pre>
     *
     * @param div the stretch parts defined in nine-patch chunk
     * @param source the length of the texture
     * @param target the length on the drawing plan
     * @param u output, the positions of these dividers in the texture
     *        coordinate
     * @param x output, the corresponding position of these dividers on the
     *        drawing plan
     * @return the number of these dividers.
     */
    private int stretch(
            int x[], float u[], int div[], int source, int target) {
        int textureSize = Util.nextPowerOf2(source);
        float textureBound = (source - 0.5f) / textureSize;

        int stretch = 0;
        for (int i = 0, n = div.length; i < n; i += 2) {
            stretch += div[i + 1] - div[i];
        }

        float remaining = target - source + stretch;

        int lastX = 0;
        int lastU = 0;

        x[0] = 0;
        u[0] = 0;
        for (int i = 0, n = div.length; i < n; i += 2) {
            // fixed segment
            x[i + 1] = lastX + (div[i] - lastU);
            u[i + 1] = Math.min((float) div[i] / textureSize, textureBound);

            // stretchy segment
            float partU = div[i + 1] - div[i];
            int partX = (int)(remaining * partU / stretch + 0.5f);
            remaining -= partX;
            stretch -= partU;

            lastX = x[i + 1] + partX;
            lastU = div[i + 1];
            x[i + 2] = lastX;
            u[i + 2] = Math.min((float) lastU / textureSize, textureBound);
        }
        // the last fixed segment
        x[div.length + 1] = target;
        u[div.length + 1] = textureBound;

        // remove segments with length 0.
        int last = 0;
        for (int i = 1, n = div.length + 2; i < n; ++i) {
            if (x[last] == x[i]) continue;
            x[++last] = x[i];
            u[last] = u[i];
        }
        return last + 1;
    }

    private void drawMesh(
            int x[], int y[], float u[], float v[], int nx, int ny) {
        /*
         * Given a 3x3 nine-patch image, the vertex order is defined as the
         * following graph:
         *
         * (0) (1) (2) (3)
         *  |  /|  /|  /|
         *  | / | / | / |
         * (4) (5) (6) (7)
         *  | \ | \ | \ |
         *  |  \|  \|  \|
         * (8) (9) (A) (B)
         *  |  /|  /|  /|
         *  | / | / | / |
         * (C) (D) (E) (F)
         *
         * And we draw the triangle strip in the following index order:
         *
         * index: 04152637B6A5948C9DAEBF
         */
        int pntCount = 0;
        float xy[] = mXyBuffer;
        float uv[] = mUvBuffer;
        for (int j = 0; j < ny; ++j) {
            for (int i = 0; i < nx; ++i) {
                int xIndex = (pntCount++) << 1;
                int yIndex = xIndex + 1;
                xy[xIndex] = x[i];
                xy[yIndex] = y[j];
                uv[xIndex] = u[i];
                uv[yIndex] = v[j];
            }
        }
        mUvPointer.asFloatBuffer().put(uv, 0, pntCount << 1).position(0);
        mXyPointer.asFloatBuffer().put(xy, 0, pntCount << 1).position(0);

        int idxCount = 1;
        byte index[] = mIndexBuffer;
        for (int i = 0, bound = nx * (ny - 1); true;) {
            // normal direction
            --idxCount;
            for (int j = 0; j < nx; ++j, ++i) {
                index[idxCount++] = (byte) i;
                index[idxCount++] = (byte) (i + nx);
            }
            if (i >= bound) break;

            // reverse direction
            int sum = i + i + nx - 1;
            --idxCount;
            for (int j = 0; j < nx; ++j, ++i) {
                index[idxCount++] = (byte) (sum - i);
                index[idxCount++] = (byte) (sum - i + nx);
            }
            if (i >= bound) break;
        }
        mIndexPointer.put(index, 0, idxCount).position(0);

        mGL.glDrawElements(GL11.GL_TRIANGLE_STRIP,
                idxCount, GL11.GL_UNSIGNED_BYTE, mIndexPointer);
    }

    private float[] mapPoints(Matrix matrix, int x1, int y1, int x2, int y2) {
        float[] point = mXyBuffer;
        point[0] = x1; point[1] = y1; point[2] = x2; point[3] = y2;
        matrix.mapPoints(point, 0, point, 0, 4);
        return point;
    }

    public void clipRect(int x, int y, int width, int height) {
        float point[] = mapPoints(
                mTransformation.getMatrix(), x, y + height, x + width, y);

        // mMatrix could be a rotation matrix. In this case, we need to find
        // the boundaries after rotation. (only handle 90 * n degrees)
        if (point[0] > point[2]) {
            x = (int) point[2];
            width = (int) point[0] - x;
        } else {
            x = (int) point[0];
            width = (int) point[2] - x;
        }
        if (point[1] > point[3]) {
            y = (int) point[3];
            height = (int) point[1] - y;
        } else {
            y = (int) point[1];
            height = (int) point[3] - y;
        }
        mGL.glScissor(x, y, width, height);
    }

    public void clearClip() {
        mGL.glScissor(0, 0, getWidth(), getHeight());
    }

    private static float[] toGLMatrix(float v[]) {
        v[15] = v[8]; v[13] = v[5]; v[5] = v[4]; v[4] = v[1];
        v[12] = v[2]; v[1] = v[3]; v[3] = v[6];
        v[2] = v[6] = v[8] = v[9] = 0;
        v[10] = 1;
        return v;
    }

    public void drawColor(int x, int y, int width, int height, int color) {
        float alpha = mTransformation.getAlpha();
        GL11 gl = mGL;
        if (mTexture2DEnabled) {
            // Set mLastAlpha to an invalid value, so that it will reset again
            // in setAlphaValue(float) later.
            mLastAlpha = -1.0f;
            gl.glDisable(GL11.GL_TEXTURE_2D);
            mTexture2DEnabled = false;
        }
        alpha /= 256.0f;
        gl.glColor4f(Color.red(color) * alpha, Color.green(color) * alpha,
                Color.blue(color) * alpha, Color.alpha(color) * alpha);
        drawRect(x, y, width, height);
    }

    public void drawTexture(
            BasicTexture texture, int x, int y, int width, int height) {
        drawTexture(texture, x, y, width, height, mTransformation.getAlpha());
    }

    public void drawTexture(BasicTexture texture,
            int x, int y, int width, int height, float alpha) {

        if (!mTexture2DEnabled) {
            mGL.glEnable(GL11.GL_TEXTURE_2D);
            mTexture2DEnabled = true;
        }

        if (!texture.bind(this, mGL)) {
            throw new RuntimeException("cannot bind" + texture.toString());
        }
        if (width <= 0 || height <= 0) return ;

        Matrix matrix = mTransformation.getMatrix();
        matrix.getValues(mMatrixValues);

        // Test whether it has been rotated or flipped, if so, glDrawTexiOES
        // won't work
        if (isMatrixRotatedOrFlipped(mMatrixValues)) {
            putRectangle(0, 0,
                    (texture.mWidth - 0.5f) / texture.mTextureWidth,
                    (texture.mHeight - 0.5f) / texture.mTextureHeight,
                    mUvBuffer, mUvPointer);
            setAlphaValue(alpha);
            drawRect(x, y, width, height, mMatrixValues);
        } else {
            // draw the rect from bottom-left to top-right
            float points[] = mapPoints(matrix, x, y + height, x + width, y);
            x = (int) points[0];
            y = (int) points[1];
            width = (int) points[2] - x;
            height = (int) points[3] - y;
            if (width > 0 && height > 0) {
                setAlphaValue(alpha);
                ((GL11Ext) mGL).glDrawTexiOES(x, y, 0, width, height);
            }
        }
    }

    private static boolean isMatrixRotatedOrFlipped(float matrix[]) {
        return matrix[Matrix.MSKEW_X] != 0 || matrix[Matrix.MSKEW_Y] != 0
                || matrix[Matrix.MSCALE_X] < 0 || matrix[Matrix.MSCALE_Y] > 0;
    }

    public synchronized void onDrawFrame(GL10 gl) {
        if (ENABLE_FPS_TEST) {
            long now = System.nanoTime();
            if (mFrameCountingStart == 0) {
                mFrameCountingStart = now;
            } else if ((now - mFrameCountingStart) > 1000000000) {
                Log.v(TAG, "fps: " + (double) mFrameCount
                        * 1000000000 / (now - mFrameCountingStart));
                mFrameCountingStart = now;
                mFrameCount = 0;
            }
            ++mFrameCount;
        }

        if ((mFlags & FLAG_NEED_LAYOUT) != 0) layoutContentPane();
        clearClip();
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL11.GL_BLEND);
        gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        mAnimationTime = SystemClock.uptimeMillis();
        if (mContentView != null) {
            mContentView.render(GLRootView.this, (GL11) gl);
        }
        long now = SystemClock.uptimeMillis();
        for (Animation animation : mAnimations) {
            animation.setStartTime(now);
        }
        mAnimations.clear();
    }

    @Override
    public synchronized boolean dispatchTouchEvent(MotionEvent event) {
        // If this has been detached from root, we don't need to handle event
        return mContentView != null
                ? mContentView.dispatchTouchEvent(event)
                : false;
    }

    public DisplayMetrics getDisplayMetrics() {
        if (mDisplayMetrics == null) {
            mDisplayMetrics = new DisplayMetrics();
            ((Activity) getContext()).getWindowManager()
                    .getDefaultDisplay().getMetrics(mDisplayMetrics);
        }
        return mDisplayMetrics;
    }

    public void copyTexture2D(
            RawTexture texture, int x, int y, int width, int height)
            throws GLOutOfMemoryException {
        Matrix matrix = mTransformation.getMatrix();
        matrix.getValues(mMatrixValues);

        if (isMatrixRotatedOrFlipped(mMatrixValues)) {
            throw new IllegalArgumentException("cannot support rotated matrix");
        }
        float points[] = mapPoints(matrix, x, y + height, x + width, y);
        x = (int) points[0];
        y = (int) points[1];
        width = (int) points[2] - x;
        height = (int) points[3] - y;

        GL11 gl = mGL;
        int newWidth = Util.nextPowerOf2(width);
        int newHeight = Util.nextPowerOf2(height);
        int glError = GL11.GL_NO_ERROR;

        gl.glBindTexture(GL11.GL_TEXTURE_2D, texture.getId());

        int[] cropRect = {0,  0, width, height};
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
        gl.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0,
                GL11.GL_RGBA, x, y, newWidth, newHeight, 0);
        glError = gl.glGetError();

        if (glError == GL11.GL_OUT_OF_MEMORY) {
            throw new GLOutOfMemoryException();
        }

        if (glError != GL11.GL_NO_ERROR) {
            throw new RuntimeException(
                    "Texture copy fail, glError " + glError);
        }

        texture.setSize(width, height);
        texture.setTextureSize(newWidth, newHeight);
    }

}
