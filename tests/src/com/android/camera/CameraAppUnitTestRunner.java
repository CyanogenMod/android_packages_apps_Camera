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

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;
import com.android.camera.unit.*;

import junit.framework.TestSuite;

/**
 * Instrumentation Test Runner for all camera application unit tests.
 * Usage: adb shell am instrument -w -e package com.android.camera.tests \
 * com.android.camera.tests/android.test.InstrumentationTest
 */

public class CameraAppUnitTestRunner extends InstrumentationTestRunner {

    @Override
    public TestSuite getAllTests() {
        TestSuite suite = new InstrumentationTestSuite(this);
        suite.addTestSuite(CameraAppUnitTest.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return CameraAppUnitTestRunner.class.getClassLoader();
    }
}
