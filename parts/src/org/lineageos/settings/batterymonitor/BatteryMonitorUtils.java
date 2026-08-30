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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.lineageos.settings.utils.FileUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class BatteryMonitorUtils {

    private static final String TAG = "BatteryMonitorUtils";
    private static final String CURRENT_NOW_PATH =
            "/sys/class/power_supply/battery/current_now";

    public static final String PREF_ENABLED = "battery_monitor_enabled";
    public static final String PREF_SHOW_CURRENT = "bm_show_current";
    public static final String PREF_SHOW_DRAIN = "bm_show_drain";
    public static final String PREF_SHOW_TEMP = "bm_show_temp";
    public static final String PREF_SHOW_VOLTAGE = "bm_show_voltage";
    public static final String PREF_SHOW_HEALTH = "bm_show_health";
    public static final String PREF_SHOW_SCREEN = "bm_show_screen";
    public static final String PREF_SHOW_RAM = "bm_show_ram";
    public static final String PREF_SHOW_UPTIME = "bm_show_uptime";
    public static final String PREF_POLL_INTERVAL = "bm_poll_interval";

    public static final int DEFAULT_POLL_MS = 7_000;
    public static final int DEFAULT_CAPACITY_MA = 5000;

    public static final int[] POLL_INTERVALS = {3000, 5000, 7000, 10000, 30000, 60000};
    public static final String[] POLL_INTERVAL_LABELS = {
            "3s", "5s", "7s", "10s", "30s", "60s"
    };

    public static class BatteryStats {
        public int level;
        public int currentMa;
        public float temperature;
        public int voltageMv;
        public int health;
        public long screenOnMs;
        public long screenOffMs;
        public long awakeMs;
        public long deepSleepMs;
        public long ramUsedMb;
        public long ramTotalMb;
        public long uptimeMs;
        public long elapsedRealtimeMs;
        public long elapsedBaseMs;
        public boolean isCharging;
        public boolean isScreenOn;
    }

    public static BatteryStats collect(Context context) {
        BatteryStats stats = new BatteryStats();

        PowerManager pm = (PowerManager)
                context.getSystemService(Context.POWER_SERVICE);
        stats.isScreenOn = pm != null && pm.isInteractive();

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = context.registerReceiver(null, filter);
        if (battery != null) {
            stats.level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (scale != 100) stats.level = stats.level * 100 / scale;

            stats.temperature = battery.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f;
            stats.voltageMv = battery.getIntExtra(
                    BatteryManager.EXTRA_VOLTAGE, 0);
            stats.health = battery.getIntExtra(
                    BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);

            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            stats.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        stats.currentMa = readCurrentNow();
        if (stats.currentMa != 0) {
            // Some drivers report current_now inverted (negative for
            // charging). Normalize so positive == charging, negative ==
            // discharging regardless of hardware convention.
            if (stats.isCharging && stats.currentMa < 0) {
                stats.currentMa = -stats.currentMa;
            } else if (!stats.isCharging && stats.currentMa > 0) {
                stats.currentMa = -stats.currentMa;
            }
        }

        ActivityManager am = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(memInfo);
            stats.ramTotalMb = memInfo.totalMem / (1024 * 1024);
            stats.ramUsedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024);
        }

        stats.uptimeMs = readUptime();
        stats.elapsedRealtimeMs = SystemClock.elapsedRealtime();

        BatteryMonitorService svc = BatteryMonitorService.getInstance();
        if (svc != null) {
            stats.screenOnMs = svc.getScreenOnMs();
            stats.screenOffMs = svc.getScreenOffMs();
            stats.deepSleepMs = svc.getDeepSleepMs();
            stats.awakeMs = svc.getAwakeMs();
            // The screen-on/off totals survive reboots, but the session
            // elapsed baseline restarts, so base the percentage on the
            // larger (never less than the accumulated totals).
            stats.elapsedBaseMs = Math.max(svc.getElapsedSinceStartMs(),
                    stats.screenOnMs + stats.screenOffMs);
        } else {
            stats.elapsedBaseMs = stats.elapsedRealtimeMs;
        }

        return stats;
    }

    private static long readUptime() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        new java.io.FileInputStream("/proc/uptime")))) {
            String line = r.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                float seconds = Float.parseFloat(parts[0]);
                return (long) (seconds * 1000);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read /proc/uptime", e);
        }
        return SystemClock.elapsedRealtime();
    }

    private static int readCurrentNow() {
        // Keep the sign — positive when charging, negative when discharging —
        // so the UI can show the correct +A / -A direction. The magnitude is
        // normalized against the reported battery status in collect().
        int current = readIntNode(CURRENT_NOW_PATH);
        return current / 1000;
    }

    private static int readIntNode(String path) {
        if (!FileUtils.isFileReadable(path)) return 0;
        String val = FileUtils.readOneLine(path);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int sCachedCapacity = -1;

    public static int getBatteryCapacityMa() {
        if (sCachedCapacity > 0) return sCachedCapacity;
        int chargeFull = readIntNode(
                "/sys/class/power_supply/battery/charge_full");
        if (chargeFull <= 0) {
            chargeFull = readIntNode(
                    "/sys/class/power_supply/battery/charge_full_design");
        }
        if (chargeFull > 0) {
            sCachedCapacity = chargeFull / 1000;
        }
        return sCachedCapacity > 0 ? sCachedCapacity : DEFAULT_CAPACITY_MA;
    }

    public static float drainFromCurrent(int currentMa) {
        int capacity = getBatteryCapacityMa();
        if (capacity <= 0 || currentMa <= 0) return 0;
        return (currentMa * 100.0f) / capacity;
    }

    public static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long mins = (totalSec % 3600) / 60;
        long secs = totalSec % 60;
        if (hours > 0) return hours + "h " + mins + "m";
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }

    public static String formatDurationLong(long ms) {
        long totalSec = ms / 1000;
        long days = totalSec / 86400;
        long hours = (totalSec % 86400) / 3600;
        long mins = (totalSec % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    public static String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    public static float calculateTimeToFull(int currentLevel, int currentMa) {
        if (currentMa <= 0) return -1;
        int remaining = 100 - currentLevel;
        if (remaining <= 0) return 0;
        int capacity = getBatteryCapacityMa();
        if (capacity <= 0) return -1;
        return (remaining * capacity) / (currentMa * 100.0f);
    }

    public static float calculateTimeRemaining(int level, float activeDrain) {
        if (activeDrain <= 0) return -1;
        return level / activeDrain;
    }

    public static String formatTimeRemaining(float hours) {
        if (hours < 0) return "Unknown";
        int totalMins = Math.round(hours * 60);
        int d = totalMins / 1440;
        int h = (totalMins % 1440) / 60;
        int m = totalMins % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    public static float calculatePercentage(long part, long total) {
        if (total <= 0) return 0;
        return (part * 100.0f) / total;
    }

    public static String formatTimeToFull(float hours) {
        if (hours < 0) return "Unknown";
        if (hours < 0.01f) return "Full";
        int totalMins = Math.round(hours * 60);
        int h = totalMins / 60;
        int m = totalMins % 60;
        if (h > 0) return "~" + h + "h " + m + "m";
        return "~" + m + "m";
    }
}
