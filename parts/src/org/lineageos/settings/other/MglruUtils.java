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

package org.lineageos.settings.other;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

import java.math.BigInteger;

/**
 * Multi-Gen LRU toggle. The node is chowned to the system user and allowed
 * for system_app in sepolicy (see vendor/genfs_contexts, vendor/file.te and
 * vendor/system_app.te), so the write goes through FileUtils like every other
 * sysfs feature in this app — no root required.
 */
public final class MglruUtils {

    private static final String TAG = "MglruUtils";

    private static final String MGLRU_FILE =
            "/sys/kernel/mm/lru_gen/enabled";

    public static final String MGLRU_ENABLE_KEY = "mglru_enable";
    public static final String MGLRU_CONFIGURED_KEY = "mglru_configured";

    private MglruUtils() {
    }

    /**
     * @return true if the kernel currently has MGLRU enabled
     */
    public static boolean isEnabled() {
        return isEnabledValue(FileUtils.readOneLine(MGLRU_FILE));
    }

    /**
     * Applies the persisted MGLRU setting. Called on boot and whenever the
     * toggle is used from the settings screen.
     *
     * <p>If the user {code never touched} the toggle in the app, this does
     * nothing — whatever the kernel default / a root script configured is
     * left alone. The app only overrides the node on reboot once the user
     * has explicitly toggled it here, so MGLRU enabled from root does not
     * get disabled behind their back.
     */
    public static void applySavedState(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(MGLRU_CONFIGURED_KEY, false)) {
            return;
        }
        applyEnabled(prefs.getBoolean(MGLRU_ENABLE_KEY, false));
    }

    /**
     * Enables or disables MGLRU.
     *
     * @return true if the file was written successfully
     */
    public static boolean applyEnabled(boolean enabled) {
        boolean ok = FileUtils.writeLine(MGLRU_FILE,
                enabled ? "yes" : "no");
        if (!ok) {
            Log.w(TAG, "Failed to write " + MGLRU_FILE);
        }
        return ok;
    }

    /**
     * Parses the lru_gen enabled node. The value may come back as yes/no or
     * as a bitmask (decimal or 0x hex); any non-zero value counts as enabled.
     */
    private static boolean isEnabledValue(String value) {
        if (value == null) return false;
        String t = value.trim().toLowerCase();
        if (t.isEmpty()) return false;
        if ("y".equals(t) || "yes".equals(t)) return true;
        if ("n".equals(t) || "no".equals(t)) return false;
        try {
            BigInteger v;
            if (t.startsWith("0x")) {
                v = new BigInteger(t.substring(2), 16);
            } else {
                v = new BigInteger(t, 10);
            }
            return v.signum() != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}