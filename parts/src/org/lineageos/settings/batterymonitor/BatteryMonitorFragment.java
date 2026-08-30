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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

import java.util.Locale;

public class BatteryMonitorFragment extends Fragment {

    private LinearLayout mContainer;
    private Handler mHandler;
    private boolean mRunning;
    private volatile int mGeneration;

    private TextView mNowCurrent;
    private TextView mNowPower;
    private TextView mNowTemp;
    private TextView mNowVoltage;
    private TextView mNowHealth;

    private LinearLayout mChargingCard;
    private TextView mChargeSpeed;
    private TextView mChargePower;
    private TextView mTimeToFull;

    private LinearLayout mDischargingCard;
    private TextView mActiveDrain;
    private TextView mIdleDrain;
    private TextView mTimeRemaining;

    private TextView mScreenOn;
    private TextView mScreenOff;
    private TextView mDeepSleep;
    private TextView mAwake;

    private TextView mFreeRam;
    private TextView mUptime;

    private TextView mUpdateInterval;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.battery_monitor_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
        mContainer = view.findViewById(R.id.bm_dashboard_container);
        View scrollView = view.findViewById(R.id.bm_scroll_view);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets system = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            int pad = dp(16);
            v.setPadding(pad, pad, pad, system.bottom + pad);
            return insets;
        });
        buildDashboard();
    }

    @Override
    public void onResume() {
        super.onResume();
        mRunning = true;
        mRunnable.run();
    }

    @Override
    public void onPause() {
        mRunning = false;
        mGeneration++;
        mHandler.removeCallbacks(mRunnable);
        super.onPause();
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            updateDashboardAsync();
            BatteryMonitorService svc = BatteryMonitorService.getInstance();
            int interval = svc != null ? svc.getPollInterval()
                    : BatteryMonitorUtils.DEFAULT_POLL_MS;
            mHandler.postDelayed(this, Math.max(interval, 3000));
        }
    };

    private void updateDashboardAsync() {
        final Context ctx = getContext();
        final android.app.Activity activity = getActivity();
        if (ctx == null || activity == null || mContainer == null) return;
        final int gen = mGeneration;
        new Thread(() -> {
            if (gen != mGeneration || !isAdded()) return;
            final BatteryMonitorUtils.BatteryStats stats =
                    BatteryMonitorUtils.collect(ctx);
            // Only the service records tracking samples; if it is not running
            // (monitor toggled off), seed from here so the dashboard still
            // shows drain/charge-speed. This avoids double-sampling the EMA
            // when both the service and the fragment are active.
            if (BatteryMonitorService.getInstance() == null) {
                DrainTracker.recordSample(ctx, stats.level, stats.isScreenOn,
                        stats.isCharging, stats.currentMa);
            }
            activity.runOnUiThread(() -> {
                if (gen != mGeneration || !isAdded()) return;
                updateDashboard(stats);
            });
        }, "bm-collect").start();
    }

    private void buildDashboard() {
        Context ctx = getContext();
        if (ctx == null) return;
        mContainer.removeAllViews();

        mNowCurrent = mNowPower = mNowTemp = mNowVoltage = mNowHealth = null;
        mChargeSpeed = mChargePower = mTimeToFull = null;
        mActiveDrain = mIdleDrain = mTimeRemaining = null;
        mScreenOn = mScreenOff = mDeepSleep = mAwake = null;
        mFreeRam = mUptime = null;

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean showCurrent = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_CURRENT, true);
        boolean showDrain = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_DRAIN, true);
        boolean showTemp = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_TEMP, true);
        boolean showVoltage = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_VOLTAGE, true);
        boolean showHealth = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_HEALTH, true);
        boolean showScreen = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_SCREEN, true);
        boolean showRam = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_RAM, true);
        boolean showUptime = prefs.getBoolean(BatteryMonitorUtils.PREF_SHOW_UPTIME, true);

        LinearLayout cardNow = createCard(ctx, getString(R.string.bm_card_now));
        LinearLayout nowContent = (LinearLayout) cardNow.getChildAt(1);

        LinearLayout nowRow1 = new LinearLayout(ctx);
        nowRow1.setOrientation(LinearLayout.HORIZONTAL);
        nowRow1.setLayoutParams(rowLp);
        if (showCurrent) {
            LinearLayout nowCol1 = createStatColumn(ctx);
            mNowCurrent = addStatItem(nowCol1, getString(R.string.bm_label_current), "-- mA");
            mNowPower = addStatItem(nowCol1, getString(R.string.bm_label_power), "-- W");
            nowRow1.addView(nowCol1);
        }
        if (showTemp) {
            LinearLayout nowCol2 = createStatColumn(ctx);
            mNowTemp = addStatItem(nowCol2, getString(R.string.bm_label_temperature), "--\u00b0C");
            nowRow1.addView(nowCol2);
        }
        nowContent.addView(nowRow1);

        if (showVoltage || showHealth) {
            LinearLayout nowRow2 = new LinearLayout(ctx);
            nowRow2.setOrientation(LinearLayout.HORIZONTAL);
            nowRow2.setLayoutParams(rowLp);
            if (showVoltage) {
                LinearLayout nowCol3 = createStatColumn(ctx);
                mNowVoltage = addStatItem(nowCol3, getString(R.string.bm_label_voltage), "-- mV");
                nowRow2.addView(nowCol3);
            }
            if (showHealth) {
                LinearLayout nowCol4 = createStatColumn(ctx);
                mNowHealth = addStatItem(nowCol4, getString(R.string.bm_label_health), "--");
                nowRow2.addView(nowCol4);
            }
            nowContent.addView(nowRow2);
        }

        mContainer.addView(cardNow, cardLp);

        mChargingCard = createCard(ctx, getString(R.string.bm_card_charging));
        LinearLayout chargingContent = (LinearLayout) mChargingCard.getChildAt(1);
        LinearLayout chargeStats = new LinearLayout(ctx);
        chargeStats.setOrientation(LinearLayout.HORIZONTAL);
        chargeStats.setLayoutParams(rowLp);
        LinearLayout chargeCol1 = createStatColumn(ctx);
        mChargeSpeed = addStatItem(chargeCol1, getString(R.string.bm_label_charging_speed), "-- mA");
        mChargePower = addStatItem(chargeCol1, getString(R.string.bm_label_power), "-- W");
        chargeStats.addView(chargeCol1);
        LinearLayout chargeCol2 = createStatColumn(ctx);
        mTimeToFull = addStatItem(chargeCol2, getString(R.string.bm_label_time_to_full), "--");
        chargeStats.addView(chargeCol2);
        chargingContent.addView(chargeStats);
        mContainer.addView(mChargingCard, cardLp);

        mDischargingCard = createCard(ctx, getString(R.string.bm_card_discharging));
        LinearLayout dischargingContent = (LinearLayout) mDischargingCard.getChildAt(1);
        LinearLayout drainRow = new LinearLayout(ctx);
        drainRow.setOrientation(LinearLayout.HORIZONTAL);
        drainRow.setLayoutParams(rowLp);
        LinearLayout drainCol1 = createStatColumn(ctx);
        mActiveDrain = addStatItem(drainCol1, getString(R.string.bm_label_active_drain), "-- %/h");
        drainRow.addView(drainCol1);
        LinearLayout drainCol2 = createStatColumn(ctx);
        mIdleDrain = addStatItem(drainCol2, getString(R.string.bm_label_idle_drain), "-- %/h");
        drainRow.addView(drainCol2);
        dischargingContent.addView(drainRow);
        LinearLayout remainingRow = new LinearLayout(ctx);
        remainingRow.setOrientation(LinearLayout.HORIZONTAL);
        remainingRow.setLayoutParams(rowLp);
        LinearLayout remainingCol = createStatColumn(ctx);
        mTimeRemaining = addStatItem(remainingCol, getString(R.string.bm_label_time_remaining), "--");
        remainingRow.addView(remainingCol);
        dischargingContent.addView(remainingRow);
        mDischargingCard.setVisibility(showDrain ? View.VISIBLE : View.GONE);
        mContainer.addView(mDischargingCard, cardLp);

        if (showScreen) {
            LinearLayout cardUsage = createCard(ctx, getString(R.string.bm_card_usage));
            LinearLayout usageContent = (LinearLayout) cardUsage.getChildAt(1);
            LinearLayout usageRow = new LinearLayout(ctx);
            usageRow.setOrientation(LinearLayout.HORIZONTAL);
            usageRow.setLayoutParams(rowLp);
            LinearLayout uCol1 = createStatColumn(ctx);
            mScreenOn = addStatItem(uCol1, getString(R.string.bm_label_screen_on), "--");
            mDeepSleep = addStatItem(uCol1, getString(R.string.bm_label_deep_sleep), "--");
            usageRow.addView(uCol1);
            LinearLayout uCol2 = createStatColumn(ctx);
            mScreenOff = addStatItem(uCol2, getString(R.string.bm_label_screen_off), "--");
            mAwake = addStatItem(uCol2, getString(R.string.bm_label_awake), "--");
            usageRow.addView(uCol2);
            usageContent.addView(usageRow);
            mContainer.addView(cardUsage, cardLp);
        } else {
            mScreenOn = mScreenOff = mDeepSleep = mAwake = null;
        }

        if (showRam || showUptime) {
            LinearLayout cardSystem = createCard(ctx, getString(R.string.bm_card_system));
            LinearLayout systemContent = (LinearLayout) cardSystem.getChildAt(1);
            if (showRam) {
                LinearLayout sysRow = new LinearLayout(ctx);
                sysRow.setOrientation(LinearLayout.HORIZONTAL);
                sysRow.setLayoutParams(rowLp);
                LinearLayout sCol1 = createStatColumn(ctx);
                mFreeRam = addStatItem(sCol1, getString(R.string.bm_label_free_ram), "--");
                sysRow.addView(sCol1);
                systemContent.addView(sysRow);
            }
            if (showUptime) {
                LinearLayout sysRow2 = new LinearLayout(ctx);
                sysRow2.setOrientation(LinearLayout.HORIZONTAL);
                sysRow2.setLayoutParams(rowLp);
                LinearLayout sCol2 = createStatColumn(ctx);
                mUptime = addStatItem(sCol2, getString(R.string.bm_label_uptime), "--");
                sysRow2.addView(sCol2);
                systemContent.addView(sysRow2);
            }
            mContainer.addView(cardSystem, cardLp);
        } else {
            mFreeRam = mUptime = null;
        }

        LinearLayout cardSettings = createCard(ctx, getString(R.string.bm_card_settings));
        LinearLayout settingsContent = (LinearLayout) cardSettings.getChildAt(1);

        addSwitchRow(settingsContent, getString(R.string.bm_enable_title),
                BatteryMonitorUtils.PREF_ENABLED, (btn, isChecked) -> {
                    if (isChecked) {
                        ctx.startForegroundService(
                                new Intent(ctx, BatteryMonitorService.class));
                    } else {
                        ctx.startService(new Intent(ctx,
                                BatteryMonitorService.class).setAction("STOP"));
                    }
                    btn.setTag(Boolean.TRUE);
                });
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_current),
                BatteryMonitorUtils.PREF_SHOW_CURRENT, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_drain),
                BatteryMonitorUtils.PREF_SHOW_DRAIN, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_temp),
                BatteryMonitorUtils.PREF_SHOW_TEMP, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_voltage),
                BatteryMonitorUtils.PREF_SHOW_VOLTAGE, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_health),
                BatteryMonitorUtils.PREF_SHOW_HEALTH, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_screen),
                BatteryMonitorUtils.PREF_SHOW_SCREEN, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_ram),
                BatteryMonitorUtils.PREF_SHOW_RAM, null);
        addSwitchRow(settingsContent, getString(R.string.bm_toggle_uptime),
                BatteryMonitorUtils.PREF_SHOW_UPTIME, null);

        LinearLayout intervalRow = new LinearLayout(ctx);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);
        intervalRow.setLayoutParams(rowLp);
        intervalRow.setPadding(0, dp(4), 0, dp(4));

        TextView intervalLabel = new TextView(ctx);
        intervalLabel.setText(R.string.bm_label_update_interval);
        intervalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        intervalLabel.setTextColor(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams intervalLabelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        intervalLabel.setLayoutParams(intervalLabelLp);
        intervalRow.addView(intervalLabel);

        mUpdateInterval = new TextView(ctx);
        mUpdateInterval.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mUpdateInterval.setTextColor(getResources().getColor(R.color.charging_green));
        mUpdateInterval.setTypeface(null, Typeface.BOLD);
        mUpdateInterval.setGravity(Gravity.END);
        mUpdateInterval.setOnClickListener(v -> showIntervalPicker());
        intervalRow.addView(mUpdateInterval);
        settingsContent.addView(intervalRow);

        TextView resetDrainBtn = createButtonText(ctx, getString(R.string.bm_reset_drain_title));
        resetDrainBtn.setOnClickListener(v -> confirmResetDrain());
        settingsContent.addView(resetDrainBtn);

        TextView resetAllBtn = createButtonText(ctx, getString(R.string.bm_reset_all_title));
        resetAllBtn.setOnClickListener(v -> confirmResetAll());
        settingsContent.addView(resetAllBtn);

        mContainer.addView(cardSettings, cardLp);

        updateDashboardAsync();
    }

    private void addSwitchRow(LinearLayout parent, String title, String prefKey,
            android.widget.CompoundButton.OnCheckedChangeListener listener) {
        Context ctx = parent.getContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(2);
        rowLp.bottomMargin = dp(2);
        row.setLayoutParams(rowLp);

        TextView label = new TextView(ctx);
        label.setText(title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(getResources().getColor(R.color.stat_value));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelLp);
        row.addView(label);

        Switch sw = new Switch(ctx);
        boolean defVal = !BatteryMonitorUtils.PREF_ENABLED.equals(prefKey);
        boolean checked = prefs.getBoolean(prefKey, defVal);
        sw.setTag(Boolean.FALSE);
        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            if (btn.getTag() != Boolean.TRUE) return;
            btn.setTag(Boolean.FALSE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(prefKey, isChecked);
            editor.apply();
            BatteryMonitorService svc = BatteryMonitorService.getInstance();
            if (svc != null) svc.refreshNotification();
            if (listener != null) {
                listener.onCheckedChanged(btn, isChecked);
            } else {
                buildDashboard();
            }
        });
        sw.setChecked(checked);
        sw.setTag(Boolean.TRUE);
        row.addView(sw);

        parent.addView(row);
    }

    private void updateDashboard(BatteryMonitorUtils.BatteryStats stats) {
        Context ctx = getContext();
        if (ctx == null || mContainer == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        if (mNowCurrent != null && mNowPower != null) {
            int absCurrent = Math.abs(stats.currentMa);
            if (absCurrent > 0) {
                float power = (stats.voltageMv * absCurrent) / 1000000.0f;
                mNowCurrent.setText((stats.currentMa > 0 ? "+" : "-")
                        + absCurrent + " mA");
                mNowPower.setText(String.format(Locale.US, "%.1f W", power));
            } else {
                mNowCurrent.setText("-- mA");
                mNowPower.setText("-- W");
            }
        }
        if (mNowTemp != null) {
            mNowTemp.setText(String.format(Locale.US, "%.1f\u00b0C", stats.temperature));
        }
        if (mNowVoltage != null) {
            mNowVoltage.setText(stats.voltageMv + " mV");
        }
        if (mNowHealth != null) {
            mNowHealth.setText(BatteryMonitorUtils.getHealthString(stats.health));
        }

        if (stats.isCharging) {
            mChargingCard.setVisibility(View.VISIBLE);
            mDischargingCard.setVisibility(View.GONE);

            float chargeSpeed = DrainTracker.getChargeSpeed(ctx);
            mChargeSpeed.setText(chargeSpeed > 0
                    ? String.format(Locale.US, "%.0f mA", chargeSpeed) : getString(R.string.bm_label_na));
            int absCurrent = Math.abs(stats.currentMa);
            if (absCurrent > 0) {
                float power = (stats.voltageMv * absCurrent) / 1000000.0f;
                mChargePower.setText(String.format(Locale.US, "%.1f W", power));
            } else {
                mChargePower.setText(getString(R.string.bm_label_na));
            }
            int ttfCurrent = chargeSpeed > 0
                    ? Math.round(chargeSpeed) : absCurrent;
            float ttf = BatteryMonitorUtils.calculateTimeToFull(
                    stats.level, ttfCurrent);
            mTimeToFull.setText(BatteryMonitorUtils.formatTimeToFull(ttf));
        } else {
            mChargingCard.setVisibility(View.GONE);
            boolean showDrain = prefs.getBoolean(
                    BatteryMonitorUtils.PREF_SHOW_DRAIN, true);
            mDischargingCard.setVisibility(showDrain ? View.VISIBLE : View.GONE);

            float activeDrain = DrainTracker.getActiveDrain(ctx);
            float idleDrain = DrainTracker.getIdleDrain(ctx);
            mActiveDrain.setText(activeDrain > 0
                    ? String.format(Locale.US, "%.1f %%/h", activeDrain)
                    : getString(R.string.bm_label_na));
            mIdleDrain.setText(idleDrain > 0
                    ? String.format(Locale.US, "%.1f %%/h", idleDrain)
                    : getString(R.string.bm_label_na));
            float hrsRemaining = BatteryMonitorUtils.calculateTimeRemaining(
                    stats.level, activeDrain);
            mTimeRemaining.setText(BatteryMonitorUtils.formatTimeRemaining(hrsRemaining));
        }

        if (mScreenOn != null && mScreenOff != null) {
            long elapsed = stats.elapsedBaseMs;
            float screenOnPct = BatteryMonitorUtils.calculatePercentage(
                    stats.screenOnMs, elapsed);
            float screenOffPct = BatteryMonitorUtils.calculatePercentage(
                    stats.screenOffMs, elapsed);
            mScreenOn.setText(BatteryMonitorUtils.formatDuration(stats.screenOnMs)
                    + " (" + String.format(Locale.US, "%.1f", screenOnPct) + "%)");
            mScreenOff.setText(BatteryMonitorUtils.formatDuration(stats.screenOffMs)
                    + " (" + String.format(Locale.US, "%.1f", screenOffPct) + "%)");
        }

        if (mDeepSleep != null && mAwake != null) {
            long elapsed = stats.elapsedRealtimeMs;
            if (!stats.isCharging) {
                float deepSleepPct = BatteryMonitorUtils.calculatePercentage(
                        stats.deepSleepMs, elapsed);
                float awakePct = BatteryMonitorUtils.calculatePercentage(
                        stats.awakeMs, elapsed);
                mDeepSleep.setText(BatteryMonitorUtils.formatDuration(stats.deepSleepMs)
                        + " (" + String.format(Locale.US, "%.1f", deepSleepPct) + "%)");
                mAwake.setText(BatteryMonitorUtils.formatDuration(stats.awakeMs)
                        + " (" + String.format(Locale.US, "%.1f", awakePct) + "%)");
            } else {
                mDeepSleep.setText(BatteryMonitorUtils.formatDuration(stats.deepSleepMs));
                mAwake.setText(BatteryMonitorUtils.formatDuration(stats.awakeMs));
            }
        }

        if (mFreeRam != null) {
            long freeMb = stats.ramTotalMb - stats.ramUsedMb;
            mFreeRam.setText(freeMb + " MB / " + stats.ramTotalMb + " MB");
        }
        if (mUptime != null) {
            mUptime.setText(BatteryMonitorUtils.formatDurationLong(stats.uptimeMs));
        }

        int interval = prefs.getInt(BatteryMonitorUtils.PREF_POLL_INTERVAL,
                BatteryMonitorUtils.DEFAULT_POLL_MS);
        mUpdateInterval.setText((interval / 1000) + "s");
    }

    private void showIntervalPicker() {
        Context ctx = getContext();
        if (ctx == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        int current = prefs.getInt(BatteryMonitorUtils.PREF_POLL_INTERVAL,
                BatteryMonitorUtils.DEFAULT_POLL_MS);

        int checkedItem = 0;
        for (int i = 0; i < BatteryMonitorUtils.POLL_INTERVALS.length; i++) {
            if (BatteryMonitorUtils.POLL_INTERVALS[i] == current) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.bm_label_update_interval)
                .setSingleChoiceItems(BatteryMonitorUtils.POLL_INTERVAL_LABELS,
                        checkedItem, (dialog, which) -> {
                            prefs.edit()
                                    .putInt(BatteryMonitorUtils.PREF_POLL_INTERVAL,
                                            BatteryMonitorUtils.POLL_INTERVALS[which])
                                    .apply();
                            mUpdateInterval.setText(
                                    BatteryMonitorUtils.POLL_INTERVAL_LABELS[which]);
                            BatteryMonitorService svc = BatteryMonitorService.getInstance();
                            if (svc != null) svc.refreshNotification();
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmResetDrain() {
        Context ctx = getContext();
        if (ctx == null) return;
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.bm_reset_drain_title)
                .setMessage(R.string.bm_reset_drain_dialog)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    DrainTracker.clearDrain(ctx);
                    BatteryMonitorService svc = BatteryMonitorService.getInstance();
                    if (svc != null) svc.refreshNotification();
                    updateDashboardAsync();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmResetAll() {
        Context ctx = getContext();
        if (ctx == null) return;
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.bm_reset_all_title)
                .setMessage(R.string.bm_reset_all_dialog)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    DrainTracker.clearDrain(ctx);
                    BatteryMonitorService svc = BatteryMonitorService.getInstance();
                    if (svc != null) {
                        svc.resetTracking();
                        svc.refreshNotification();
                    }
                    updateDashboardAsync();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private LinearLayout createCard(Context ctx, String title) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView header = new TextView(ctx);
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTextColor(getResources().getColor(R.color.text_secondary));
        header.setTypeface(null, Typeface.BOLD);
        header.setAllCaps(true);
        header.setLetterSpacing(0.05f);
        card.addView(header);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, 0);
        card.addView(content);

        return card;
    }

    private TextView createSubText(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        return tv;
    }

    private LinearLayout createStatColumn(Context ctx) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return col;
    }

    private TextView addStatItem(LinearLayout parent, String label, String value) {
        Context ctx = parent.getContext();

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelTv.setTextColor(getResources().getColor(R.color.stat_label));
        parent.addView(labelTv);

        TextView valueTv = new TextView(ctx);
        valueTv.setText(value);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueTv.setTextColor(getResources().getColor(R.color.stat_value));
        valueTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueLp.bottomMargin = dp(8);
        valueTv.setLayoutParams(valueLp);
        parent.addView(valueTv);

        return valueTv;
    }

    private TextView createButtonText(Context ctx, String text) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTextColor(getResources().getColor(R.color.discharging_blue));
        btn.setTypeface(null, Typeface.BOLD);
        btn.setPadding(0, dp(10), 0, dp(10));
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A2A3A);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(8);
        btn.setLayoutParams(btnLp);
        return btn;
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp, getResources().getDisplayMetrics());
    }
}
