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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Wallpaper picker for the camera application. This just redirects to the standard pick action.
 */
public class Wallpaper extends Activity {
    private static final String LOG_TAG = "Camera";
    static final int PHOTO_PICKED = 1;
    static final int CROP_DONE = 2;
    
    static final int SHOW_PROGRESS = 0;
    static final int FINISH = 1;
    
    static final String sDoLaunchIcicle = "do_launch";
    static final String sTempFilePathIcicle = "temp_file_path";
    
    private ProgressDialog mProgressDialog = null;
    private boolean mDoLaunch = true;
    private String mTempFilePath;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PROGRESS: {
                    CharSequence c = getText(R.string.wallpaper);
                    mProgressDialog = ProgressDialog.show(Wallpaper.this, "", c, true, false);
                    break;
                }
                
                case FINISH: {
                    closeProgressDialog();
                    setResult(RESULT_OK);
                    finish();
                    break;
                }
            }
        }
    };
    
    static class SetWallpaperThread extends Thread {
        private Bitmap mBitmap;
        private Handler mHandler;
        private Context mContext;
        
        public SetWallpaperThread(Bitmap bitmap, Handler handler, Context context) {
            mBitmap = bitmap;
            mHandler = handler;
            mContext = context;
        }
        
        @Override
        public void run() {
            try {
                mContext.setWallpaper(mBitmap);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to set wallpaper.", e);
            } finally {
                mHandler.sendEmptyMessage(FINISH);
            }
        }
    }
    
    private synchronized void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }        
    }
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) {
            mDoLaunch = icicle.getBoolean(sDoLaunchIcicle);
            mTempFilePath = icicle.getString(sTempFilePathIcicle);
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        icicle.putBoolean(sDoLaunchIcicle, mDoLaunch);
        icicle.putString(sTempFilePathIcicle, mTempFilePath);
    }
    
    @Override
    protected void onPause() {
        closeProgressDialog();
        super.onPause();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        if (!mDoLaunch) {
            return;
        }
        Uri imageToUse = getIntent().getData();
        if (imageToUse != null) {
            Intent intent = new Intent();
            intent.setClassName("com.android.camera", "com.android.camera.CropImage");
            intent.setData(imageToUse);
            formatIntent(intent);
            startActivityForResult(intent, CROP_DONE);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            formatIntent(intent);
            startActivityForResult(intent, PHOTO_PICKED);
        }
    }
    
    protected void formatIntent(Intent intent) {
        File f = getFileStreamPath("temp-wallpaper");
        (new File(f.getParent())).mkdirs();
        mTempFilePath = f.toString();
        f.delete();
        
        int width = getWallpaperDesiredMinimumWidth();
        int height = getWallpaperDesiredMinimumHeight();
        intent.putExtra("outputX",         width);
        intent.putExtra("outputY",         height);
        intent.putExtra("aspectX",         width);
        intent.putExtra("aspectY",         height);
        intent.putExtra("scale",           true);    
        intent.putExtra("noFaceDetection", true);
        intent.putExtra("output",          Uri.parse("file:/" + mTempFilePath));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == PHOTO_PICKED || requestCode == CROP_DONE) && (resultCode == RESULT_OK)
                && (data != null)) {
            try {
                InputStream s = new FileInputStream(mTempFilePath);
                Bitmap bitmap = BitmapFactory.decodeStream(s);
                if (bitmap == null) {
                    Log.e(LOG_TAG, "Failed to set wallpaper.  Couldn't get bitmap for path " + mTempFilePath);
                } else {
                    if (android.util.Config.LOGV)
                        Log.v(LOG_TAG, "bitmap size is " + bitmap.getWidth() + " / " + bitmap.getHeight());
                    mHandler.sendEmptyMessage(SHOW_PROGRESS);
                    new SetWallpaperThread(bitmap, mHandler, this).start();
                }
                mDoLaunch = false;
            } catch (FileNotFoundException ex) {
                
            } catch (IOException ex) {
                
            }
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
