/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Intent;
import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Bundle;
import android.widget.TextView;


/**
 *
 */
public class ErrorScreen extends Activity
{
    int mError;
    boolean mLogoutOnExit;
    boolean mReconnectOnExit;
    Handler mHandler = new Handler();
    
    Runnable mCloseScreenCallback = new Runnable() {
        public void run() {
            finish();
        }               
    };
    
    @Override public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        resolveIntent();
        
        String errMsg = null;
        
        // PENDING: resourcify error messages!
        
        switch (mError) {
            default:
                errMsg = "You need to setup your Picassa Web account first.";
            break;
        }
        
        TextView tv = new TextView(this);
        tv.setText(errMsg);        
        setContentView(tv);
    }
    
    @Override public void onResume() {
        super.onResume();
        
        mHandler.postAtTime(mCloseScreenCallback,
                SystemClock.uptimeMillis() + 5000);

    } 
    
    @Override public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mCloseScreenCallback);        
//        startNextActivity();
    }
    
    void resolveIntent() {
        Intent intent = getIntent();        
        mError = intent.getIntExtra("error", mError);

        mLogoutOnExit = intent.getBooleanExtra("logout", mLogoutOnExit);
        mReconnectOnExit = intent.getBooleanExtra("reconnect", mReconnectOnExit);
    }
    
//    void startNextActivity() {
//        GTalkApp app = GTalkApp.getInstance();
//
//        if (mLogoutOnExit) {
//            app.logout();
//        }
//        else if (mReconnectOnExit) {
//            app.showLogin(false);
//        }
//    }
}
