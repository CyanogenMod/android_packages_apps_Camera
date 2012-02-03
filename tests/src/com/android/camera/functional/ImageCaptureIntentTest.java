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

import com.android.camera.Camera;
import com.android.camera.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

public class ImageCaptureIntentTest extends ActivityInstrumentationTestCase2 <Camera> {
    private Intent mIntent;

    public ImageCaptureIntentTest() {
        super(Camera.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    }

    @LargeTest
    public void testNoExtraOutput() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        takePicture();
        pressDone();

        assertTrue(getActivity().isFinishing());
        assertEquals(Activity.RESULT_OK, getActivity().getResultCode());
        Intent resultData = getActivity().getResultData();
        Bitmap bitmap = (Bitmap) resultData.getParcelableExtra("data");
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() > 0);
        assertTrue(bitmap.getHeight() > 0);
    }

    @LargeTest
    public void testExtraOutput() throws Exception {
        File file = new File(Environment.getExternalStorageDirectory(),
            "test.jpg");
        BufferedInputStream stream = null;
        byte[] jpegData;

        try {
            mIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
            setActivityIntent(mIntent);
            getActivity();

            takePicture();
            pressDone();

            assertTrue(getActivity().isFinishing());
            assertEquals(Activity.RESULT_OK, getActivity().getResultCode());

            // Verify the jpeg file
            int fileLength = (int) file.length();
            assertTrue(fileLength > 0);
            jpegData = new byte[fileLength];
            stream = new BufferedInputStream(new FileInputStream(file));
            stream.read(jpegData);
        } finally {
            if (stream != null) stream.close();
            file.delete();
        }

        Bitmap b = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        assertTrue(b.getWidth() > 0);
        assertTrue(b.getHeight() > 0);
    }

    @LargeTest
    public void testRetake() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        takePicture();
        pressRetake();
        takePicture();
        pressDone();

        assertTrue(getActivity().isFinishing());
        assertEquals(Activity.RESULT_OK, getActivity().getResultCode());
        Intent resultData = getActivity().getResultData();
        Bitmap bitmap = (Bitmap) resultData.getParcelableExtra("data");
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() > 0);
        assertTrue(bitmap.getHeight() > 0);
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
    public void testSnapshotCancel() throws Exception {
        setActivityIntent(mIntent);
        getActivity();

        takePicture();
        pressCancel();

        assertTrue(getActivity().isFinishing());
        assertEquals(Activity.RESULT_CANCELED, getActivity().getResultCode());
    }

    private void takePicture() throws Exception {
        getInstrumentation().sendKeySync(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS));
        getInstrumentation().sendCharacterSync(KeyEvent.KEYCODE_CAMERA);
        Thread.sleep(4000);
    }

    private void pressDone() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.btn_done).performClick();
            }
        });
    }

    private void pressRetake() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.btn_retake).performClick();
            }
        });
    }

    private void pressCancel() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.btn_cancel).performClick();
            }
        });
    }
}
