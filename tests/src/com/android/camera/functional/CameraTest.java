package com.android.camera.functional;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class CameraTest extends InstrumentationTestCase {
    private static final String TAG = "CameraTest";
    private static final String CAMERA_PACKAGE = "com.google.android.camera";
    private static final String CAMERA_ACTIVITY = "com.android.camera.Camera";
    private static final String CAMCORDER_ACTIVITY = "com.android.camera.VideoCamera";

    @LargeTest
    public void testVideoCaptureIntentFdLeak() throws Exception {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.setClassName(CAMERA_PACKAGE, CAMCORDER_ACTIVITY);
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
        checkActivityLeak(CAMERA_ACTIVITY);
        checkActivityLeak(CAMCORDER_ACTIVITY);
    }

    private void checkActivityLeak(String activityName) throws Exception {
        final int TEST_COUNT = 5;
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(CAMERA_PACKAGE, activityName);
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
}
