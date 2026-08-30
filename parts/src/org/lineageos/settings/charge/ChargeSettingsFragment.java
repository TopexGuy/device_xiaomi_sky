/*
 * Copyright (C) 2026 The LineageOS Project
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

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.quicksettings.TileService;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.R;

public class ChargeSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceClickListener {

    private static final String PREF_KEY = "saved_charge_mode";

    private static final String KEY_OK = "charge_mode_ok";
    private static final String KEY_COOL = "charge_mode_cool";
    private static final String KEY_NUKE = "charge_mode_nuke";

    private Preference mOk;
    private Preference mCool;
    private Preference mNuke;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.charge_settings);

        mOk = findPreference(KEY_OK);
        mCool = findPreference(KEY_COOL);
        mNuke = findPreference(KEY_NUKE);

        if (mOk != null) mOk.setOnPreferenceClickListener(this);
        if (mCool != null) mCool.setOnPreferenceClickListener(this);
        if (mNuke != null) mNuke.setOnPreferenceClickListener(this);

        SharedPreferences prefs = getPrefs();
        int saved = prefs != null ? prefs.getInt(PREF_KEY, 0) : 0;
        updateSelection(saved);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (getActivity() == null) return false;

        int index;
        if (KEY_OK.equals(preference.getKey())) {
            index = 0;
        } else if (KEY_COOL.equals(preference.getKey())) {
            index = 1;
        } else if (KEY_NUKE.equals(preference.getKey())) {
            index = 2;
        } else {
            return false;
        }

        SharedPreferences prefs = getPrefs();
        if (prefs == null) return false;
        prefs.edit().putInt(PREF_KEY, index).apply();
        updateSelection(index);

        // Write the limit immediately. For non-zero modes applyLimit defers
        // the actual write until power is connected (writing unplugged can
        // leave the charger controller throttled); the enforcement service is
        // started below and applies it on the next POWER_CONNECTED event.
        ChargeEnforcementService.applyLimit(getActivity(), index);

        Intent intent = new Intent(getActivity(),
                ChargeEnforcementService.class);
        if (index > 0) {
            getActivity().startService(intent);
        } else {
            getActivity().stopService(intent);
        }

        TileService.requestListeningState(getActivity(),
                new ComponentName(getActivity(), ChargeTileService.class));
        return true;
    }

    private void updateSelection(int index) {
        if (mOk != null) mOk.setSummary(index == 0
                ? "\u2713 " + getString(R.string.charge_mode_ok_desc)
                : getString(R.string.charge_mode_ok_desc));
        if (mCool != null) mCool.setSummary(index == 1
                ? "\u2713 " + getString(R.string.charge_mode_cool_desc)
                : getString(R.string.charge_mode_cool_desc));
        if (mNuke != null) mNuke.setSummary(index == 2
                ? "\u2713 " + getString(R.string.charge_mode_nuke_desc)
                : getString(R.string.charge_mode_nuke_desc));
    }

    private SharedPreferences getPrefs() {
        if (getActivity() == null) return null;
        return PreferenceManager.getDefaultSharedPreferences(getActivity());
    }
}
