package org.lineageos.settings.charge;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import androidx.preference.PreferenceManager;
import org.lineageos.settings.utils.FileUtils;

/**
 * Re-applies the selected charge limit every 10s while charging. Sky has no
 * "constant_charge_current" node, so the limit is driven entirely through
 * charge_control_limit: 0 = full current, higher value = lower current
 * (8 = COOL, 37 = ~minimum). Only one limit value is active at a time, so
 * unlike the peridot original this does not need a second path.
 *
 * The sysfs node is only touched while power is actually connected. Writing
 * the limit while unplugged can leave the charger controller throttled when
 * the user plugs the phone back in, which shows up as broken charge
 * current/power values in the battery monitor.
 */
public class ChargeEnforcementService extends Service {
    private static final String TAG = "ChargeEnforcementService";
    private static final int REWRITE_INTERVAL = 10000; // 10 seconds
    private static final String LIMIT_PATH = "/sys/class/power_supply/battery/charge_control_limit";
    private static final int[] LIMIT_VALUES = {0, 8, 37}; // OK, COOL, NUKE

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    /**
     * Writes the charge limit for the given mode index to the sysfs node.
     * A non-zero limit is only written while power is actually connected —
     * writing it unplugged can leave the charger controller throttled at the
     * wrong time and shows up as broken charge current/power values in the
     * battery monitor. Clearing (index 0) is always allowed.
     *
     * @return true if the write succeeded
     */
    public static boolean applyLimit(Context context, int index) {
        if (index < 0 || index >= LIMIT_VALUES.length) index = 0;
        if (index > 0 && !isCharging(context)) {
            Log.d(TAG, "Deferred charge limit write, power not connected");
            return false;
        }
        boolean ok = FileUtils.writeLine(LIMIT_PATH, String.valueOf(LIMIT_VALUES[index]));
        if (!ok) {
            Log.w(TAG, "Failed to write charge limit " + LIMIT_VALUES[index]);
        }
        return ok;
    }

    private boolean isCharging() {
        return isCharging(this);
    }

    private static boolean isCharging(Context context) {
        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private final BroadcastReceiver mPowerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // The node may be reset by the charger controller when power is
            // (re)connected, so restart enforcement even if we had stopped.
            mHandler.removeCallbacks(mRewriteRunnable);
            mHandler.post(mRewriteRunnable);
        }
    };

    private final Runnable mRewriteRunnable = new Runnable() {
        @Override
        public void run() {
            int index = PreferenceManager.getDefaultSharedPreferences(ChargeEnforcementService.this)
                        .getInt("saved_charge_mode", 0);

            if (index < 0 || index >= LIMIT_VALUES.length) index = 0;

            if (index > 0) {
                // Only touch the sysfs node while power is actually connected;
                // writing the limit unplugged can leave the charger controller
                // throttled at the wrong time. The 10s re-write also covers
                // charge_control_limit being reset by the kernel/charger
                // controller while plugged in.
                if (isCharging()) {
                    if (!FileUtils.writeLine(LIMIT_PATH, String.valueOf(LIMIT_VALUES[index]))) {
                        Log.w(TAG, "Failed to write charge limit " + LIMIT_VALUES[index]);
                    }
                    mHandler.postDelayed(this, REWRITE_INTERVAL);
                }
                // Not charging: do not keep polling. The POWER_CONNECTED
                // receiver posts this runnable again as soon as power is
                // (re)connected, so the limit is applied on plug-in without
                // the service waking up every 10s while disconnected.
            } else {
                FileUtils.writeLine(LIMIT_PATH, "0");
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread("charge-enforcer");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        IntentFilter filter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mPowerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mHandler == null) {
            onCreate();
        }
        mHandler.removeCallbacks(mRewriteRunnable);
        mHandler.post(mRewriteRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mPowerReceiver);
        } catch (Exception ignored) {}
        if (mHandler != null) {
            mHandler.removeCallbacks(mRewriteRunnable);
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
