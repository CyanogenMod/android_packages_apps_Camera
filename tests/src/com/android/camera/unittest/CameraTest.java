package com.android.camera.unittest;

import com.android.camera.Camera;

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

@SmallTest
public class CameraTest extends TestCase {
    public void testRoundOrientation() {
        assertEquals(0, Camera.roundOrientation(0));
        assertEquals(0, Camera.roundOrientation(0 + 44));
        assertEquals(90, Camera.roundOrientation(0 + 45));
        assertEquals(90, Camera.roundOrientation(90));
        assertEquals(90, Camera.roundOrientation(90 + 44));
        assertEquals(180, Camera.roundOrientation(90 + 45));
        assertEquals(180, Camera.roundOrientation(180));
        assertEquals(180, Camera.roundOrientation(180 + 44));
        assertEquals(270, Camera.roundOrientation(180 + 45));
        assertEquals(270, Camera.roundOrientation(270));
        assertEquals(270, Camera.roundOrientation(270 + 44));
        assertEquals(0, Camera.roundOrientation(270 + 45));
        assertEquals(0, Camera.roundOrientation(359));
    }
}
