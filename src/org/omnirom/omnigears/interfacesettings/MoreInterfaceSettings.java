/*
 *  Copyright (C) 2015 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.omnirom.omnigears.interfacesettings;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.util.omni.PackageUtils;
import com.android.internal.util.omni.DeviceUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import java.util.List;
import java.util.ArrayList;

public class MoreInterfaceSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {
    private static final String TAG = "MoreInterfaceSettings";

    private static final String CATEGORY_WEATHER = "weather_category";
    private static final String WEATHER_ICON_PACK = "weather_icon_pack";
    private static final String STATUS_BAR_HEADER_WEATHER = "status_bar_header_weather";
    private static final String DEFAULT_WEATHER_ICON_PACKAGE = "org.omnirom.omnijaws";
    private static final String WEATHER_SERVICE_PACKAGE = "org.omnirom.omnijaws";
    private static final String CHRONUS_ICON_PACK_INTENT = "com.dvtonder.chronus.ICON_PACK";
    private static final String LOCK_CLOCK_PACKAGE="com.cyanogenmod.lockclock";
    private static final String DASHBOARD_COLUMNS = "dashboard_columns";
    private static final String DASHBOARD_DIVIDER_SHOW = "dashboard_divider_show";

    private PreferenceCategory mWeatherCategory;
    private ListPreference mWeatherIconPack;
    private CheckBoxPreference mHeaderWeather;
    private ListPreference mDashboardColumns;
    private CheckBoxPreference mDashBoardDividerShow;

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.OMNI_SETTINGS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.more_interface_settings);

        final PreferenceScreen prefScreen = getPreferenceScreen();

        mWeatherCategory = (PreferenceCategory) prefScreen.findPreference(CATEGORY_WEATHER);
        if (mWeatherCategory != null && !isOmniJawsServiceInstalled()) {
            prefScreen.removePreference(mWeatherCategory);
        } else {
            String settingHeaderPackage = Settings.System.getString(getContentResolver(),
                    Settings.System.STATUS_BAR_WEATHER_ICON_PACK);
            if (settingHeaderPackage == null) {
                settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE;
            }
            mWeatherIconPack = (ListPreference) findPreference(WEATHER_ICON_PACK);

            List<String> entries = new ArrayList<String>();
            List<String> values = new ArrayList<String>();
            getAvailableWeatherIconPacks(entries, values);
            mWeatherIconPack.setEntries(entries.toArray(new String[entries.size()]));
            mWeatherIconPack.setEntryValues(values.toArray(new String[values.size()]));

            int valueIndex = mWeatherIconPack.findIndexOfValue(settingHeaderPackage);
            if (valueIndex == -1) {
                // no longer found
                settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE;
                Settings.System.putString(getContentResolver(),
                        Settings.System.STATUS_BAR_WEATHER_ICON_PACK, settingHeaderPackage);
                valueIndex = mWeatherIconPack.findIndexOfValue(settingHeaderPackage);
            }
            mWeatherIconPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
            mWeatherIconPack.setSummary(mWeatherIconPack.getEntry());
            mWeatherIconPack.setOnPreferenceChangeListener(this);

            mHeaderWeather = (CheckBoxPreference) findPreference(STATUS_BAR_HEADER_WEATHER);
        }
        mDashboardColumns = (ListPreference) findPreference(DASHBOARD_COLUMNS);
        int dashboardValue = getResources().getInteger(R.integer.dashboard_num_columns);

        final boolean isPhone = DeviceUtils.isPhone(getActivity());
        if (isPhone) {
            // layout-land has a value of 2 but we dont want this to be the default
            // for phones so set 1 as the default to display
            dashboardValue = 1;
        }
        mDashboardColumns.setEntries(getResources().getStringArray(!isPhone ?
                R.array.dashboard_columns_tablet_entries : R.array.dashboard_columns_phone_entries));
        mDashboardColumns.setEntryValues(getResources().getStringArray(!isPhone ?
                R.array.dashboard_columns_tablet_values : R.array.dashboard_columns_phone_values));

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (!prefs.contains(DASHBOARD_COLUMNS)) {
            mDashboardColumns.setValue(Integer.toString(dashboardValue));
        }
        mDashboardColumns.setSummary(mDashboardColumns.getEntry());
        mDashboardColumns.setOnPreferenceChangeListener(this);

        mDashBoardDividerShow = (CheckBoxPreference) findPreference(DASHBOARD_DIVIDER_SHOW);
        if (!prefs.contains(DASHBOARD_DIVIDER_SHOW)) {
            mDashBoardDividerShow.setChecked(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWeatherSettings();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mWeatherIconPack) {
            String value = (String) objValue;
            Settings.System.putString(getContentResolver(),
                    Settings.System.STATUS_BAR_WEATHER_ICON_PACK, value);
            int valueIndex = mWeatherIconPack.findIndexOfValue(value);
            mWeatherIconPack.setSummary(mWeatherIconPack.getEntries()[valueIndex]);
            return true;
        } else if (preference == mDashboardColumns) {
            String value = (String) objValue;
            int valueIndex = mDashboardColumns.findIndexOfValue(value);
            mDashboardColumns.setSummary(mDashboardColumns.getEntries()[valueIndex]);
            return true;
        }
        return false;
    }

    private boolean isOmniJawsServiceInstalled() {
        return PackageUtils.isAvailableApp(WEATHER_SERVICE_PACKAGE, getActivity());
    }

    private boolean isLockClockInstalled() {
        return PackageUtils.isAvailableApp(LOCK_CLOCK_PACKAGE, getActivity());
    }

    private void getAvailableWeatherIconPacks(List<String> entries, List<String> values) {
        Intent i = new Intent();
        PackageManager packageManager = getPackageManager();
        i.setAction("org.omnirom.WeatherIconPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                values.add(0, r.activityInfo.name);
            } else {
                values.add(r.activityInfo.name);
            }
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                entries.add(0, label);
            } else {
                entries.add(label);
            }
        }
        i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(CHRONUS_ICON_PACK_INTENT);
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            values.add(packageName + ".weather");
            String label = r.activityInfo.loadLabel(getPackageManager()).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            entries.add(label);
        }
        if (isLockClockInstalled()) {
            values.add(LOCK_CLOCK_PACKAGE + ".weather");
            values.add(LOCK_CLOCK_PACKAGE + ".weather_color");
            values.add(LOCK_CLOCK_PACKAGE + ".weather_vclouds");

            entries.add("LockClock (white)");
            entries.add("LockClock (color)");
            entries.add("LockClock (vclouds)");
        }
    }

    private void updateWeatherSettings() {
        final boolean weatherEnabled = isOmniJawsEnabled();
        if (mHeaderWeather.isChecked() && !weatherEnabled) {
            // disable if service got disabled
            Settings.System.putInt(getContentResolver(), Settings.System.STATUS_BAR_HEADER_WEATHER, 0);
            mHeaderWeather.setChecked(false);
        }
        mHeaderWeather.setEnabled(weatherEnabled);
        mWeatherIconPack.setEnabled(weatherEnabled);
    }

    private boolean isOmniJawsEnabled() {
        final Uri SETTINGS_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/settings");

        final String[] SETTINGS_PROJECTION = new String[] {
            "enabled"
        };

        final Cursor c = getContentResolver().query(SETTINGS_URI, SETTINGS_PROJECTION,
                null, null, null);
        if (c != null) {
            int count = c.getCount();
            if (count == 1) {
                c.moveToPosition(0);
                boolean enabled = c.getInt(0) == 1;
                return enabled;
            }
        }
        return true;
    }

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.more_interface_settings;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    ArrayList<String> result = new ArrayList<String>();
                    return result;
                }
            };
}

