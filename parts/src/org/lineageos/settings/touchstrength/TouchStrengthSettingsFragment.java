/*
 * Copyright (C) 2018-2024 The LineageOS Project
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
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.CustomSeekBarPreference;
import org.lineageos.settings.R;

public class TouchStrengthSettingsFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener {

    private static final String TS_ENABLE = "touch_strength_enable";
    private static final String TS_THRESHOLD = "touch_strength_threshold";
    private static final String TS_TOLERANCE = "touch_strength_tolerance";

    private SwitchPreference mEnablePreference;
    private CustomSeekBarPreference mThresholdPreference;
    private CustomSeekBarPreference mTolerancePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.touch_strength_settings);

        mEnablePreference = (SwitchPreference) findPreference(TS_ENABLE);
        mThresholdPreference = (CustomSeekBarPreference) findPreference(TS_THRESHOLD);
        mTolerancePreference = (CustomSeekBarPreference) findPreference(TS_TOLERANCE);

        boolean enabled = TouchStrengthUtils.isEnabled(getActivity());
        if (mEnablePreference != null) {
            mEnablePreference.setChecked(enabled);
            mEnablePreference.setOnPreferenceChangeListener(this);
        }
        if (mThresholdPreference != null) {
            mThresholdPreference.setOnPreferenceChangeListener(this);
        }
        if (mTolerancePreference != null) {
            mTolerancePreference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (getActivity() == null) return false;

        if (TS_ENABLE.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (enabled && !TouchStrengthUtils.isAvailable()) {
                Toast.makeText(getActivity(),
                        R.string.touch_strength_unavailable, Toast.LENGTH_LONG).show();
                return false;
            }
            prefs.edit().putBoolean(TS_ENABLE, enabled).apply();
            // Offload sysfs writes to a background thread.
            new Thread(() ->
                    TouchStrengthUtils.applySavedState(getActivity())).start();
        } else if (TS_THRESHOLD.equals(preference.getKey())) {
            if (TouchStrengthUtils.isEnabled(getActivity())) {
                final int val = (Integer) newValue;
                new Thread(() ->
                        TouchStrengthUtils.writeUpThreshold(val)).start();
            }
        } else if (TS_TOLERANCE.equals(preference.getKey())) {
            if (TouchStrengthUtils.isEnabled(getActivity())) {
                final int val = (Integer) newValue;
                new Thread(() ->
                        TouchStrengthUtils.writeTolerance(val)).start();
            }
        }
        return true;
    }
}
