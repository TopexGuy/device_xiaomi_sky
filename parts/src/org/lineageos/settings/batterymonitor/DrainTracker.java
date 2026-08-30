/*
 * Copyright (C) 2026 TopexGuy
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

package org.lineageos.settings.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class DrainTracker {

    private static final Object sLock = new Object();
    private static final String KEY_ACTIVE_DRAIN = "bm_active_drain";
    private static final String KEY_IDLE_DRAIN = "bm_idle_drain";
    private static final String KEY_CHARGE_SPEED = "bm_charge_speed";

    public static void recordSample(Context context, int level, boolean screenOn,
            boolean charging, int currentMa) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);

        // currentMa may carry a sign now; the drain/charge-speed magnitudes
        // are always positive.
        currentMa = Math.abs(currentMa);

        if (charging) {
            smoothValue(prefs, KEY_CHARGE_SPEED, currentMa);
        } else {
            float drain = BatteryMonitorUtils.drainFromCurrent(currentMa);
            String key = screenOn ? KEY_ACTIVE_DRAIN : KEY_IDLE_DRAIN;
            smoothValue(prefs, key, drain);
        }
    }

    private static void smoothValue(SharedPreferences prefs, String key, float value) {
        if (value <= 0) return;
        synchronized (sLock) {
            SharedPreferences.Editor editor = prefs.edit();
            float prev = prefs.getFloat(key, 0f);
            float smoothed = prev <= 0f ? value : prev * 0.7f + value * 0.3f;
            editor.putFloat(key, smoothed);
            editor.apply();
        }
    }

    public static float getActiveDrain(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getFloat(KEY_ACTIVE_DRAIN, 0f);
    }

    public static float getIdleDrain(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getFloat(KEY_IDLE_DRAIN, 0f);
    }

    public static float getChargeSpeed(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getFloat(KEY_CHARGE_SPEED, 0f);
    }

    public static void clearDrain(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove(KEY_ACTIVE_DRAIN)
                .remove(KEY_IDLE_DRAIN)
                .remove(KEY_CHARGE_SPEED)
                .apply();
    }
}