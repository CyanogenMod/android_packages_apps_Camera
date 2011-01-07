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

package com.android.camera.stress;

import android.os.Environment;
import java.io.FileWriter;
import java.io.BufferedWriter;


/**
 * Collection of utility functions used for the test.
 */
public class TestUtil {
    public BufferedWriter mOut;
    public FileWriter mfstream;

    public TestUtil() {
    }

    public void prepareOutputFile() throws Exception {
        String camera_test_output_file =
                Environment.getExternalStorageDirectory().toString() + "/mediaStressOut.txt";
        mfstream = new FileWriter(camera_test_output_file, true);
        mOut = new BufferedWriter(mfstream);
    }

    public void closeOutputFile() throws Exception {
        mOut.write("\n");
        mOut.close();
        mfstream.close();
    }

    public void writeReportHeader(String reportTag, int iteration) throws Exception {
        mOut.write(reportTag);
        mOut.write("No of loops :" + iteration + "\n");
        mOut.write("loop: ");
    }

    public void writeResult(int iteration) throws Exception {
        mOut.write(" ," + iteration);
        mOut.flush();
    }
}
