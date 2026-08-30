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

package org.lineageos.settings.other;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import org.lineageos.settings.R;
import org.lineageos.settings.thermal.ThermaldUtils;

public class OtherSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private SwitchPreference mMglruPreference;
    private SwitchPreference mPerformancePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.other_settings);

        mMglruPreference = (SwitchPreference) findPreference(
                MglruUtils.MGLRU_ENABLE_KEY);
        if (mMglruPreference != null) {
            mMglruPreference.setChecked(MglruUtils.isEnabled());
            mMglruPreference.setOnPreferenceChangeListener(this);
        }

        mPerformancePreference = (SwitchPreference) findPreference(
                ThermaldUtils.PERF_MODE_ENABLE_KEY);
        if (mPerformancePreference != null) {
            mPerformancePreference.setChecked(
                    ThermaldUtils.isPerformanceModeEnabled(getActivity()));
            mPerformancePreference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (MglruUtils.MGLRU_ENABLE_KEY.equals(preference.getKey())) {
            final boolean enabled = (Boolean) newValue;
            // Run the sysfs write off the UI thread like every other
            // feature in this app.
            new Thread(() -> {
                boolean ok = MglruUtils.applyEnabled(enabled);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (ok) {
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .putBoolean(MglruUtils.MGLRU_CONFIGURED_KEY, true)
                                .putBoolean(MglruUtils.MGLRU_ENABLE_KEY, enabled)
                                .apply();
                    } else {
                        Toast.makeText(getActivity(),
                                R.string.mglru_apply_failed,
                                Toast.LENGTH_LONG).show();
                        if (mMglruPreference != null) {
                            mMglruPreference.setChecked(!enabled);
                        }
                    }
                });
            }).start();
            return true;
        }
        if (ThermaldUtils.PERF_MODE_ENABLE_KEY.equals(preference.getKey())) {
            final boolean enabled = (Boolean) newValue;
            new Thread(() -> {
                boolean ok = ThermaldUtils.applyEnabled(enabled);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (ok) {
                        SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(getActivity());
                        prefs.edit()
                                .putBoolean(ThermaldUtils.PERF_MODE_ENABLE_KEY, enabled)
                                .apply();
                    } else {
                        Toast.makeText(getActivity(),
                                R.string.performance_apply_failed,
                                Toast.LENGTH_LONG).show();
                        if (mPerformancePreference != null) {
                            mPerformancePreference.setChecked(!enabled);
                        }
                    }
                });
            }).start();
            return true;
        }
        return false;
    }
}