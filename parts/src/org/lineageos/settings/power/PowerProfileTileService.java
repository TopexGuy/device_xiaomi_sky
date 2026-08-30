/*
 * Copyright (C) 2024 Paranoid Android
 *               2026 TopexGuy
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.power;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

/**
 * Power profile QS tile with REAL CPU control.
 *
 * CPU clusters (Sky / Snapdragon 680):
 *   Little (cpu0-3): max 1958400 KHz
 *   Big    (cpu4-6): max 1958400 KHz
 *   Prime  (cpu7):   max 2208000 KHz
 *
 * Profiles:
 *   DEFAULT    -> restore stock governor, full freq, sconfig 500
 *   SCHEDUTIL  -> schedutil governor, 90% freq cap, sconfig 500, no saver
 *   GAMING     -> performance governor, full freq, sconfig 520
 *   BATTERY    -> walt governor, 80% freq cap, sconfig 500, battery saver
 *   EXTREME    -> powersave governor, 50% freq cap, sconfig 500, extreme saver
 *
 * Frequency caps are computed from the live scaling_max_freq value instead of
 * hardcoded table values, so they follow the actual cluster topology. The
 * stock governor is snapshotted once per core and restored by DEFAULT.
 */
public class PowerProfileTileService extends TileService {
    private static final String TAG = "PowerProfileTileService";
    private static final String POWER_PROFILE_PATH =
            "/sys/class/thermal/thermal_message/sconfig";
    private static final String POWER_ENABLED_KEY = "power_enabled";
    private static final String POWER_PROFILE_PREF_KEY = "saved_power_profile";
    private static final String ORIG_GOVERNOR_PREFIX = "power_orig_governor_";
    private static final String ORIG_MAX_FREQ_PREFIX = "power_orig_maxfreq_";
    private static final int NOTIFICATION_ID_PERFORMANCE = 1001;

    private static final String CPU_BASE = "/sys/devices/system/cpu/";
    private static final String[] CPU_REPS = {"cpu0", "cpu4", "cpu7"};

    enum PowerProfile {
        DEFAULT(R.string.powerprofile_default,
                R.drawable.ic_power_default, "500",
                "walt", 100),
        SCHEDUTIL(R.string.powerprofile_schedutil,
                R.drawable.ic_power_balanced, "500",
                "schedutil", 90),
        GAMING(R.string.powerprofile_gaming,
                R.drawable.ic_power_gaming, "520",
                "performance", 100),
        BATTERY(R.string.powerprofile_battery,
                R.drawable.ic_power_battery_saver, "500",
                "walt", 80),
        EXTREME(R.string.powerprofile_extreme,
                R.drawable.ic_power_battery_saver, "500",
                "powersave", 50),
        UNKNOWN(R.string.powerprofile_unknown,
                R.drawable.ic_power_default, "500",
                "walt", 100);

        private final int nameResId;
        private final int iconResId;
        private final String sconfigValue;
        private final String governor;
        private final int freqPercent;

        PowerProfile(int nameResId, int iconResId, String sconfigValue,
                     String governor, int freqPercent) {
            this.nameResId = nameResId;
            this.iconResId = iconResId;
            this.sconfigValue = sconfigValue;
            this.governor = governor;
            this.freqPercent = freqPercent;
        }

        public int getNameResId() { return nameResId; }
        public int getIconResId() { return iconResId; }
        public String getSconfigValue() { return sconfigValue; }
        public String getGovernor() { return governor; }
        public int getFreqPercent() { return freqPercent; }

        public static PowerProfile fromSconfig(int value) {
            switch (value) {
                case 520: return GAMING;
                case 500: return DEFAULT;
                default:  return UNKNOWN;
            }
        }

        public static PowerProfile fromName(String name) {
            for (PowerProfile profile : values()) {
                if (profile.name().equals(name)) return profile;
            }
            return UNKNOWN;
        }

        public PowerProfile getNext() {
            switch (this) {
                case DEFAULT: return SCHEDUTIL;
                case SCHEDUTIL: return GAMING;
                case GAMING: return BATTERY;
                case BATTERY: return EXTREME;
                case EXTREME:
                case UNKNOWN:
                default: return DEFAULT;
            }
        }
    }

    private SharedPreferences mSharedPrefs;

