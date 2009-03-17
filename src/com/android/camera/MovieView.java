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
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.view.View;
import android.widget.MediaController;
import android.widget.VideoView;

public class MovieView extends Activity implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener
{
    private static final String TAG = "MovieView";
    // Copied from MediaPlaybackService in the Music Player app. Should be public, but isn't.
    private static final String SERVICECMD = "com.android.music.musicservicecommand";
    private static final String CMDNAME = "command";
    private static final String CMDPAUSE = "pause";

    private VideoView   mVideoView;
    private View        mProgressView;
    private boolean		mFinishOnCompletion;
    private Uri			mUri;

    // State maintained for proper onPause/OnResume behaviour.
    private int mPositionWhenPaused = -1;
    private boolean mWasPlayingWhenPaused = false;

    public MovieView()
    {
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        setContentView(R.layout.movie_view);

        mVideoView = (VideoView) findViewById(R.id.surface_view);
        mProgressView = findViewById(R.id.progress_indicator);
        Intent intent = getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION)) {
            int orientation = intent.getIntExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (orientation != getRequestedOrientation()) {
                setRequestedOrientation(orientation);
            }
        }
        mFinishOnCompletion = intent.getBooleanExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
        mUri = intent.getData();

        // For streams that we expect to be slow to start up, show a
        // progress spinner until playback starts.
        String scheme = mUri.getScheme();
        if ("http".equalsIgnoreCase(scheme) ||
                "rtsp".equalsIgnoreCase(scheme)) {
            mHandler.postDelayed(mPlayingChecker, 250);
        } else {
            mProgressView.setVisibility(View.GONE);
        }

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setVideoURI(mUri);
        mVideoView.setMediaController(new MediaController(this));
        mVideoView.requestFocus(); // make the video view handle keys for seeking and pausing

        Intent i = new Intent(SERVICECMD);
        i.putExtra(CMDNAME, CMDPAUSE);
        sendBroadcast(i);
        {
            final Integer bookmark = getBookmark();
            if (bookmark != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.resume_playing_title);
                builder.setMessage(String.format(
                        getString(R.string.resume_playing_message),
                        MenuHelper.formatDuration(this, bookmark)));
                builder.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }});
                builder.setPositiveButton(R.string.resume_playing_resume,
                        new OnClickListener(){
                    public void onClick(DialogInterface dialog, int which) {
                        mVideoView.seekTo(bookmark);
                        mVideoView.start();
                    }});
                builder.setNegativeButton(R.string.resume_playing_restart, new OnClickListener(){
                    public void onClick(DialogInterface dialog, int which) {
                        mVideoView.start();
                    }});
                builder.show();
            } else {
                mVideoView.start();
            }
        }
    }

    private static boolean uriSupportsBookmarks(Uri uri) {
        String scheme = uri.getScheme();
        String authority = uri.getAuthority();
        return ("content".equalsIgnoreCase(scheme)
                && MediaStore.AUTHORITY.equalsIgnoreCase(authority));
    }
    
    private Integer getBookmark() {
        if (!uriSupportsBookmarks(mUri)) {
            return null;
        }
        
        String[] projection = new String[]{Video.VideoColumns.DURATION,
                Video.VideoColumns.BOOKMARK};
        try {
            Cursor cursor = getContentResolver().query(mUri, projection, null, null, null);
            if (cursor != null) {
                try {
                    if ( cursor.moveToFirst() ) {
                        int duration = getCursorInteger(cursor, 0);
                        int bookmark = getCursorInteger(cursor, 1);
                        final int ONE_MINUTE = 60 * 1000;
                        final int TWO_MINUTES = 2 * ONE_MINUTE;
                        final int FIVE_MINUTES = 5 * ONE_MINUTE;
                        if ((bookmark < TWO_MINUTES)
                                || (duration < FIVE_MINUTES)
                                || (bookmark > (duration - ONE_MINUTE))) {
                            return null;
                        }

                        return new Integer(bookmark);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (SQLiteException e) {
            // ignore
        }

        return null;
    }

    private int getCursorInteger(Cursor cursor, int index) {
        try {
            return cursor.getInt(index);
        } catch (SQLiteException e) {
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }

    }

    private void setBookmark(int bookmark) {
        if (!uriSupportsBookmarks(mUri)) {
            return;
        }
        
        ContentValues values = new ContentValues();
        values.put(Video.VideoColumns.BOOKMARK, Integer.toString(bookmark));
        try {
            getContentResolver().update(mUri, values, null, null);
        } catch (SecurityException ex) {
            // Ignore, can happen if we try to set the bookmark on a read-only resource
            // such as a video attached to GMail.
        } catch (SQLiteException e) {
            // ignore. can happen if the content doesn't support a bookmark column.
        } catch (UnsupportedOperationException e) {
            // ignore. can happen if the external volume is already detached.
        }
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacksAndMessages(null);
        setBookmark(mVideoView.getCurrentPosition());

        mPositionWhenPaused = mVideoView.getCurrentPosition();
        mWasPlayingWhenPaused = mVideoView.isPlaying();
        mVideoView.stopPlayback();

        super.onPause();
    }

    @Override
    public void onResume() {
        if (mPositionWhenPaused >= 0) {
            mVideoView.setVideoURI(mUri);
            mVideoView.seekTo(mPositionWhenPaused);
            if (mWasPlayingWhenPaused) {
                mVideoView.start();
            }
        }

        super.onResume();
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    Runnable mPlayingChecker = new Runnable() {
        public void run() {
            if (mVideoView.isPlaying()) {
                mProgressView.setVisibility(View.GONE);
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        mHandler.removeCallbacksAndMessages(null);
        mProgressView.setVisibility(View.GONE);
        return false;
    }

    public void onCompletion(MediaPlayer mp) {
        if (mFinishOnCompletion) {
            finish();
        }
    }
}
