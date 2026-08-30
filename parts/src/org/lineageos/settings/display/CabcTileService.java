package org.lineageos.settings.display;

import android.content.Context;
import android.os.SystemProperties;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.Arrays;

import org.lineageos.settings.R;

public class CabcTileService extends TileService {

    private Context context;
    private Tile tile;

    private String[] CabcModes;
    private int currentCabcMode;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        CabcModes = context.getResources().getStringArray(R.array.lcd_cabc_modes);
    }

    private void updateCurrentCabcMode() {
        int idx = Arrays.asList(getResources().getStringArray(R.array.lcd_cabc_values))
                .indexOf(SystemProperties.get(LcdFeaturesPreferenceFragment.CABC_PROP, "0"));
        currentCabcMode = Math.max(0, idx);
    }

    private void updateCabcTile() {
        if (tile == null || CabcModes.length == 0) return;
        tile.setState(currentCabcMode > 0 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setContentDescription(CabcModes[currentCabcMode]);
        tile.setSubtitle(CabcModes[currentCabcMode]);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        updateCurrentCabcMode();
        updateCabcTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        updateCurrentCabcMode();
        if (currentCabcMode >= CabcModes.length - 1) {
            currentCabcMode = 0;
        } else {
            currentCabcMode++;
        }
        String[] values = getResources().getStringArray(R.array.lcd_cabc_values);
        SystemProperties.set(LcdFeaturesPreferenceFragment.CABC_PROP,
                values[Math.min(currentCabcMode, values.length - 1)]);
        updateCabcTile();
    }
}
