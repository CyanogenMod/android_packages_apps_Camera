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

package com.android.camera.functional;

import com.android.camera.VideoCamera;
import com.android.camera.R;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.provider.MediaStore.Video.VideoColumns;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.UiThreadTest;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;

public class VideoCaptureIntentTest extends ActivityInstrumentationTestCase2 <VideoCamera> {
    private static final String TAG = "VideoCaptureIntentTest";
    private Intent mIntent;
    private Uri mVideoUri;
    private File mFile, mFile2;

    public VideoCaptureIntentTest() {
        super(VideoCamera.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mVideoUri != null) {
            ContentResolver resolver = getActivity().getContentResolver();
            Uri query = mVideoUri.buildUpon().build();
            String[] projection = new String[] {VideoColumns.DATA};

            Cursor cursor = null;
            try {
                cursor = resolver.query(query, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    new File(cursor.getString(0)).delete();
                }
            } finally {
                if (cursor != null) cursor.close();
            }

            resolver.delete(mVideoUri, null, null);
        }
        if (mFile != null) mFile.delete();
        if (mFile2 != null) mFile2.delete();
        super.tearDown();
    }

    @LargeTest
    public void testNoExtraOutput() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        recordVideo();
        pressDone();

        Intent resultData = getActivity().getResultData();
        mVideoUri = resultData.getData();
        assertNotNull(mVideoUri);
        verify(getActivity(), mVideoUri);
    }

    @LargeTest
    public void testExtraOutput() throws Exception {
        mFile = new File(Environment.getExternalStorageDirectory(), "video.tmp");

        Uri uri = Uri.fromFile(mFile);
        mIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        setActivityIntent(mIntent);
        getActivity();

        recordVideo();
        pressDone();

        verify(getActivity(), uri);
    }

    @LargeTest
    public void testRetake() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        recordVideo();
        pressRetake();
        recordVideo();
        pressDone();

        Intent resultData = getActivity().getResultData();
        mVideoUri = resultData.getData();
        assertNotNull(mVideoUri);
        verify(getActivity(), mVideoUri);
    }

    @LargeTest
    public void testCancel() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        pressCancel();

        assertTrue(getActivity().isFinishing());
        assertEquals(Activity.RESULT_CANCELED, getActivity().getResultCode());
    }

    @LargeTest
    public void testRecordCancel() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        recordVideo();
        pressCancel();

        assertTrue(getActivity().isFinishing());
        assertEquals(Activity.RESULT_CANCELED, getActivity().getResultCode());
    }

    @LargeTest
    public void testExtraSizeLimit() throws Exception {
        mFile = new File(Environment.getExternalStorageDirectory(), "video.tmp");
        final long sizeLimit = 500000;  // bytes

        Uri uri = Uri.fromFile(mFile);
        mIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        mIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, sizeLimit);
        mIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);  // use low quality to speed up
        setActivityIntent(mIntent);
        getActivity();

        recordVideo(5000);
        pressDone();

        verify(getActivity(), uri);
        long length = mFile.length();
        Log.v(TAG, "Video size is " + length + " bytes.");
        assertTrue(length > 0);
        assertTrue("Actual size=" + length, length <= sizeLimit);
    }

    @LargeTest
    public void testExtraDurationLimit() throws Exception {
        mFile = new File(Environment.getExternalStorageDirectory(), "video.tmp");
        final int durationLimit = 2;  // seconds

        Uri uri = Uri.fromFile(mFile);
        mIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        mIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, durationLimit);
        setActivityIntent(mIntent);
        getActivity();

        recordVideo(5000);
        pressDone();

        int duration = verify(getActivity(), uri);
        // The duraion should be close to to the limit. The last video duration
        // also has duration, so the total duration may exceeds the limit a
        // little bit.
        Log.v(TAG, "Video length is " + duration + " ms.");
        assertTrue(duration  < (durationLimit + 1) * 1000);
    }

    @LargeTest
    public void testExtraVideoQuality() throws Exception {
        mFile = new File(Environment.getExternalStorageDirectory(), "video.tmp");
        mFile2 = new File(Environment.getExternalStorageDirectory(), "video2.tmp");

        Uri uri = Uri.fromFile(mFile);
        mIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        mIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);  // low quality
        setActivityIntent(mIntent);
        getActivity();

        recordVideo();
        pressDone();

        verify(getActivity(), uri);
        setActivity(null);

        uri = Uri.fromFile(mFile2);
        mIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        mIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);  // high quality
        setActivityIntent(mIntent);
        getActivity();

        recordVideo();
        pressDone();

        verify(getActivity(), uri);
        assertTrue(mFile.length() <= mFile2.length());
    }

    // Verify result code, result data, and the duration.
    private int verify(VideoCamera activity, Uri uri) throws Exception {
        assertTrue(activity.isFinishing());
        assertEquals(Activity.RESULT_OK, activity.getResultCode());

        // Verify the video file
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(activity, uri);
        String duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
        assertNotNull(duration);
        int durationValue = Integer.parseInt(duration);
        Log.v(TAG, "Video duration is " + durationValue);
        assertTrue(durationValue > 0);
        return durationValue;
    }

    private void recordVideo(int ms) throws Exception {
        getInstrumentation().sendCharacterSync(KeyEvent.KEYCODE_CAMERA);
        Thread.sleep(ms);
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                // If recording is in progress, stop it. Run these atomically in
                // UI thread.
                if (getActivity().isRecording()) {
                    getActivity().findViewById(R.id.shutter_button).performClick();
                }
            }
        });
    }

    private void recordVideo() throws Exception {
        recordVideo(2000);
    }

    private void pressDone() {
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                getActivity().findViewById(R.id.btn_done).performClick();
            }
        });
    }

    private void pressRetake() {
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                getActivity().findViewById(R.id.btn_retake).performClick();
            }
        });
    }

    private void pressCancel() {
        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                getActivity().findViewById(R.id.btn_cancel).performClick();
            }
        });
    }
}
