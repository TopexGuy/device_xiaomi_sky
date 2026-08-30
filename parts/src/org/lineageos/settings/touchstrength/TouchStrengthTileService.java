/*
 * Copyright (C) 2024 The LineageOS Project
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

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class TouchStrengthTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean newState = !prefs.getBoolean(TouchStrengthUtils.TS_ENABLE_KEY, false);
        prefs.edit().putBoolean(TouchStrengthUtils.TS_ENABLE_KEY, newState).apply();
        // The sysfs writes can block, so keep them off the main thread.
        new Thread(() -> TouchStrengthUtils.applySavedState(this)).start();
        updateTileState();
    }

    private void updateTileState() {
        boolean enabled = TouchStrengthUtils.isEnabled(this);

        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.touch_strength_tile_label));
            tile.updateTile();
        }
    }
}
