/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.unittest;

import com.android.camera.Util;

import android.graphics.Matrix;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

@SmallTest
public class CameraUnitTest extends TestCase {
    public void testRoundOrientation() {
        int h = Util.ORIENTATION_HYSTERESIS;
        assertEquals(0, Util.roundOrientation(0, 0));
        assertEquals(0, Util.roundOrientation(359, 0));
        assertEquals(0, Util.roundOrientation(0 + 44 + h, 0));
        assertEquals(90, Util.roundOrientation(0 + 45 + h, 0));
        assertEquals(0, Util.roundOrientation(360 - 44 - h, 0));
        assertEquals(270, Util.roundOrientation(360 - 45 - h, 0));

        assertEquals(90, Util.roundOrientation(90, 90));
        assertEquals(90, Util.roundOrientation(90 + 44 + h, 90));
        assertEquals(180, Util.roundOrientation(90 + 45 + h, 90));
        assertEquals(90, Util.roundOrientation(90 - 44 - h, 90));
        assertEquals(0, Util.roundOrientation(90 - 45 - h, 90));

        assertEquals(180, Util.roundOrientation(180, 180));
        assertEquals(180, Util.roundOrientation(180 + 44 + h, 180));
        assertEquals(270, Util.roundOrientation(180 + 45 + h, 180));
        assertEquals(180, Util.roundOrientation(180 - 44 - h, 180));
        assertEquals(90, Util.roundOrientation(180 - 45 - h, 180));

        assertEquals(270, Util.roundOrientation(270, 270));
        assertEquals(270, Util.roundOrientation(270 + 44 + h, 270));
        assertEquals(0, Util.roundOrientation(270 + 45 + h, 270));
        assertEquals(270, Util.roundOrientation(270 - 44 - h, 270));
        assertEquals(180, Util.roundOrientation(270 - 45 - h, 270));

        assertEquals(90, Util.roundOrientation(90, 0));
        assertEquals(180, Util.roundOrientation(180, 0));
        assertEquals(270, Util.roundOrientation(270, 0));

        assertEquals(0, Util.roundOrientation(0, 90));
        assertEquals(180, Util.roundOrientation(180, 90));
        assertEquals(270, Util.roundOrientation(270, 90));

        assertEquals(0, Util.roundOrientation(0, 180));
        assertEquals(90, Util.roundOrientation(90, 180));
        assertEquals(270, Util.roundOrientation(270, 180));

        assertEquals(0, Util.roundOrientation(0, 270));
        assertEquals(90, Util.roundOrientation(90, 270));
        assertEquals(180, Util.roundOrientation(180, 270));
    }

    public void testPrepareMatrix() {
        Matrix matrix = new Matrix();
        float[] points;
        int[] expected;

        Util.prepareMatrix(matrix, false, 0, 800, 480);
        points = new float[] {-1000, -1000, 0, 0, 1000, 1000, 0, 1000, -750, 250};
        expected = new int[] {0, 0, 400, 240, 800, 480, 400, 480, 100, 300};
        matrix.mapPoints(points);
        assertEquals(expected, points);

        Util.prepareMatrix(matrix, false, 90, 800, 480);
        points = new float[] {-1000, -1000,   0,   0, 1000, 1000, 0, 1000, -750, 250};
        expected = new int[] {800, 0, 400, 240, 0, 480, 0, 240, 300, 60};
        matrix.mapPoints(points);
        assertEquals(expected, points);

        Util.prepareMatrix(matrix, false, 180, 800, 480);
        points = new float[] {-1000, -1000, 0, 0, 1000, 1000, 0, 1000, -750, 250};
        expected = new int[] {800, 480, 400, 240, 0, 0, 400, 0, 700, 180};
        matrix.mapPoints(points);
        assertEquals(expected, points);

        Util.prepareMatrix(matrix, true, 180, 800, 480);
        points = new float[] {-1000, -1000, 0, 0, 1000, 1000, 0, 1000, -750, 250};
        expected = new int[] {0, 480, 400, 240, 800, 0, 400, 0, 100, 180};
        matrix.mapPoints(points);
        assertEquals(expected, points);
    }

    private void assertEquals(int expected[], float[] actual) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Array index " + i + " mismatch", expected[i], Math.round(actual[i]));
        }
    }
}
