package com.android.camera;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class CameraBackupAgent extends BackupAgentHelper
{
    static final String SHARED_KEY = "shared_pref";

    public void onCreate () {
        addHelper(SHARED_KEY, new SharedPreferencesBackupHelper(this,
              CameraHolder.instance().getCameraNode()));
    }
}
