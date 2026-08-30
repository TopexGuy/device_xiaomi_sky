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

package org.lineageos.settings.power;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.quicksettings.TileService;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class PowerProfileSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceClickListener {

    private static final String PREF_KEY = "saved_power_profile";

    private static final String KEY_DEFAULT = "power_default";
    private static final String KEY_SCHEDUTIL = "power_schedutil";
    private static final String KEY_GAMING = "power_gaming";
    private static final String KEY_BATTERY = "power_battery";
    private static final String KEY_EXTREME = "power_extreme";

    private Preference mDefault;
    private Preference mSchedutil;
    private Preference mGaming;
    private Preference mBattery;
    private Preference mExtreme;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.power_profile_settings);

        mDefault = findPreference(KEY_DEFAULT);
        mSchedutil = findPreference(KEY_SCHEDUTIL);
        mGaming = findPreference(KEY_GAMING);
        mBattery = findPreference(KEY_BATTERY);
        mExtreme = findPreference(KEY_EXTREME);

        if (mDefault != null) mDefault.setOnPreferenceClickListener(this);
        if (mSchedutil != null) mSchedutil.setOnPreferenceClickListener(this);
        if (mGaming != null) mGaming.setOnPreferenceClickListener(this);
        if (mBattery != null) mBattery.setOnPreferenceClickListener(this);
        if (mExtreme != null) mExtreme.setOnPreferenceClickListener(this);

        SharedPreferences prefs = getPrefs();
        String saved = prefs != null ? prefs.getString(PREF_KEY, "DEFAULT") : "DEFAULT";
        updateSelection(saved);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (getActivity() == null) return false;

        String profile;
        if (KEY_DEFAULT.equals(preference.getKey())) {
            profile = "DEFAULT";
        } else if (KEY_SCHEDUTIL.equals(preference.getKey())) {
            profile = "SCHEDUTIL";
        } else if (KEY_GAMING.equals(preference.getKey())) {
            profile = "GAMING";
        } else if (KEY_BATTERY.equals(preference.getKey())) {
            profile = "BATTERY";
        } else if (KEY_EXTREME.equals(preference.getKey())) {
            profile = "EXTREME";
        } else {
            return false;
        }

        SharedPreferences prefs = getPrefs();
        if (prefs == null) return false;
        prefs.edit().putString(PREF_KEY, profile).apply();
        updateSelection(profile);

        // Apply the kernel settings on a background thread to avoid blocking
        // the UI (multiple sysfs writes + ContentResolver IPC).
        final android.app.Activity activity = getActivity();
        final PowerProfileTileService.PowerProfile pp =
                PowerProfileTileService.PowerProfile.fromName(profile);
        new Thread(() ->
                PowerProfileTileService.applyKernelSettings(activity, pp)).start();

        // Cancel or show the GAMING notification from the Settings path so
        // it doesn't get orphaned when the user switches profile via Settings.
        PowerProfileTileService.updatePerformanceNotification(
                activity, pp);

        TileService.requestListeningState(getActivity(),
                new ComponentName(getActivity(),
                        PowerProfileTileService.class));

        return true;
    }

    private void updateSelection(String profile) {
        if (mDefault != null) mDefault.setSummary("DEFAULT".equals(profile)
                ? "\u2713 " + getString(R.string.powerprofile_default_desc)
                : getString(R.string.powerprofile_default_desc));
        if (mSchedutil != null) mSchedutil.setSummary("SCHEDUTIL".equals(profile)
                ? "\u2713 " + getString(R.string.powerprofile_schedutil_desc)
                : getString(R.string.powerprofile_schedutil_desc));
        if (mGaming != null) mGaming.setSummary("GAMING".equals(profile)
                ? "\u2713 " + getString(R.string.powerprofile_gaming_desc)
                : getString(R.string.powerprofile_gaming_desc));
        if (mBattery != null) mBattery.setSummary("BATTERY".equals(profile)
                ? "\u2713 " + getString(R.string.powerprofile_battery_desc)
                : getString(R.string.powerprofile_battery_desc));
        if (mExtreme != null) mExtreme.setSummary("EXTREME".equals(profile)
                ? "\u2713 " + getString(R.string.powerprofile_extreme_desc)
                : getString(R.string.powerprofile_extreme_desc));
    }

    private SharedPreferences getPrefs() {
        if (getActivity() == null) return null;
        return PreferenceManager.getDefaultSharedPreferences(getActivity());
    }
}
