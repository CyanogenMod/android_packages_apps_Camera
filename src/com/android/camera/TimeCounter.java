/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.util.Log;

// A simple counter to measure the time to run a code fragment. Example:
//
// TimeCounter mDrawCounter = TimeCounter("Draw", 128);
// ...
// mDrawCounter.begin()
// // drawing code
// mDrawCounter.end()
//
// In this example, the counter will report (using Log.d) average time
// between begin() and end() every 128 times.
public class TimeCounter {
    private static final String TAG = "TimeCounter";
    private final String mName;
    private int mSamples;
    private final int mPeriod;

    // To avoid overflow, these values are in microseconds.
    private long mSum;
    private long mSumSquare;
    private long mBeginTime;
    private long mMax;
    private long mMin;

    public TimeCounter(String name, int period) {
        mName = name;
        mPeriod = period;
        reset();
    }

    public void reset() {
        mSamples = 0;
        mSum = 0;
        mSumSquare = 0;
        mMax = Long.MIN_VALUE;
        mMin = Long.MAX_VALUE;
    }

    public void begin() {
        mBeginTime = System.nanoTime();
    }

    public void end() {
        long delta = (System.nanoTime() - mBeginTime) / 1000;
        mSum += delta;
        mSumSquare += delta * delta;
        if (delta > mMax) mMax = delta;
        if (delta < mMin) mMin = delta;
        mSamples++;
        if (mSamples == mPeriod) {
            report();
            reset();
        }
    }

    public void report() {
        double avg = mSum / (double) mSamples;
        double stddev = Math.sqrt(mSumSquare / (double) mSamples - avg * avg);
        Log.d(TAG, "Counter " + mName
                + ": Avg = " + avg
                + ", StdDev = " + stddev
                + ", Max = " + mMax
                + ", Min = " + mMin);
    }
}
