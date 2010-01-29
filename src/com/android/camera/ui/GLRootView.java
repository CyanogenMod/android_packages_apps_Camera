package com.android.camera.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.os.Process;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Transformation;

import com.android.camera.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

public class GLRootView extends GLSurfaceView
        implements GLSurfaceView.Renderer {
    private static final String TAG = "GLRootView";

    private final boolean ENABLE_FPS_TEST = false;
    private int mFrameCount = 0;
    private long mFrameCountingStart = 0;

    // The event processing timeout for GL events, 1.5 seconds
    private static final long EVENT_TIMEOUT = 1500;
    private static final int VERTEX_BUFFER_SIZE = 8;

    private static final int FLAG_INITIALIZED = 1;
    private static final int FLAG_NEED_LAYOUT = 2;
    private static final int FLAG_TEXTURE_MODE = 4;

    private GL11 mGL;
    private GLView mContentView;
    private DisplayMetrics mDisplayMetrics;

    private final Stack<Transformation> mFreeTransform =
            new Stack<Transformation>();

    private final Transformation mTransformation = new Transformation();
    private final Stack<Transformation> mTransformStack =
            new Stack<Transformation>();

    private float mLastAlpha = mTransformation.getAlpha();

    private final float mMatrixValues[] = new float[16];

    private ByteBuffer mVertexBuffer;
    private ByteBuffer mTexCoordBuffer;

    private int mFlags = FLAG_NEED_LAYOUT;

    public GLRootView(Context context) {
        super(context);
        initialize();
    }

    public Transformation obtainTransformation() {
        return mFreeTransform.isEmpty() ? new Transformation() : mFreeTransform.pop();
    }

    public void freeTransformation(Transformation freeTransformation) {
        mFreeTransform.push(freeTransformation);
    }

    public GLRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public Transformation getTransformation() {
        return mTransformation;
    }

    public void pushTransform() {
        Transformation trans = obtainTransformation();
        trans.set(mTransformation);
        mTransformStack.push(trans);
    }

    public void popTransform() {
        Transformation trans = mTransformStack.pop();
        mTransformation.set(trans);
        freeTransformation(trans);
    }

    private void initialize() {
        mFlags |= FLAG_INITIALIZED;
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        super.setDebugFlags(DEBUG_CHECK_GL_ERROR);

        setRenderer(this);

        mVertexBuffer = ByteBuffer
                .allocateDirect(VERTEX_BUFFER_SIZE * Float.SIZE / Byte.SIZE)
                .order(ByteOrder.nativeOrder());
        mVertexBuffer.asFloatBuffer()
                .put(new float[] {0, 0, 1, 0, 0, 1, 1, 1})
                .position(0);
        mTexCoordBuffer = ByteBuffer
                .allocateDirect(VERTEX_BUFFER_SIZE * Float.SIZE / Byte.SIZE)
                .order(ByteOrder.nativeOrder());
    }

    public void setContentPane(GLView content) {
        mContentView = content;
        content.mRootView = this;

        // no parent for the content pane
        content.onAddToParent(null);
        requestLayoutContentPane();
    }

    public GLView getContentPane() {
        return mContentView;
    }

    public GLRootView getGLRootView() {
        return this;
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

        // Increase the priority of the render thread
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);

        // Disable unused state
        gl.glDisable(GL11.GL_LIGHTING);

        // Enable used features
        gl.glEnable(GL11.GL_BLEND);
        gl.glEnable(GL11.GL_SCISSOR_TEST);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glEnable(GL11.GL_TEXTURE_2D);
        gl.glEnable(GL11.GL_STENCIL_TEST);

        gl.glTexEnvf(GL11.GL_TEXTURE_ENV,
                GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);

        // Set the blend function for premultiplied alpha.
        gl.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Set the background color
        gl.glClearColor(0f, 0f, 0f, 0f);
        gl.glVertexPointer(2, GL11.GL_FLOAT, 0, mVertexBuffer);
        gl.glTexCoordPointer(2, GL11.GL_FLOAT, 0, mTexCoordBuffer);
    }

    protected void setTexCoords(float ... point) {
        mTexCoordBuffer.asFloatBuffer().put(point).position(0);
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
        drawRect(x, y, width, height, mTransformation.getAlpha());
    }

    public void drawRect(int x, int y, int width, int height, float alpha) {
        GL11 gl = mGL;
        gl.glPushMatrix();
        mTransformation.getMatrix().getValues(mMatrixValues);

        setAlphaValue(alpha);
        gl.glMultMatrixf(toGLMatrix(mMatrixValues), 0);
        gl.glTranslatef(x, y, 0);
        gl.glScalef(width, height, 1);
        gl.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        gl.glPopMatrix();
    }

    public void clipRect(int x, int y, int width, int height) {
        float point[] = new float[]{x, y + height, x + width, y};

        mTransformation.getMatrix().mapPoints(point);

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

    public void draw2D(int x, int y, int width, int height, float alpha) {
        if (width <= 0 || height <= 0) return ;

        Matrix matrix = mTransformation.getMatrix();
        matrix.getValues(mMatrixValues);

        // Test if it has been rotated, if it is, glDrawTexiOES won't work
        if (mMatrixValues[1] != 0
                || mMatrixValues[3] != 0 || mMatrixValues[0] < 0) {
            drawRect(x, y, width, height, alpha);
        } else {
            // draw the rect from bottom-left to top-right
            float points[] = new float[]{x, y + height, x + width, y};
            matrix.mapPoints(points);
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

    public void draw2D(int x, int y, int width, int height) {
        draw2D(x, y, width, height, mTransformation.getAlpha());
    }

    // This is a GLSurfaceView.Renderer callback
    public void onDrawFrame(GL10 gl) {
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

        // gl.glScissor(0, 0, getWidth(), getHeight());
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL11.GL_BLEND);
        gl.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        /*gl.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        gl.glColor4f(0, 0, 0.5f, 0.4f);
        drawRect(30, 30, 30, 30);*/

        if (mContentView != null) {
            mContentView.render(GLRootView.this, (GL11) gl);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        // Allocate a new event to prevent concurrency access
        FutureTask<Boolean> task = new FutureTask<Boolean>(
                new TouchEventHandler(MotionEvent.obtain(event)));
        queueEvent(task);
        try {
            return task.get(EVENT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.w(TAG, "event timeout, assume the event is handled");
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class TouchEventHandler implements Callable<Boolean> {

        private final MotionEvent mEvent;

        public TouchEventHandler(MotionEvent event) {
            mEvent = event;
        }

        public Boolean call() throws Exception {
            return mContentView.dispatchTouchEvent(mEvent);
        }
    }

    public DisplayMetrics getDisplayMetrics() {
        if (mDisplayMetrics == null) {
            mDisplayMetrics = new DisplayMetrics();
            ((Activity) getContext()).getWindowManager()
                    .getDefaultDisplay().getMetrics(mDisplayMetrics);
        }
        return mDisplayMetrics;
    }

    public Texture copyTexture2D(int x, int y, int width, int height)
            throws GLOutOfMemoryException {

        Matrix matrix = mTransformation.getMatrix();
        matrix.getValues(mMatrixValues);

        // Test if it has been rotated, if it is, glDrawTexiOES won't work
        if (mMatrixValues[1] != 0 || mMatrixValues[3] != 0) {
            throw new IllegalArgumentException("cannot support rotated matrix");
        }

        float points[] = new float[]{x, y + height, x + width, y};
        matrix.mapPoints(points);
        x = (int) points[0];
        y = (int) points[1];
        width = (int) points[2] - x;
        height = (int) points[3] - y;

        GL11 gl = mGL;
        int newWidth = Util.nextPowerOf2(width);
        int newHeight = Util.nextPowerOf2(height);
        int glError = GL11.GL_NO_ERROR;

        int[] textureId = new int[1];
        gl.glGenTextures(1, textureId, 0);
        gl.glBindTexture(GL11.GL_TEXTURE_2D, textureId[0]);
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

        if (glError == GL11.GL_NO_ERROR) {
            return new RawTexture(gl, textureId[0], width, height,
                    (float) width / newWidth, (float) height / newHeight);
        }
        throw new RuntimeException(
                "Texture copy fail, glError " + glError);
    }

}
