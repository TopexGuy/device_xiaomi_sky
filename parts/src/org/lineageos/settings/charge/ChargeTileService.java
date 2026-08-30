/*
 * Copyright (C) 2026 The LineageOS Project
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

package org.lineageos.settings.charge;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class ChargeTileService extends TileService {
    private static final String PREF_KEY = "saved_charge_mode";
    private static final String[] MODES = {"0", "8", "37"};
    private static final int[] LABEL_RES = {
        R.string.charge_mode_ok,
        R.string.charge_mode_cool,
        R.string.charge_mode_nuke
    };
    private static final int[] SUBTITLE_RES = {
        R.string.charge_mode_ok_desc,
        R.string.charge_mode_cool_desc,
        R.string.charge_mode_nuke_desc
    };

    @Override
    public void onClick() {
        int nextIndex = (getCurrentIndex() + 1) % MODES.length;
        saveIndex(nextIndex);
        updateTile();

        // Write the limit immediately. For non-zero modes applyLimit defers
        // the actual write until power is connected (writing unplugged can
        // leave the charger controller throttled); the enforcement service is
        // started below and applies it on the next POWER_CONNECTED event.
        ChargeEnforcementService.applyLimit(this, nextIndex);

        Intent intent = new Intent(this, ChargeEnforcementService.class);
        if (nextIndex > 0) {
            startService(intent);
        } else {
            stopService(intent);
        }
    }

    private int getCurrentIndex() {
        int index = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(PREF_KEY, 0);
        return Math.max(0, Math.min(index, MODES.length - 1));
    }

    private void saveIndex(int index) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(PREF_KEY, index).apply();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        int index = getCurrentIndex();
        tile.setLabel(getString(R.string.charge_tile_title));
        tile.setSubtitle(getString(LABEL_RES[index])
                + " \u00b7 " + getString(SUBTITLE_RES[index]));
        tile.setState(index > 0 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }
}
