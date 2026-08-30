/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *               2026 TopexGuy
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

package org.lineageos.settings.touchstrength;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

/**
 * Writes/reads the touch strength sysfs bridge exposed by the kernel's
 * xiaomi_touch driver. The bridge (up_threshold/tolerance nodes) forwards to
 * whichever panel driver is registered - NT36672C or FT8720 - so this works
 * regardless of panel variant.
 */
public final class TouchStrengthUtils {

    private static final String TAG = "TouchStrengthUtils";

    public static final String UP_THRESHOLD_FILE =
            "/sys/devices/virtual/touch/touch_dev/up_threshold";
    public static final String TOLERANCE_FILE =
            "/sys/devices/virtual/touch/touch_dev/tolerance";

    public static final String TS_ENABLE_KEY = "touch_strength_enable";
    public static final String TS_THRESHOLD_KEY = "touch_strength_threshold";
    public static final String TS_TOLERANCE_KEY = "touch_strength_tolerance";

    public static boolean writeUpThreshold(int value) {
        boolean success = FileUtils.writeLine(UP_THRESHOLD_FILE, String.valueOf(value));
        if (!success) {
            Log.e(TAG, "Failed to write touch up threshold: " + value);
        }
        return success;
    }

    public static boolean writeTolerance(int value) {
        boolean success = FileUtils.writeLine(TOLERANCE_FILE, String.valueOf(value));
        if (!success) {
            Log.e(TAG, "Failed to write touch tolerance: " + value);
        }
        return success;
    }

    public static int readUpThreshold() {
        String value = FileUtils.readOneLine(UP_THRESHOLD_FILE);
        return parseValue(value);
    }

    public static int readTolerance() {
        String value = FileUtils.readOneLine(TOLERANCE_FILE);
        return parseValue(value);
    }

    private static int parseValue(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(TS_ENABLE_KEY, false);
    }

    public static int getSavedUpThreshold(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(TS_THRESHOLD_KEY, 0);
    }

    public static int getSavedTolerance(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(TS_TOLERANCE_KEY, 0);
    }

    public static boolean isAvailable() {
        return FileUtils.isFileWritable(UP_THRESHOLD_FILE)
                && FileUtils.isFileWritable(TOLERANCE_FILE);
    }

    /**
     * Applies the persisted touch strength settings to the kernel bridge.
     * Called on boot and whenever the master switch changes.
     *
     * @return false if the kernel bridge is unavailable, true otherwise
     */
    public static boolean applySavedState(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(TS_ENABLE_KEY, false)) {
            boolean ok = writeUpThreshold(prefs.getInt(TS_THRESHOLD_KEY, 0));
            ok &= writeTolerance(prefs.getInt(TS_TOLERANCE_KEY, 0));
            return ok;
        } else {
            writeUpThreshold(0);
            writeTolerance(0);
            return true;
        }
    }
}
