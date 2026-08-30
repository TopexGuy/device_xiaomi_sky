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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class BatteryMonitorService extends Service {

    private static final String TAG = "BatteryMonitorService";
    private static final String CHANNEL_ID = "battery_monitor";
    private static final int NOTIFICATION_ID = 9001;

    private static final String PREF_TOTAL_SCREEN_ON = "bm_total_screen_on_ms";
    private static final String PREF_TOTAL_SCREEN_OFF = "bm_total_screen_off_ms";

    private static volatile BatteryMonitorService sInstance;

    private Thread mWorker;
    private volatile boolean mRunning;

    private final AtomicLong mTotalScreenOnMs = new AtomicLong();
    private final AtomicLong mTotalScreenOffMs = new AtomicLong();
    private volatile long mScreenStateChangedAtMs;
    private volatile boolean mScreenWasOn;
    private volatile long mServiceStartElapsedMs;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long now = SystemClock.elapsedRealtime();
            long duration = now - mScreenStateChangedAtMs;
            if (duration < 0) duration = 0;

            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (!mScreenWasOn) {
                    mTotalScreenOffMs.addAndGet(duration);
                }
                mScreenWasOn = true;
                mScreenStateChangedAtMs = now;
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (mScreenWasOn) {
                    mTotalScreenOnMs.addAndGet(duration);
                }
                mScreenWasOn = false;
                mScreenStateChangedAtMs = now;
            }
        }
    };

    public static BatteryMonitorService getInstance() {
        return sInstance;
    }

    public long getScreenOnMs() {
        long total = mTotalScreenOnMs.get();
        if (mScreenWasOn) {
            long now = SystemClock.elapsedRealtime();
            total += now - mScreenStateChangedAtMs;
        }
        return total;
    }

    public long getScreenOffMs() {
        long total = mTotalScreenOffMs.get();
        if (!mScreenWasOn) {
            long now = SystemClock.elapsedRealtime();
            total += now - mScreenStateChangedAtMs;
        }
        return total;
    }

    public long getDeepSleepMs() {
        long uptime = SystemClock.uptimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        return elapsed - uptime;
    }

    public long getAwakeMs() {
        return SystemClock.uptimeMillis();
    }

    public long getElapsedSinceStartMs() {
        return SystemClock.elapsedRealtime() - mServiceStartElapsedMs;
    }

    public synchronized void resetTracking() {
        mTotalScreenOnMs.set(0);
        mTotalScreenOffMs.set(0);
        long now = SystemClock.elapsedRealtime();
        mScreenStateChangedAtMs = now;
        mServiceStartElapsedMs = now;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .remove(PREF_TOTAL_SCREEN_ON)
                .remove(PREF_TOTAL_SCREEN_OFF)
                .apply();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        createNotificationChannel();

        long now = SystemClock.elapsedRealtime();
        mScreenStateChangedAtMs = now;
        mServiceStartElapsedMs = now;

        // Carry the accumulated screen-on/off totals across reboots and
        // service restarts — they only get cleared by "Reset All Data".
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        mTotalScreenOnMs.set(prefs.getLong(PREF_TOTAL_SCREEN_ON, 0L));
        mTotalScreenOffMs.set(prefs.getLong(PREF_TOTAL_SCREEN_OFF, 0L));

        PowerManager pm = getSystemService(PowerManager.class);
        mScreenWasOn = pm != null && pm.isInteractive();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // If this is a system restart (null intent) but the user disabled
        // tracking, do not resurrect the notification and polling loop.
        if (intent == null) {
            boolean enabled = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(BatteryMonitorUtils.PREF_ENABLED, false);
            if (!enabled) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        try {
            startForeground(NOTIFICATION_ID, buildBasicNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startPolling();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        persistTotals();
        mRunning = false;
        if (mWorker != null) mWorker.interrupt();
        try {
            unregisterReceiver(mScreenReceiver);
        } catch (Exception ignored) {}
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void refreshNotification() {
        if (mRunning) {
            updateNotification();
        }
    }

    public int getPollInterval() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getInt(BatteryMonitorUtils.PREF_POLL_INTERVAL,
                BatteryMonitorUtils.DEFAULT_POLL_MS);
    }

    private void startPolling() {
        if (mRunning) return;
        mRunning = true;
        mWorker = new Thread(() -> {
            while (mRunning) {
                try {
                    updateNotification();
                    int interval = getPollInterval();
                    Thread.sleep(Math.max(interval, 3000));
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "updateNotification failed", e);
                    try { Thread.sleep(10000); } catch (InterruptedException ie) { break; }
                }
            }
        }, "battery-monitor");
        mWorker.start();
    }

    private void updateNotification() {
        persistTotals();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /**
     * Snapshots the current screen-on/off totals so they survive a reboot
     * or service restart. Writes are cheap (async) and happen on every poll.
     */
    private void persistTotals() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(PREF_TOTAL_SCREEN_ON, getScreenOnMs())
                .putLong(PREF_TOTAL_SCREEN_OFF, getScreenOffMs())
                .apply();
    }

    private Notification buildBasicNotification() {
        Intent settings = new Intent(this, BatteryMonitorActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Topex Tools")
                .setContentText("Battery Monitor active")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private Notification buildNotification() {
        BatteryMonitorUtils.BatteryStats stats =
                BatteryMonitorUtils.collect(this);
        DrainTracker.recordSample(this, stats.level, stats.isScreenOn,
                stats.isCharging, stats.currentMa);

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        boolean showCurrent = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_CURRENT, true);
        boolean showDrain = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_DRAIN, true);
        boolean showTemp = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_TEMP, true);
        boolean showVoltage = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_VOLTAGE, true);
        boolean showHealth = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_HEALTH, true);
        boolean showScreen = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_SCREEN, true);
        boolean showRam = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_RAM, true);
        boolean showUptime = prefs.getBoolean(
                BatteryMonitorUtils.PREF_SHOW_UPTIME, true);

        float activeDrain = DrainTracker.getActiveDrain(this);
        float idleDrain = DrainTracker.getIdleDrain(this);

        String header = buildHeaderLine(stats, showTemp);
        String compact = buildCompactText(stats, showCurrent, showDrain);
        String expanded = buildExpandedText(stats, showCurrent, showDrain, showTemp,
                showVoltage, showHealth, showScreen, showRam, showUptime,
                activeDrain, idleDrain);

        Intent settings = new Intent(this, BatteryMonitorActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this,
                CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(header)
                .setContentText(compact)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(expanded)
                        .setBigContentTitle(header))
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        return builder.build();
    }

    private String buildHeaderLine(BatteryMonitorUtils.BatteryStats stats,
            boolean showTemp) {
        StringBuilder sb = new StringBuilder();
        if (stats.isCharging) sb.append("\u26A1");
        if (showTemp) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format(Locale.US, "%.1f",
                    stats.temperature)).append("\u00b0C");
        }
        return sb.toString();
    }

    private String buildCompactText(BatteryMonitorUtils.BatteryStats stats,
            boolean showCurrent, boolean showDrain) {
        StringBuilder sb = new StringBuilder();
        if (showCurrent) {
            int abs = Math.abs(stats.currentMa);
            if (abs > 0) {
                sb.append(stats.currentMa > 0 ? "+" : "-").append(abs)
                        .append(" mA");
                float power = (stats.voltageMv * abs) / 1000000.0f;
                sb.append("  ").append(String.format(Locale.US, "%.1f", power))
                        .append(" W");
            } else {
                sb.append("0 mA");
            }
        }
        if (!stats.isCharging && !showCurrent && showDrain) {
            float drain = DrainTracker.getActiveDrain(this);
            if (drain > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(String.format(Locale.US, "%.1f", drain)).append("%/hr");
            }
        }
        return sb.toString();
    }

    private String buildExpandedText(BatteryMonitorUtils.BatteryStats stats,
            boolean showCurrent, boolean showDrain, boolean showTemp,
            boolean showVoltage, boolean showHealth,
            boolean showScreen, boolean showRam, boolean showUptime,
            float activeDrain, float idleDrain) {
        StringBuilder sb = new StringBuilder();

        if (showCurrent) {
            int abs = Math.abs(stats.currentMa);
            if (abs > 0) {
                sb.append("Now: ").append(stats.currentMa > 0 ? "+" : "-")
                        .append(abs).append(" mA");
                float power = (stats.voltageMv * abs) / 1000000.0f;
                sb.append(" (").append(String.format(Locale.US, "%.1f", power))
                        .append(" W)");
            } else {
                sb.append("Now: 0 mA");
            }
            if (showVoltage) {
                sb.append("  ").append(stats.voltageMv).append(" mV");
            }
            sb.append("\n");
        } else if (showVoltage) {
            sb.append(stats.voltageMv).append(" mV\n");
        }

        if (showHealth) {
            sb.append("Health: ")
                    .append(BatteryMonitorUtils.getHealthString(stats.health))
                    .append("\n");
        }

        if (stats.isCharging) {
            float chargeSpeed = DrainTracker.getChargeSpeed(this);
            if (showCurrent && chargeSpeed > 0) {
                sb.append("Charging Speed: ")
                        .append(String.format(Locale.US, "%.0f", chargeSpeed))
                        .append(" mA\n");
            }
            if (chargeSpeed > 0) {
                float hours = BatteryMonitorUtils.calculateTimeToFull(
                        stats.level, Math.round(chargeSpeed));
                sb.append("Time to Full: ")
                        .append(BatteryMonitorUtils.formatTimeToFull(hours))
                        .append("\n");
            }
        } else if (showDrain) {
            sb.append("Active: ")
                    .append(String.format(Locale.US, "%.1f", activeDrain))
                    .append("%/h   Idle: ")
                    .append(String.format(Locale.US, "%.1f", idleDrain))
                    .append("%/h\n");
        }

        if (showScreen) {
            sb.append("Screen On: ")
                    .append(BatteryMonitorUtils.formatDuration(stats.screenOnMs))
                    .append(" (").append(String.format(Locale.US, "%.1f",
                            BatteryMonitorUtils.calculatePercentage(
                                    stats.screenOnMs, stats.elapsedBaseMs)))
                    .append("%)   Off: ")
                    .append(BatteryMonitorUtils.formatDuration(stats.screenOffMs))
                    .append(" (").append(String.format(Locale.US, "%.1f",
                            BatteryMonitorUtils.calculatePercentage(
                                    stats.screenOffMs, stats.elapsedBaseMs)))
                    .append("%)\n");
            sb.append("Deep Sleep: ")
                    .append(BatteryMonitorUtils.formatDuration(stats.deepSleepMs))
                    .append(" (").append(String.format(Locale.US, "%.1f",
                            BatteryMonitorUtils.calculatePercentage(
                                    stats.deepSleepMs, stats.elapsedRealtimeMs)))
                    .append("%)   Awake: ")
                    .append(BatteryMonitorUtils.formatDuration(stats.awakeMs))
                    .append(" (").append(String.format(Locale.US, "%.1f",
                            BatteryMonitorUtils.calculatePercentage(
                                    stats.awakeMs, stats.elapsedRealtimeMs)))
                    .append("%)\n");
        }

        if (showRam) {
            long freeMb = stats.ramTotalMb - stats.ramUsedMb;
            sb.append("RAM: ").append(freeMb).append(" MB free / ")
                    .append(stats.ramTotalMb).append(" MB\n");
        }

        if (showUptime) {
            sb.append("Uptime: ")
                    .append(BatteryMonitorUtils.formatDurationLong(stats.uptimeMs))
                    .append("\n");
        }

        return sb.toString();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bm_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.bm_notification_channel));
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