    /**
     * Shared across tile service instances. The QS host tears the service
     * down as soon as the panel closes; a per-instance executor would be
     * shutdownNow()'d (onDestroy) before a queued kernel apply had run,
     * leaving the tile showing a profile that was never applied.
     */
    private static final ExecutorService mExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        initializeComponents();
        setupNotificationChannel(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!isPowerEnabled()) {
            updateTileState(PowerProfile.UNKNOWN, false);
            return;
        }
        if (isFirstBoot()) {
            saveProfile(PowerProfile.DEFAULT);
            updateTileState(PowerProfile.DEFAULT, true);
        } else {
            PowerProfile saved = getSavedProfile();
            if (saved != PowerProfile.UNKNOWN) {
                updateTileState(saved, true);
            } else {
                updateTileState(PowerProfile.DEFAULT, true);
            }
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!isPowerEnabled()) return;
        PowerProfile next = getSavedProfile().getNext();
        applyProfile(next);
    }

    @Override
    public void onDestroy() {
        // Do NOT shut down the shared executor here: the QS host destroys this
        // service as soon as the panel closes, and a queued kernel apply task
        // must still run. The executor lives for the process lifetime.
        mSharedPrefs = null;
        super.onDestroy();
    }

    private void initializeComponents() {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private boolean isPowerEnabled() {
        if (mSharedPrefs == null) return true;
        return mSharedPrefs.getBoolean(POWER_ENABLED_KEY, true);
    }

    private void applyProfile(PowerProfile profile) {
        saveProfile(profile);
        updateTileState(profile, true);

        updatePerformanceNotification(this, profile);

        mExecutor.execute(() -> applyKernelSettings(this, profile));
    }

    /**
     * Shows or cancels the ongoing performance notification depending on
     * the active profile.  Safe to call from Settings or the tile.
     */
    public static void updatePerformanceNotification(Context context,
            PowerProfile profile) {
        if (profile == PowerProfile.GAMING) {
            showPerformanceNotification(context, profile);
        } else {
            cancelPerformanceNotification(context);
        }
    }

    /**
     * Applies the kernel side (governor, freq cap, sconfig, battery saver)
     * for the requested profile. Used both by the tile and by the settings
     * fragment so a selection in Settings is applied immediately.
     */
    public static void applyKernelSettings(Context context, PowerProfile profile) {
        if (profile == PowerProfile.DEFAULT) {
            restoreOriginalGovernors(context);
        } else {
            applyGovernor(context, profile.getGovernor());
        }
        applyFreqCap(context, profile.getFreqPercent());
        FileUtils.writeLine(POWER_PROFILE_PATH, profile.getSconfigValue());
        handleBatterySaver(context, profile);
        Log.d(TAG, "Applied: " + profile.name()
                + " freq=" + profile.getFreqPercent() + "%"
                + " sconfig=" + profile.getSconfigValue());
    }

    private static void applyGovernor(Context context, String governor) {
        for (String cpu : CPU_REPS) {
            recordOriginalGovernor(context, cpu);
            writeGovernor(cpu, governor);
        }
    }

    private static void recordOriginalGovernor(Context context, String cpu) {
        String key = ORIG_GOVERNOR_PREFIX + cpu;
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(key)) return;
        String gov = FileUtils.readOneLine(
                CPU_BASE + cpu + "/cpufreq/scaling_governor");
        if (gov != null && !gov.isEmpty()) {
            prefs.edit().putString(key, gov.trim()).apply();
        }
    }

    private static void restoreOriginalGovernors(Context context) {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        for (String cpu : CPU_REPS) {
            recordOriginalGovernor(context, cpu);
            String gov = prefs.getString(ORIG_GOVERNOR_PREFIX + cpu, "");
            if (gov == null || gov.isEmpty()) continue;
            writeGovernor(cpu, gov);
        }
    }

    private static void writeGovernor(String cpu, String governor) {
        String path = CPU_BASE + cpu + "/cpufreq/scaling_governor";
        if (!FileUtils.writeLine(path, governor)) {
            Log.w(TAG, "Failed to set governor " + governor + " on " + cpu);
        }
    }

    private static void recordOriginalMaxFreq(Context context, String cpu) {
        String key = ORIG_MAX_FREQ_PREFIX + cpu;
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(key)) return;
        String cur = FileUtils.readOneLine(
                CPU_BASE + cpu + "/cpufreq/scaling_max_freq");
        if (cur == null) return;
        try {
            int v = Integer.parseInt(cur.trim());
            if (v > 0) {
                prefs.edit().putInt(key, v).apply();
            }
        } catch (NumberFormatException e) {
            // ignore unparseable values
        }
    }

    private static void writeMaxFreq(String cpu, int freq) {
        String path = CPU_BASE + cpu + "/cpufreq/scaling_max_freq";
        if (!FileUtils.writeLine(path, String.valueOf(freq))) {
            Log.w(TAG, "Failed to set max freq on " + cpu);
        }
    }

    private static void applyFreqCap(Context context, int percent) {
        // Compose the cap from the snapshot of the stock scaling_max_freq,
        // never from the current (possibly already capped) value, so switching
        // back to a 100% profile reliably restores the full frequency.
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        for (String cpu : CPU_REPS) {
            recordOriginalMaxFreq(context, cpu);
            int orig = prefs.getInt(ORIG_MAX_FREQ_PREFIX + cpu, 0);
            if (orig <= 0) {
                Log.w(TAG, "Could not read original max freq on " + cpu);
                continue;
            }
            int capped = orig * percent / 100;
            if (percent >= 100) {
                capped = orig;
            }
            writeMaxFreq(cpu, capped);
        }
    }

    private static void handleBatterySaver(Context context, PowerProfile profile) {
        PowerManager pm = context.getSystemService(PowerManager.class);
        if (pm == null) return;
        try {
            switch (profile) {
                case BATTERY:
                    Settings.Global.putInt(context.getContentResolver(),
                            Settings.Global.LOW_POWER_MODE, 1);
                    Settings.Global.putInt(context.getContentResolver(),
                            Settings.Global.EXTRA_LOW_POWER_MODE, 0);
                    break;
                case EXTREME:
                    Settings.Global.putInt(context.getContentResolver(),
                            Settings.Global.LOW_POWER_MODE, 1);
                    Settings.Global.putInt(context.getContentResolver(),
                            Settings.Global.EXTRA_LOW_POWER_MODE, 1);
                    break;
                default:
                    Settings.Global.putInt(context.getContentResolver(),
                            Settings.Global.LOW_POWER_MODE, 0);
                    Settings.Global.putInt(context.getContentResolver(),
                            Settings.Global.EXTRA_LOW_POWER_MODE, 0);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle battery saver", e);
        }
    }

    private void updateTileState(PowerProfile profile, boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel(getString(R.string.powerprofile_title));
        tile.setIcon(Icon.createWithResource(this, profile.getIconResId()));

        if (enabled && profile != PowerProfile.UNKNOWN) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(profile.getNameResId()));
        } else {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.power_tile_disabled_subtitle));
        }
        tile.updateTile();
    }

    private static void setupNotificationChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(TAG,
                context.getString(R.string.perf_mode_title),
                NotificationManager.IMPORTANCE_LOW);
        channel.setBlockable(true);
        nm.createNotificationChannel(channel);
    }

    private static void showPerformanceNotification(Context context,
            PowerProfile profile) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        setupNotificationChannel(context);
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(context, TAG)
                .setContentTitle(context.getString(profile.getNameResId()))
                .setContentText(context.getString(R.string.gaming_mode_notification))
                .setSmallIcon(profile.getIconResId())
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        nm.notify(NOTIFICATION_ID_PERFORMANCE, n);
    }

    private static void cancelPerformanceNotification(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_PERFORMANCE);
        }
    }

    private void saveProfile(PowerProfile profile) {
        if (mSharedPrefs != null) {
            mSharedPrefs.edit()
                    .putString(POWER_PROFILE_PREF_KEY, profile.name())
                    .apply();
        }
    }

    private PowerProfile getSavedProfile() {
        if (mSharedPrefs == null) return PowerProfile.DEFAULT;
        String saved = mSharedPrefs.getString(
                POWER_PROFILE_PREF_KEY, PowerProfile.DEFAULT.name());
        return PowerProfile.fromName(saved);
    }

    private boolean isFirstBoot() {
        if (mSharedPrefs == null) return true;
        return !mSharedPrefs.contains(POWER_PROFILE_PREF_KEY);
    }
}
