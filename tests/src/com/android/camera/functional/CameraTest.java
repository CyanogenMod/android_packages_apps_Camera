package com.android.camera.functional;

import com.android.camera.Camera;
import com.android.camera.R;
import com.android.camera.VideoCamera;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.KeyEvent;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class CameraTest extends InstrumentationTestCase {
    private static final String TAG = "CameraTest";

    @LargeTest
    public void testVideoCaptureIntentFdLeak() throws Exception {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.setClass(getInstrumentation().getTargetContext(), VideoCamera.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.parse("file://"
                + Environment.getExternalStorageDirectory().toString()
                + "test_fd_leak.3gp"));
        getInstrumentation().startActivitySync(intent).finish();
        // Test if the fd is closed.
        for (File f: new File("/proc/" + Process.myPid() + "/fd").listFiles()) {
            assertEquals(-1, f.getCanonicalPath().indexOf("test_fd_leak.3gp"));
        }
    }

    @LargeTest
    public void testActivityLeak() throws Exception {
        checkActivityLeak(Camera.class);
        checkActivityLeak(VideoCamera.class);
    }

    private void checkActivityLeak(Class<?> cls) throws Exception {
        final int TEST_COUNT = 5;
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(getInstrumentation().getTargetContext(), cls);
        ArrayList<WeakReference<Activity>> refs =
                new ArrayList<WeakReference<Activity>>();
        for (int i = 0; i < TEST_COUNT; i++) {
            Activity activity = getInstrumentation().startActivitySync(intent);
            refs.add(new WeakReference<Activity>(activity));
            activity.finish();
            getInstrumentation().waitForIdleSync();
            activity = null;
        }
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();
        Runtime.getRuntime().gc();
        int refCount = 0;
        for (WeakReference<Activity> c: refs) {
            if (c.get() != null) refCount++;
        }
        // If applications are leaking activity, every reference is reachable.
        assertTrue(refCount != TEST_COUNT);
    }

    @LargeTest
    public void testImageCaptureIntent() throws Exception {
        Instrumentation inst = getInstrumentation();
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        final Camera activity = (Camera) launchActivityWithIntent(
                inst.getTargetContext().getPackageName(), Camera.class,
                intent);

        // Take a picture
        inst.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS));
        inst.sendCharacterSync(KeyEvent.KEYCODE_CAMERA);
        Thread.sleep(4000);

        // Press done button.
        inst.runOnMainSync(new Runnable() {
                public void run() {
                    activity.findViewById(R.id.btn_done).performClick();
                }
        });

        assertTrue(activity.isFinishing());
        assertEquals(Activity.RESULT_OK, activity.getResultCode());
        Intent resultData = activity.getResultData();
        Bitmap bitmap = (Bitmap) resultData.getParcelableExtra("data");
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() > 0);
        assertTrue(bitmap.getHeight() > 0);
    }
}
