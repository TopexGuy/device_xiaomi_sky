/*
 * Copyright (C) 2020 The LineageOS Project
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

package org.lineageos.settings.thermal;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class ThermalService extends Service {

    private static final String TAG = "ThermalService";
    private static final boolean DEBUG = false;

    private volatile String mPreviousApp;
    private ThermalUtils mThermalUtils;

    private IActivityTaskManager mActivityTaskManager;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPreviousApp = "";
            mThermalUtils.setDefaultThermalProfile();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "Creating service");
        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (Exception e) {
            Log.w(TAG, "Failed to register task stack listener", e);
        }
        mThermalUtils = new ThermalUtils(this);
        registerReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskListener);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister task stack listener", e);
        }
        try {
            unregisterReceiver(mIntentReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        this.registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private final TaskStackListener mTaskListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            if (mActivityTaskManager == null) return;
            try {
                final RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
                if (info == null || info.topActivity == null) {
                    return;
                }

                String foregroundApp = info.topActivity.getPackageName();
                if (!foregroundApp.equals(mPreviousApp)) {
                    mThermalUtils.setThermalProfile(foregroundApp);
                    mPreviousApp = foregroundApp;
                }
            } catch (Exception e) {}
        }
    };
}
