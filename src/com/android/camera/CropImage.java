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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class CropImage extends Activity {
    private static final String TAG = "CropImage";
    private ProgressDialog mFaceDetectionDialog = null;
    private ProgressDialog mSavingProgressDialog = null;
    private ImageManager.IImageList mAllImages;
    private Bitmap.CompressFormat mSaveFormat = Bitmap.CompressFormat.JPEG; // only used with mSaveUri
    private Uri mSaveUri = null;
    private int mAspectX, mAspectY;
    private int mOutputX, mOutputY;
    private boolean mDoFaceDetection = true;
    private boolean mCircleCrop = false;
    private boolean mWaitingToPick;
    private boolean mScale;
    private boolean mSaving;
    private boolean mScaleUp = true;

    CropImageView mImageView;
    ContentResolver mContentResolver;

    Bitmap mBitmap;
    Bitmap mCroppedImage;
    HighlightView mCrop;

    ImageManager.IImage mImage;

    public CropImage() {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    static public class CropImageView extends ImageViewTouchBase {
        ArrayList<HighlightView> mHighlightViews = new ArrayList<HighlightView>();
        HighlightView mMotionHighlightView = null;
        float mLastX, mLastY;
        int mMotionEdge;

        public CropImageView(Context context) {
            super(context);
        }

        @Override
        protected boolean doesScrolling() {
            return false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (mBitmapDisplayed != null) {
                for (HighlightView hv : mHighlightViews) {
                    hv.mMatrix.set(getImageMatrix());
                    hv.invalidate();
                    if (hv.mIsFocused) {
                        centerBasedOnHighlightView(hv);
                    }
                }
            }
        }

        public CropImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        protected void zoomTo(float scale, float centerX, float centerY) {
            super.zoomTo(scale, centerX, centerY);
            for (HighlightView hv : mHighlightViews) {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
            }
        }

        protected void zoomIn() {
            super.zoomIn();
            for (HighlightView hv : mHighlightViews) {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
            }
        }

        protected void zoomOut() {
            super.zoomOut();
            for (HighlightView hv : mHighlightViews) {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
            }
        }


        @Override
        protected boolean usePerfectFitBitmap() {
            return false;
        }

        @Override
        protected void postTranslate(float deltaX, float deltaY) {
            super.postTranslate(deltaX, deltaY);
            for (int i = 0; i < mHighlightViews.size(); i++) {
                HighlightView hv = mHighlightViews.get(i);
                hv.mMatrix.postTranslate(deltaX, deltaY);
                hv.invalidate();
            }
        }

        private void recomputeFocus(MotionEvent event) {
            for (int i = 0; i < mHighlightViews.size(); i++) {
                HighlightView hv = mHighlightViews.get(i);
                hv.setFocus(false);
                hv.invalidate();
            }

            for (int i = 0; i < mHighlightViews.size(); i++) {
                HighlightView hv = mHighlightViews.get(i);
                int edge = hv.getHit(event.getX(), event.getY());
                if (edge != HighlightView.GROW_NONE) {
                    if (!hv.hasFocus()) {
                        hv.setFocus(true);
                        hv.invalidate();
                    }
                    break;
                }
            }
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            CropImage cropImage = (CropImage)mContext;
            if (cropImage.mSaving)
                return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (cropImage.mWaitingToPick) {
                        recomputeFocus(event);
                    } else {
                        for (int i = 0; i < mHighlightViews.size(); i++) {
                            HighlightView hv = mHighlightViews.get(i);
                            int edge = hv.getHit(event.getX(), event.getY());
                            if (edge != HighlightView.GROW_NONE) {
                                mMotionEdge = edge;
                                mMotionHighlightView = hv;
                                mLastX = event.getX();
                                mLastY = event.getY();
                                mMotionHighlightView.setMode(edge == HighlightView.MOVE
                                        ? HighlightView.ModifyMode.Move
                                                : HighlightView.ModifyMode.Grow);
                                break;
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (cropImage.mWaitingToPick) {
                        for (int i = 0; i < mHighlightViews.size(); i++) {
                            HighlightView hv = mHighlightViews.get(i);
                            if (hv.hasFocus()) {
                                cropImage.mCrop = hv;
                                for (int j = 0; j < mHighlightViews.size(); j++) {
                                    if (j == i)
                                        continue;
                                    mHighlightViews.get(j).setHidden(true);
                                }
                                centerBasedOnHighlightView(hv);
                                ((CropImage)mContext).mWaitingToPick = false;
                                return true;
                            }
                        }
                    } else if (mMotionHighlightView != null) {
                        centerBasedOnHighlightView(mMotionHighlightView);
                        mMotionHighlightView.setMode(HighlightView.ModifyMode.None);
                    }
                    mMotionHighlightView = null;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (cropImage.mWaitingToPick) {
                        recomputeFocus(event);
                    } else if (mMotionHighlightView != null) {
                        mMotionHighlightView.handleMotion(mMotionEdge, event.getX()-mLastX, event.getY()-mLastY);
                        mLastX = event.getX();
                        mLastY = event.getY();

                        if (true) {
                            // This section of code is optional.  It has some user
                            // benefit in that moving the crop rectangle against
                            // the edge of the screen causes scrolling but it means
                            // that the crop rectangle is no longer fixed under
                            // the user's finger.
                            ensureVisible(mMotionHighlightView);
                        }
                    }
                    break;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    center(true, true, true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    // if we're not zoomed then there's no point in even allowing
                    // the user to move the image around.  This call to center
                    // puts it back to the normalized location (with false meaning
                    // don't animate).
                    if (getScale() == 1F)
                        center(true, true, false);
                    break;
            }

            return true;
        }

        private void ensureVisible(HighlightView hv) {
            Rect r = hv.mDrawRect;

            int panDeltaX1 = Math.max(0, mLeft - r.left);
            int panDeltaX2 = Math.min(0, mRight - r.right);

            int panDeltaY1 = Math.max(0, mTop - r.top);
            int panDeltaY2 = Math.min(0, mBottom - r.bottom);

            int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;
            int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

            if (panDeltaX != 0 || panDeltaY != 0)
                panBy(panDeltaX, panDeltaY);
        }

        private void centerBasedOnHighlightView(HighlightView hv) {
            Rect drawRect = hv.mDrawRect;

            float width = drawRect.width();
            float height = drawRect.height();

            float thisWidth = getWidth();
            float thisHeight = getHeight();

            float z1 = thisWidth / width * .6F;
            float z2 = thisHeight / height * .6F;

            float zoom = Math.min(z1, z2);
            zoom = zoom * this.getScale();
            zoom = Math.max(1F, zoom);

            if ((Math.abs(zoom - getScale()) / zoom) > .1) {
                float [] coordinates = new float[] { hv.mCropRect.centerX(), hv.mCropRect.centerY() };
                getImageMatrix().mapPoints(coordinates);
                zoomTo(zoom, coordinates[0], coordinates[1], 300F);
            }

            ensureVisible(hv);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (int i = 0; i < mHighlightViews.size(); i++) {
                mHighlightViews.get(i).draw(canvas);
            }
        }

        public HighlightView get(int i) {
            return mHighlightViews.get(i);
        }

        public int size() {
            return mHighlightViews.size();
        }

        public void add(HighlightView hv) {
            mHighlightViews.add(hv);
            invalidate();
        }
    }

    private void fillCanvas(int width, int height, Canvas c) {
        Paint paint = new Paint();
        paint.setColor(0x00000000);  // pure alpha
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        c.drawRect(0F, 0F, width, height, paint);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mContentResolver = getContentResolver();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.cropimage);

        mImageView = (CropImageView) findViewById(R.id.image);

        MenuHelper.showStorageToast(this);

        try {
            android.content.Intent intent = getIntent();
            Bundle extras = intent.getExtras();
            if (Config.LOGV)
                Log.v(TAG, "extras are " + extras);
            if (extras != null) {
                for (String s: extras.keySet()) {
                    if (Config.LOGV)
                        Log.v(TAG, "" + s + " >>> " + extras.get(s));
                }
                if (extras.getString("circleCrop") != null) {
                    mCircleCrop = true;
                    mAspectX = 1;
                    mAspectY = 1;
                }
                mSaveUri = (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT);
                if (mSaveUri != null) {
                    String compressFormatString = extras.getString("outputFormat");
                    if (compressFormatString != null)
                        mSaveFormat = Bitmap.CompressFormat.valueOf(compressFormatString);
                }
                mBitmap = (Bitmap) extras.getParcelable("data");
                mAspectX = extras.getInt("aspectX");
                mAspectY = extras.getInt("aspectY");
                mOutputX = extras.getInt("outputX");
                mOutputY = extras.getInt("outputY");
                mScale = extras.getBoolean("scale", true);
                mScaleUp = extras.getBoolean("scaleUpIfNeeded", true);
                mDoFaceDetection = extras.containsKey("noFaceDetection") ? !extras.getBoolean("noFaceDetection") : true;
            }

            if (mBitmap == null) {
                Uri target = intent.getData();
                mAllImages = ImageManager.makeImageList(target, CropImage.this, ImageManager.SORT_ASCENDING);
                mImage = mAllImages.getImageForUri(target);
                if(mImage != null) {
                    // don't read in really large bitmaps.  max out at 1000.
                    // TODO when saving the resulting bitmap use the decode/crop/encode
                    // api so we don't lose any resolution
                    mBitmap = mImage.thumbBitmap();
                    if (Config.LOGV)
                        Log.v(TAG, "thumbBitmap returned " + mBitmap);
                }
            }

            if (mBitmap == null) {
                finish();
                return;
            }

            mHandler.postDelayed(new Runnable() {
                public void run() {
                    if (isFinishing()) {
                        return;
                    }
                    mFaceDetectionDialog = ProgressDialog.show(CropImage.this,
                            null,
                            getResources().getString(R.string.runningFaceDetection),
                            true, false);
                    mImageView.setImageBitmapResetBase(mBitmap, true, true);
                    if (mImageView.getScale() == 1F)
                        mImageView.center(true, true, false);

                    new Thread(new Runnable() {
                        public void run() {
                            final Bitmap b = mImage != null ? mImage.fullSizeBitmap(500) : mBitmap;
                            if (Config.LOGV)
                                Log.v(TAG, "back from mImage.fullSizeBitmap(500) with bitmap of size " + b.getWidth() + " / " + b.getHeight());
                            mHandler.post(new Runnable() {
                                public void run() {
                                    if (b != mBitmap && b != null) {
                                        mBitmap = b;
                                        mImageView.setImageBitmapResetBase(b, true, false);
                                    }
                                    if (mImageView.getScale() == 1F)
                                        mImageView.center(true, true, false);

                                   new Thread(mRunFaceDetection).start();
                                }
                            });
                        }
                    }).start();
                }}, 100);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bitmap", e);
            finish();
        }

        findViewById(R.id.discard).setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        findViewById(R.id.save).setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(View v) {
                // TODO this code needs to change to use the decode/crop/encode single
                // step api so that we don't require that the whole (possibly large) bitmap
                // doesn't have to be read into memory
                mSaving = true;
                if (mCroppedImage == null) {
                    if (mCrop == null) {
                        if (Config.LOGV)
                            Log.v(TAG, "no cropped image...");
                        return;
                    }

                    Rect r = mCrop.getCropRect();

                    int width  = r.width();
                    int height = r.height();

                    // if we're circle cropping we'll want alpha which is the third param here
                    mCroppedImage = Bitmap.createBitmap(width, height,
                            mCircleCrop ?
                                    Bitmap.Config.ARGB_8888 :
                                    Bitmap.Config.RGB_565);
                    Canvas c1 = new Canvas(mCroppedImage);
                    c1.drawBitmap(mBitmap, r, new Rect(0, 0, width, height), null);

                    if (mCircleCrop) {
                        // OK, so what's all this about?
                        // Bitmaps are inherently rectangular but we want to return something
                        // that's basically a circle.  So we fill in the area around the circle
                        // with alpha.  Note the all important PortDuff.Mode.CLEAR.
                        Canvas c = new Canvas (mCroppedImage);
                        android.graphics.Path p = new android.graphics.Path();
                        p.addCircle(width/2F, height/2F, width/2F, android.graphics.Path.Direction.CW);
                        c.clipPath(p, Region.Op.DIFFERENCE);

                        fillCanvas(width, height, c);
                    }
                }

                /* If the output is required to a specific size then scale or fill */
                if (mOutputX != 0 && mOutputY != 0) {

                    if (mScale) {

                        /* Scale the image to the required dimensions */
                        mCroppedImage = ImageLoader.transform(new Matrix(),
                                mCroppedImage, mOutputX, mOutputY, mScaleUp);
                    } else {

                        /* Don't scale the image crop it to the size requested.
                         * Create an new image with the cropped image in the center and
                         * the extra space filled.
                         */

                        /* Don't scale the image but instead fill it so it's the required dimension */
                        Bitmap b = Bitmap.createBitmap(mOutputX, mOutputY, Bitmap.Config.RGB_565);
                        Canvas c1 = new Canvas(b);

                        /* Draw the cropped bitmap in the center */
                        Rect r = mCrop.getCropRect();
                        int left = (mOutputX / 2) - (r.width() / 2);
                        int top = (mOutputY / 2) - (r.width() / 2);
                        c1.drawBitmap(mBitmap, r, new Rect(left, top, left
                                + r.width(), top + r.height()), null);

                        /* Set the cropped bitmap as the new bitmap */
                        mCroppedImage = b;
                    }
                }

                Bundle myExtras = getIntent().getExtras();
                if (myExtras != null && (myExtras.getParcelable("data") != null || myExtras.getBoolean("return-data"))) {
                    Bundle extras = new Bundle();
                    extras.putParcelable("data", mCroppedImage);
                    setResult(RESULT_OK,
                            (new Intent()).setAction("inline-data").putExtras(extras));
                    finish();
                } else {
                    if (!isFinishing()) {
                        mSavingProgressDialog = ProgressDialog.show(CropImage.this,
                                null,
                                getResources().getString(R.string.savingImage),
                                true, true);
                    }
                    Runnable r = new Runnable() {
                        public void run() {
                            if (mSaveUri != null) {
                                OutputStream outputStream = null;
                                try {
                                    String scheme = mSaveUri.getScheme();
                                    if (scheme.equals("file")) {
                                        outputStream = new FileOutputStream(mSaveUri.toString().substring(scheme.length()+":/".length()));
                                    } else {
                                        outputStream = mContentResolver.openOutputStream(mSaveUri);
                                    }
                                    if (outputStream != null)
                                        mCroppedImage.compress(mSaveFormat, 75, outputStream);

                                } catch (IOException ex) {
                                    if (Config.LOGV)
                                        Log.v(TAG, "got IOException " + ex);
                                } finally {
                                    if (outputStream != null)  {
                                        try {
                                            outputStream.close();
                                        } catch (IOException ex) {

                                        }
                                    }
                                }
                                Bundle extras = new Bundle();
                                setResult(RESULT_OK,
                                        (new Intent())
                                                .setAction(mSaveUri.toString())
                                                .putExtras(extras));
                            } else {
                                Bundle extras = new Bundle();
                                extras.putString("rect",  mCrop.getCropRect().toString());

                                // here we decide whether to create a new image or
                                // modify the existing image
                                if (false) {
                                    /*
                                    // this is the "modify" case
                                    ImageManager.IGetBoolean_cancelable cancelable =
                                        mImage.saveImageContents(mCroppedImage, null, null, null, mImage.getDateTaken(), 0, false);
                                    boolean didSave = cancelable.get();
                                    extras.putString("thumb1uri", mImage.thumbUri().toString());
                                    setResult(RESULT_OK,
                                            (new Intent()).setAction(mImage.fullSizeImageUri().toString())
                                                    .putExtras(extras));
                                    */
                                } else {
                                    // this is the "new image" case
                                    java.io.File oldPath = new java.io.File(mImage.getDataPath());
                                    java.io.File directory = new java.io.File(oldPath.getParent());

                                    int x = 0;
                                    String fileName = oldPath.getName();
                                    fileName = fileName.substring(0, fileName.lastIndexOf("."));

                                    while (true) {
                                        x += 1;
                                        String candidate = directory.toString() + "/" + fileName + "-" + x + ".jpg";
                                        if (Config.LOGV)
                                            Log.v(TAG, "candidate is " + candidate);
                                        boolean exists = (new java.io.File(candidate)).exists();
                                        if (!exists)
                                            break;
                                    }

                                    try {
                                        Uri newUri = ImageManager.instance().addImage(
                                                CropImage.this,
                                                getContentResolver(),
                                                mImage.getTitle(),
                                                mImage.getDescription(),
                                                mImage.getDateTaken(),
                                                null,    // TODO this null is going to cause us to lose the location (gps)
                                                0,       // TODO this is going to cause the orientation to reset
                                                directory.toString(),
                                                fileName + "-" + x + ".jpg");

                                        ImageManager.IAddImage_cancelable cancelable = ImageManager.instance().storeImage(
                                                newUri,
                                                CropImage.this,
                                                getContentResolver(),
                                                0, // TODO fix this orientation
                                                mCroppedImage,
                                                null);

                                        cancelable.get();
                                        setResult(RESULT_OK,
                                                (new Intent()).setAction(newUri.toString())
                                                .putExtras(extras));
                                    } catch (Exception ex) {
                                        // basically ignore this or put up
                                        // some ui saying we failed
                                    }
                                }
                            }
                            finish();
                        }
                    };
                    Thread t = new Thread(r);
                    t.start();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    Handler mHandler = new Handler();

    Runnable mRunFaceDetection = new Runnable() {
        float mScale = 1F;
        RectF mUnion = null;
        Matrix mImageMatrix;
        FaceDetector.Face[] mFaces = new FaceDetector.Face[3];
        int mNumFaces;

        private void handleFace(FaceDetector.Face f) {
            PointF midPoint = new PointF();

            int r = ((int)(f.eyesDistance() * mScale)) * 2 ;
            f.getMidPoint(midPoint);
            midPoint.x *= mScale;
            midPoint.y *= mScale;

            int midX = (int) midPoint.x;
            int midY = (int) midPoint.y;

            HighlightView hv = makeHighlightView();

            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            RectF faceRect = new RectF(midX, midY, midX, midY);
            faceRect.inset(-r, -r);
            if (faceRect.left < 0)
                faceRect.inset(-faceRect.left, -faceRect.left);

            if (faceRect.top < 0)
                faceRect.inset(-faceRect.top, -faceRect.top);

            if (faceRect.right > imageRect.right)
                faceRect.inset(faceRect.right - imageRect.right, faceRect.right - imageRect.right);

            if (faceRect.bottom > imageRect.bottom)
                faceRect.inset(faceRect.bottom - imageRect.bottom, faceRect.bottom - imageRect.bottom);

            hv.setup(mImageMatrix, imageRect, faceRect, mCircleCrop, mAspectX != 0 && mAspectY != 0);

            if (mUnion == null) {
                mUnion = new RectF(faceRect);
            } else {
                mUnion.union(faceRect);
            }

            mImageView.add(hv);
        }

        private HighlightView makeHighlightView() {
            return new HighlightView(mImageView);
        }

        private void makeDefault() {
            HighlightView hv = makeHighlightView();

            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            // make the default size about 4/5 of the width or height
            int cropWidth = Math.min(width, height) * 4 / 5;
            int cropHeight = cropWidth;

            if (mAspectX != 0 && mAspectY != 0) {
                if (mAspectX > mAspectY) {
                    cropHeight = cropWidth * mAspectY / mAspectX;
//                    Log.v(TAG, "adjusted cropHeight to " + cropHeight);
                } else {
                    cropWidth = cropHeight * mAspectX / mAspectY;
//                    Log.v(TAG, "adjusted cropWidth to " + cropWidth);
                }
            }

            int x = (width - cropWidth) / 2;
            int y = (height - cropHeight) / 2;

            RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
            hv.setup(mImageMatrix, imageRect, cropRect, mCircleCrop, mAspectX != 0 && mAspectY != 0);
            mImageView.add(hv);
        }

        private Bitmap prepareBitmap() {
            if (mBitmap == null)
                return null;

            // scale the image down for faster face detection
            // 256 pixels wide is enough.
            if (mBitmap.getWidth() > 256) {
                mScale = 256.0F / (float) mBitmap.getWidth();
            }
            Matrix matrix = new Matrix();
            matrix.setScale(mScale, mScale);
            Bitmap faceBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap
                    .getWidth(), mBitmap.getHeight(), matrix, true);
            return faceBitmap;
        }

        public void run() {
            mImageMatrix = mImageView.getImageMatrix();
            Bitmap faceBitmap = prepareBitmap();

            mScale = 1.0F / mScale;
            if (faceBitmap != null && mDoFaceDetection) {
                FaceDetector detector = new FaceDetector(faceBitmap.getWidth(),
                    faceBitmap.getHeight(), mFaces.length);
                mNumFaces = detector.findFaces(faceBitmap, mFaces);
                if (Config.LOGV)
                    Log.v(TAG, "numFaces is " + mNumFaces);
            }
            mHandler.post(new Runnable() {
                public void run() {
                    mWaitingToPick = mNumFaces > 1;
                    if (mNumFaces > 0) {
                        for (int i = 0; i < mNumFaces; i++) {
                            handleFace(mFaces[i]);
                        }
                    } else {
                        makeDefault();
                    }
                    mImageView.invalidate();
                    if (mImageView.mHighlightViews.size() == 1) {
                        mCrop = mImageView.mHighlightViews.get(0);
                        mCrop.setFocus(true);
                    }

                    closeProgressDialog();

                    if (mNumFaces > 1) {
                        Toast t = Toast.makeText(CropImage.this, R.string.multiface_crop_help, Toast.LENGTH_SHORT);
                        t.show();
                    }
                }
            });

        }
    };

    @Override
    public void onStop() {
        closeProgressDialog();
        super.onStop();
        if (mAllImages != null)
            mAllImages.deactivate();
    }

    private synchronized void closeProgressDialog() {
        if (mFaceDetectionDialog != null) {
            mFaceDetectionDialog.dismiss();
            mFaceDetectionDialog = null;
        }
        if (mSavingProgressDialog != null) {
            mSavingProgressDialog.dismiss();
            mSavingProgressDialog = null;
        }
    }
}
