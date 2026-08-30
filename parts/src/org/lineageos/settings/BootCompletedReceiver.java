/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2020 The LineageOS Project
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

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.batterymonitor.BatteryMonitorService;
import org.lineageos.settings.batterymonitor.BatteryMonitorUtils;
import org.lineageos.settings.charge.ChargeEnforcementService;
import org.lineageos.settings.display.LcdFeaturesService;
import org.lineageos.settings.other.MglruUtils;
import org.lineageos.settings.power.PowerProfileTileService;
import org.lineageos.settings.thermal.ThermaldUtils;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.touchstrength.TouchStrengthUtils;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "XiaomiParts";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (DEBUG) Log.d(TAG, "Received boot completed intent: " + intent.getAction());
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        ThermalUtils.startService(context);
        context.startService(new Intent(context, LcdFeaturesService.class));

        // Restore Touch Strength if it was enabled
        TouchStrengthUtils.applySavedState(context);

        // Restart charge enforcement if a limiting mode was active
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt("saved_charge_mode", 0) > 0) {
            context.startService(new Intent(context, ChargeEnforcementService.class));
        }

        // Restore MGLRU state if it was enabled
        MglruUtils.applySavedState(context);

        // Re-apply Performance Mode (stop mi_thermald) if it was on
        ThermaldUtils.applySavedState(context);

        // Let the power profile tile reconcile the saved profile with the kernel
        TileService.requestListeningState(context,
                new ComponentName(context, PowerProfileTileService.class));

        // Start battery monitor if it was enabled
        if (prefs.getBoolean(BatteryMonitorUtils.PREF_ENABLED, false)) {
            context.startForegroundService(
                    new Intent(context, BatteryMonitorService.class));
        }
    }
}
