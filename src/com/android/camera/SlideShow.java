/*
 * Copyright (C) 2007 The Android Open Source Project
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

import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ViewSwitcher;
import android.widget.Gallery.LayoutParams;

import android.view.WindowManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import com.android.camera.ImageManager.IGetBitmap_cancelable;
import com.android.camera.ImageManager.IImage;
import com.android.camera.ImageManager.IImageList;

import android.view.MotionEvent;

public class SlideShow extends Activity implements ViewSwitcher.ViewFactory
{
    static final private String TAG = "SlideShow";
    static final int sLag = 2000;
    static final int sNextImageInterval = 3000;
    private ImageManager.IImageList mImageList;
    private int mCurrentPosition = 0;
    private ImageView mSwitcher;
    private boolean mPosted = false;

    @Override
    protected void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        Window wp = getWindow();
        wp.setFlags(FLAG_KEEP_SCREEN_ON, FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.slide_show);

        mSwitcher = (ImageView)findViewById(R.id.imageview);
        if (android.util.Config.LOGV)
            Log.v(TAG, "mSwitcher " + mSwitcher);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (mImageList == null) {
            mImageList = new FileImageList();
            mCurrentPosition = 0;
        }
        loadImage();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelPost();
    }

    static public class ImageViewTouch extends ImageView {
        class xy {
            public xy(float xIn, float yIn) {
                x = xIn;
                y = yIn;
                timeAdded = System.currentTimeMillis();
            }
            public xy(MotionEvent e) {
                x = e.getX();
                y = e.getY();
                timeAdded = System.currentTimeMillis();
            }
            float x,y;
            long timeAdded;
        }

        SlideShow mSlideShow;
        Paint mPaints[] = new Paint[1];
        ArrayList<xy> mPoints = new ArrayList<xy>();
        boolean mDown;

        public ImageViewTouch(Context context) {
            super(context);
            mSlideShow = (SlideShow) context;
            setScaleType(ImageView.ScaleType.CENTER);
            setupPaint();
        }

        public ImageViewTouch(Context context, AttributeSet attrs) {
            super(context, attrs);
            mSlideShow = (SlideShow) context;
            setScaleType(ImageView.ScaleType.CENTER);
            setupPaint();
        }

        private void setupPaint() {
            for (int i = 0; i < mPaints.length; i++) {
                Paint p = new Paint();
                p.setARGB(255, 255, 255, 0);
                p.setAntiAlias(true);
                p.setStyle(Paint.Style.FILL);
                p.setStrokeWidth(3F);
                mPaints[i] = p;
            }
        }

        private void addEvent(MotionEvent event) {
            long now = System.currentTimeMillis();
            mPoints.add(new xy(event));
            for (int i = 0; i < event.getHistorySize(); i++)
                mPoints.add(new xy(event.getHistoricalX(i), event.getHistoricalY(i)));
            while (mPoints.size() > 0) {
                xy ev = mPoints.get(0);
                if (now - ev.timeAdded < sLag)
                    break;
                mPoints.remove(0);
            }
        }

        public boolean onTouchEvent(MotionEvent event) {
            addEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mDown = true;
                    mSlideShow.cancelPost();
                    postInvalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    mDown = false;
                    postInvalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    mSlideShow.cancelPost();
                    postInvalidate();
                    break;
            }
            return true;
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            boolean didPaint = false;
            long now = System.currentTimeMillis();
            for (xy ev: mPoints) {
                Paint p = mPaints[0];
                long delta = now - ev.timeAdded;
                if (delta > sLag)
                    continue;

                int alpha2 = Math.max(0, 255 - (255 * (int)delta / sLag));
                if (alpha2 == 0)
                    continue;
                p.setAlpha(alpha2);
                canvas.drawCircle(ev.x, ev.y, 2, p);
                didPaint = true;
            }
            if (didPaint && !mDown)
                postInvalidate();
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cancelPost();
                loadPreviousImage();
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cancelPost();
                loadNextImage();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mPosted)
                    cancelPost();
                else
                    loadNextImage();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void cancelPost() {
        mHandler.removeCallbacks(mNextImageRunnable);
        mPosted = false;
    }

    private void post() {
        mHandler.postDelayed(mNextImageRunnable, sNextImageInterval);
        mPosted = true;
    }

    private void loadImage() {
        ImageManager.IImage image = mImageList.getImageAt(mCurrentPosition);
        if (image == null)
            return;

        Bitmap bitmap = image.thumbBitmap();
        if (bitmap == null)
            return;

        mSwitcher.setImageDrawable(new BitmapDrawable(bitmap));
        post();
    }

    private Runnable mNextImageRunnable = new Runnable() {
        public void run() {
            if (android.util.Config.LOGV)
                Log.v(TAG, "mNextImagerunnable called");
            loadNextImage();
        }
    };

    private void loadNextImage() {
        if (++mCurrentPosition >= mImageList.getCount())
            mCurrentPosition = 0;
        loadImage();
    }

    private void loadPreviousImage() {
        if (mCurrentPosition == 0)
            mCurrentPosition = mImageList.getCount() - 1;
        else
            mCurrentPosition -= 1;

        loadImage();
    }

    public View makeView() {
        ImageView i = new ImageView(this);
        i.setBackgroundColor(0xFF000000);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        return i;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    class FileImageList implements IImageList {
        public HashMap<String, String> getBucketIds() {
            throw new UnsupportedOperationException();
        }

        public void checkThumbnails(ThumbCheckCallback cb, int totalThumbnails) {
            // TODO Auto-generated method stub

        }

        public void commitChanges() {
            // TODO Auto-generated method stub

        }

        public void removeOnChangeListener(OnChange changeCallback) {
            // TODO Auto-generated method stub

        }

        public void setOnChangeListener(OnChange changeCallback, Handler h) {
            // TODO Auto-generated method stub

        }

        private ArrayList<FileImage> mImages = new ArrayList<FileImage>();
        // image uri ==> Image object
        private HashMap<Long, IImage> mCache = new HashMap<Long, IImage>();

        class FileImage extends ImageManager.SimpleBaseImage {
            long mId;
            String mPath;

            FileImage(long id, String path) {
                mId = id;
                mPath = path;
            }

            public long imageId() {
                return mId;
            }

            public String getDataPath() {
                return mPath;
            }

            public Bitmap fullSizeBitmap(int targetWidthOrHeight) {
                return BitmapFactory.decodeFile(mPath);
            }

            public IGetBitmap_cancelable fullSizeBitmap_cancelable(int targetWidthOrHeight) {
                return null;
            }

            public Bitmap thumbBitmap() {
                Bitmap b = fullSizeBitmap(320);
                Matrix m = new Matrix();
                float scale = 320F / (float) b.getWidth();
                m.setScale(scale, scale);
                Bitmap scaledBitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                return scaledBitmap;
            }

            public Bitmap miniThumbBitmap() {
                return thumbBitmap();
            }

            public long fullSizeImageId() {
                return mId;
            }
        }

        private void enumerate(String path, ArrayList<String> list) {
            File f = new File(path);
            if (f.isDirectory()) {
                String [] children = f.list();
                if (children != null) {
                    for (int i = 0; i < children.length; i++) {
                        if (children[i].charAt(0) != '.')
                            enumerate(path + "/" + children[i], list);
                    }
                }
            } else {
                if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".png")) {
                    if (f.length() > 0) {
                        list.add(path);
                    }
                }
            }
        }

        public FileImageList() {
            ArrayList<String> list = new ArrayList<String>();
            enumerate(Environment.getExternalStorageDirectory().getPath(), list);
            enumerate("/data/images", list);

            for (int i = 0; i < list.size(); i++) {
                FileImage img = new FileImage(i, list.get(i));
                mCache.put((long)i, img);
                mImages.add(img);
            }
        }

        public IImage getImageAt(int i) {
            if (i >= mImages.size())
                return null;

            return mImages.get(i);
        }

        public IImage getImageForUri(Uri uri) {
            // TODO make this a hash lookup
            int count = getCount();
            for (int i = 0; i < count; i++) {
                IImage image = getImageAt(i);
                if (image.fullSizeImageUri().equals(uri)) {
                    return image;
                }
            }
            return null;
        }

        public IImage getImageWithId(long id) {
            throw new UnsupportedOperationException();
        }

        public void removeImageAt(int i) {
            throw new UnsupportedOperationException();
        }

        public boolean removeImage(IImage image) {
            throw new UnsupportedOperationException();
        }

        public int getCount() {
            return mImages.size();
        }

        public boolean isEmpty() {
            return mImages.isEmpty();
        }

        public void deactivate() {
            // nothing to do here
        }
    }

}
