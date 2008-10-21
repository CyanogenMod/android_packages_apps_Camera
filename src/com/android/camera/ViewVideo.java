/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.media.MediaPlayer;
import android.app.Activity;
import android.os.Bundle;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.MediaController;
import android.view.Window;
import android.widget.VideoView;
import android.util.Config;

class ViewVideo extends Activity
{
    static final String TAG = "ViewVideo";

    private ImageManager.IImageList mAllVideos;
    private PowerManager.WakeLock   mWakeLock;
    private ContentResolver         mContentResolver;
    private VideoView               mVideoView;
    private ImageManager.IImage     mVideo;
    private int                     mCurrentPosition = -1;
    private MediaController         mMediaController;

    // if the activity gets paused the stash the current position here
    int mPausedPlaybackPosition = 0;


    public ViewVideo()
    {
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        if (Config.LOGV)
            Log.v(TAG, "onCreate");
        //getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);

        mContentResolver = getContentResolver();

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        setContentView(R.layout.viewvideo);

        mMediaController = new MediaController(this);
        mVideoView = (VideoView) findViewById(R.id.video);
        mVideoView.setMediaController(mMediaController);
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                // TODO what do we really want to do at the end of playback?
                finish();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        if (Config.LOGV)
            Log.v(TAG, "onSaveInstanceState");
        b.putInt("playback_position", mPausedPlaybackPosition);
    }

    @Override
    public void onRestoreInstanceState(Bundle b) {
        if (Config.LOGV)
            Log.v(TAG, "onRestoreInstanceState");
        mPausedPlaybackPosition = b.getInt("playback_position", 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Config.LOGV)
            Log.v(TAG, "onPause");
        mAllVideos.deactivate();

        mVideoView.pause();
        mPausedPlaybackPosition = mVideoView.getCurrentPosition();
        mVideoView.setVideoURI(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Config.LOGV)
            Log.v(TAG, "onStop");
    }

    @Override
    public void onResume()
    {
    	super.onResume();
        if (Config.LOGV)
            Log.v(TAG, "onResume");

        mAllVideos = ImageManager.instance().allImages(
                ViewVideo.this,
                mContentResolver,
                ImageManager.DataLocation.ALL,
                ImageManager.INCLUDE_VIDEOS,
                ImageManager.SORT_DESCENDING);

        // TODO smarter/faster here please
        Uri uri = getIntent().getData();
        if (mVideo == null) {
            for (int i = 0; i < mAllVideos.getCount(); i++) {
                ImageManager.IImage video = mAllVideos.getImageAt(i);
                if (video.fullSizeImageUri().equals(uri)) {
                    mCurrentPosition = i;
                    mVideo = video;
                    break;
                }
            }
        }

        if (mCurrentPosition != -1) {
            mMediaController.setPrevNextListeners(
                new android.view.View.OnClickListener() {
                    public void onClick(View v) {
                        if (++mCurrentPosition == mAllVideos.getCount())
                            mCurrentPosition = 0;
                        ImageManager.IImage video = mAllVideos.getImageAt(mCurrentPosition);
                        mVideo = video;
                        mVideoView.setVideoURI(video.fullSizeImageUri());
                        mVideoView.start();
                    }
                },
                new android.view.View.OnClickListener() {
                    public void onClick(View v) {
                        if (--mCurrentPosition == -1)
                            mCurrentPosition = mAllVideos.getCount() - 1;
                        ImageManager.IImage video = mAllVideos.getImageAt(mCurrentPosition);
                        mVideo = video;
                        mVideoView.setVideoURI(video.fullSizeImageUri());
                        mVideoView.start();
                    }
                });
        }
        if (Config.LOGV)
            android.util.Log.v("camera", "seekTo " + mPausedPlaybackPosition);
        mVideoView.setVideoURI(uri);
        mVideoView.seekTo(mPausedPlaybackPosition);
        mVideoView.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        MenuHelper.addVideoMenuItems(
                menu,
                MenuHelper.INCLUDE_ALL & ~MenuHelper.INCLUDE_VIEWPLAY_MENU,
                ViewVideo.this,       // activity
                null,       // handler
                new SelectedImageGetter() {
                    public ImageManager.IImage getCurrentImage() {
                        return mVideo;
                    }
                    public Uri getCurrentImageUri() {
                        return mVideo.fullSizeImageUri();
                    }
                },

                // deletion case
                new Runnable() {
                    public void run() {
                        mAllVideos.removeImage(mVideo);
                        finish();
                    }
                },

                // pre-work
                null,

                // post-work
                null);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        return super.onPrepareOptionsMenu(menu);
    }
}
