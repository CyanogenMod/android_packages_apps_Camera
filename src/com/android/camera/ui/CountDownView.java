/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera.ui;

import java.util.Locale;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.camera.R;

public class CountDownView extends FrameLayout {

    private static final String TAG = "CAM_CountDownView";
    private static final int SET_TIMER_TEXT = 1;
    private TextView mRemainingSecondsView;
    private int mRemainingSecs = 0;
    private OnCountDownFinishedListener mListener;
    private Animation mCountDownAnim;
    private SoundPool mSoundPool;
    private int mBeepTwice;
    private int mBeepOnce;
    private boolean mPlaySound;
    private final Handler mHandler = new MainHandler();

    public CountDownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mCountDownAnim = AnimationUtils.loadAnimation(context, R.anim.count_down_exit);
        // Load the beeps
        mSoundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
        mBeepOnce = mSoundPool.load(context, R.raw.beep_once, 1);
        mBeepTwice = mSoundPool.load(context, R.raw.beep_twice, 1);
    }

    public boolean isCountingDown() {
        return mRemainingSecs > 0;
    };

    public interface OnCountDownFinishedListener {
        public void onCountDownFinished();
    }

    private void remainingSecondsChanged(int newVal) {
        mRemainingSecs = newVal;
        if (newVal == 0) {
            // Countdown has finished
            setVisibility(View.INVISIBLE);
            mListener.onCountDownFinished();
        } else {
            Locale locale = getResources().getConfiguration().locale;
            String localizedValue = String.format(locale, "%d", newVal);
            mRemainingSecondsView.setText(localizedValue);
            // Fade-out animation
            mCountDownAnim.reset();
            mRemainingSecondsView.clearAnimation();
            mRemainingSecondsView.startAnimation(mCountDownAnim);

            // Play sound effect for the last 3 seconds of the countdown
            if (mPlaySound) {
                if (newVal == 1) {
                    mSoundPool.play(mBeepTwice, 1.0f, 1.0f, 0, 0, 1.0f);
                } else if (newVal <= 3) {
                    mSoundPool.play(mBeepOnce, 1.0f, 1.0f, 0, 0, 1.0f);
                }
            }
            // Schedule the next remainingSecondsChanged() call in 1 second
            mHandler.sendEmptyMessageDelayed(SET_TIMER_TEXT, 1000);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRemainingSecondsView = (TextView) findViewById(R.id.remaining_seconds);
    }

    public void setCountDownFinishedListener(OnCountDownFinishedListener listener) {
        mListener = listener;
    }

    public void startCountDown(int sec, boolean playSound) {
        if (sec <= 0) {
            Log.w(TAG, "Invalid input for countdown timer: " + sec + " seconds");
            return;
        }
        setVisibility(View.VISIBLE);
        mPlaySound = playSound;
        remainingSecondsChanged(sec);
    }

    public void cancelCountDown() {
        if (mRemainingSecs > 0) {
            mRemainingSecs = 0;
            mHandler.removeMessages(SET_TIMER_TEXT);
            setVisibility(View.INVISIBLE);
        }
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (message.what == SET_TIMER_TEXT) {
                remainingSecondsChanged(mRemainingSecs -1);
            }
        }
    }
}