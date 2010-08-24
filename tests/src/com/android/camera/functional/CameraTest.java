package com.android.camera.functional;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import java.io.File;

public class CameraTest extends InstrumentationTestCase {
    private static final String CAMERA_PACKAGE = "com.google.android.camera";
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
}
