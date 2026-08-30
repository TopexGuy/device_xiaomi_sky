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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

/**
 * Quick Settings tile that stops mi_thermald (Disable Thermals). The control
 * is applied off the main thread while the tile is optimistically updated;
 * it is reverted if the control command failed.
 */
public class PerformanceTileService extends TileService {

    private static final String NOTIFICATION_CHANNEL_ID = "performance_mode_channel";
    private static final int NOTIFICATION_ID_PERFORMANCE = 2001;

    private NotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        setupNotificationChannel();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState(ThermaldUtils.isPerformanceModeEnabled(this));
    }

    @Override
    public void onClick() {
        super.onClick();
        final boolean nextState = !ThermaldUtils.isPerformanceModeEnabled(this);
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(ThermaldUtils.PERF_MODE_ENABLE_KEY, nextState).apply();
        // Don't block the QS tap on the ctl command.
        updateTileState(nextState);
        new Thread(() -> {
            boolean ok = ThermaldUtils.applyEnabled(nextState);
            if (!ok) {
                prefs.edit()
                        .putBoolean(ThermaldUtils.PERF_MODE_ENABLE_KEY, !nextState)
                        .apply();
            }
            final boolean applied = ok;
            getMainExecutor().execute(() -> {
                if (!applied) {
                    Toast.makeText(getApplicationContext(),
                            R.string.performance_apply_failed,
                            Toast.LENGTH_LONG).show();
                }
                updateTileState(applied ? nextState : !nextState);
                if (applied) {
                    if (nextState) {
                        showPerformanceNotification();
                    } else {
                        cancelPerformanceNotification();
                    }
                }
            });
        }, "thermals-toggle").start();
    }

    private void updateTileState(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel(getString(R.string.performance_tile_title));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_performance_mode));
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(enabled
                ? R.string.performance_tile_subtitle_on
                : R.string.performance_tile_subtitle_off));
        tile.updateTile();
    }

    private void setupNotificationChannel() {
        if (mNotificationManager == null) return;
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.performance_tile_title),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setBlockable(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void showPerformanceNotification() {
        if (mNotificationManager == null) return;

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.performance_notification_title))
                .setContentText(getString(R.string.performance_notification_text))
                .setStyle(new Notification.BigTextStyle()
                        .bigText(getString(R.string.performance_notification_text)))
                .setSmallIcon(R.drawable.ic_performance_mode)
                .setOngoing(true)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID_PERFORMANCE, notification);
    }

    private void cancelPerformanceNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID_PERFORMANCE);
        }
    }
}