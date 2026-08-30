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

package org.lineageos.settings.thermal;

import android.content.Context;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

/**
 * Disables thermals: stops the vendor thermal daemon (mi_thermald) to reduce
 * CPU throttling. As a privileged system app this controls the init service
 * through the ctl properties (ctl.stop / ctl.start), which the vendor
 * sepolicy of this device explicitly allows for system_app:
 *
 *   ctl.mi_thermald     u:object_r:ctl_mi_thermald:s0   (property_contexts)
 *   set_prop(system_app, ctl_mi_thermald)               (system_app.te)
 *
 * State is kept in this app's own SharedPreferences, so the QS tile, the
 * settings toggle and the boot-time restore can never drift apart.
 */
public final class ThermaldUtils {

    private static final String TAG = "ThermaldUtils";

    private static final String THERMALD_SERVICE = "mi_thermald";
    private static final String CTL_START = "ctl.start";
    private static final String CTL_STOP = "ctl.stop";
    private static final String SVC_STATE_PROP = "init.svc.mi_thermald";

    private static final int SVC_TIMEOUT_MS = 2000;
    private static final int SVC_POLL_MS = 50;

    public static final String PERF_MODE_ENABLE_KEY = "performance_mode_enable";

    private ThermaldUtils() {
    }

    /**
     * @return true if Disable Thermals (thermal management stopped) is on
     */
    public static boolean isPerformanceModeEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PERF_MODE_ENABLE_KEY, false);
    }

    /**
     * Re-asserts the persisted setting on boot: if Disable Thermals was on
     * when the device rebooted, mi_thermald (which init starts on boot /
     * charger) is stopped again. If it was off, nothing is done here — init
     * owns the normal thermald lifecycle.
     */
    public static void applySavedState(Context context) {
        if (!isPerformanceModeEnabled(context)) {
            return;
        }
        new Thread(() -> {
            boolean ok = applyEnabled(true);
            if (!ok) {
                Log.w(TAG, "Could not stop " + THERMALD_SERVICE + " on boot");
            }
        }, "thermals-boot").start();
    }

    /**
     * Turns Disable Thermals on (stops mi_thermald) or off (restarts it).
     *
     * @return true if the service reached the requested state
     */
    public static boolean applyEnabled(boolean enabled) {
        try {
            SystemProperties.set(enabled ? CTL_STOP : CTL_START, THERMALD_SERVICE);
            return waitForServiceState(enabled ? "stopped" : "running");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set ctl for " + THERMALD_SERVICE, e);
            return false;
        }
    }

    private static boolean waitForServiceState(String expected) {
        long deadline = SystemClock.elapsedRealtime() + SVC_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            String state = SystemProperties.get(SVC_STATE_PROP);
            if (expected.equals(state)) {
                return true;
            }
            try {
                Thread.sleep(SVC_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return expected.equals(SystemProperties.get(SVC_STATE_PROP));
    }
}