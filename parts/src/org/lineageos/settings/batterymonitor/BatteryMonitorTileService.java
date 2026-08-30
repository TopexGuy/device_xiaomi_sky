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
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class BatteryMonitorTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean(
                BatteryMonitorUtils.PREF_ENABLED, false);
        enabled = !enabled;
        prefs.edit()
                .putBoolean(BatteryMonitorUtils.PREF_ENABLED, enabled)
                .apply();

        if (enabled) {
            startForegroundService(new Intent(this,
                    BatteryMonitorService.class));
        } else {
            startService(new Intent(this,
                    BatteryMonitorService.class).setAction("STOP"));
        }

        updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean(
                BatteryMonitorUtils.PREF_ENABLED, false);
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.bm_tile_label));
        tile.setSubtitle(getString(enabled
                ? R.string.bm_tile_active
                : R.string.bm_tile_inactive));
        tile.updateTile();
    }
}
