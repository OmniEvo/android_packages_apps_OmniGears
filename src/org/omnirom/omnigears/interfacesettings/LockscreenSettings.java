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

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.SearchIndexableResource;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import org.omnirom.omnigears.R;
import org.omnirom.omnigears.preference.AppMultiSelectListPreference;
import org.omnirom.omnigears.preference.FontPreference;
import org.omnirom.omnigears.preference.NumberPickerPreference;
import org.omnirom.omnigears.preference.ColorPickerPreference;
import org.omnirom.omnigears.ui.ShortcutDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


public class LockscreenSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {
    public static final int IMAGE_PICK = 1;

    private static final String KEY_WALLPAPER_SET = "lockscreen_wallpaper_set";
    private static final String KEY_WALLPAPER_CLEAR = "lockscreen_wallpaper_clear";
    private static final String KEY_CLOCK_FONT = "lockscreen_clock_font";
    private static final String KEY_CLOCK_COLOR = "lockscreen_clock_color";
    private static final String KEY_CLOCK_SIZE = "lockscreen_clock_size";
    private static final String KEY_CLOCK_DISPLAY = "lockscreen_clock_display";
    private static final String KEY_CLOCK_DISPLAY_TIME = "lockscreen_clock_display_time";
    private static final String KEY_CLOCK_DISPLAY_DATE = "lockscreen_clock_display_date";
    private static final String KEY_CLOCK_DISPLAY_ALARM = "lockscreen_clock_display_alarm";
    private static final String KEY_SHORTCUTS = "lockscreen_shortcuts";

    private Preference mSetWallpaper;
    private Preference mClearWallpaper;
    private FontPreference mClockFont;
    private NumberPickerPreference mClockSize;
    private ColorPickerPreference mClockColor;
    private CheckBoxPreference mClockDisplayTime;
    private CheckBoxPreference mClockDisplayDate;
    private CheckBoxPreference mClockDisplayAlarm;
    private Preference mShortcuts;

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.OMNI_SETTINGS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lockscreen_settings);

        mSetWallpaper = (Preference) findPreference(KEY_WALLPAPER_SET);
        mClearWallpaper = (Preference) findPreference(KEY_WALLPAPER_CLEAR);

        mClockFont = (FontPreference) findPreference(KEY_CLOCK_FONT);
        mClockFont.setOnPreferenceChangeListener(this);
        mClockColor = (ColorPickerPreference) findPreference(KEY_CLOCK_COLOR);
        mClockColor.setOnPreferenceChangeListener(this);
        mClockSize = (NumberPickerPreference) findPreference(KEY_CLOCK_SIZE);
        mClockSize.setOnPreferenceChangeListener(this);
        mClockSize.setMinValue(40);
        mClockSize.setMaxValue(140);
        mClockDisplayTime = (CheckBoxPreference) findPreference(KEY_CLOCK_DISPLAY_TIME);
        mClockDisplayDate = (CheckBoxPreference) findPreference(KEY_CLOCK_DISPLAY_DATE);
        mClockDisplayAlarm = (CheckBoxPreference) findPreference(KEY_CLOCK_DISPLAY_ALARM);

        ContentResolver resolver = getActivity().getContentResolver();
        int color = Settings.System.getInt(resolver, Settings.System.LOCK_CLOCK_COLOR, Color.WHITE);
        mClockColor.setColor(color);
        String hexColor = String.format("#%08X", color);
        mClockColor.setSummary(hexColor);

        int defaultSize = (int) (getResources().getDimension(com.android.internal.R.dimen.lock_clock_time_font_size)
                / getResources().getDisplayMetrics().density);
        int size = Settings.System.getInt(resolver, Settings.System.LOCK_CLOCK_SIZE, defaultSize);
        mClockSize.setValue(size);
        mClockSize.setSummary(String.valueOf(size));

        String font = Settings.System.getString(resolver, Settings.System.LOCK_CLOCK_FONT);
        if (font != null) {
            mClockFont.setValue(font);
            int valueIndex = mClockFont.findIndexOfValue(font);
            if (valueIndex != -1) {
                mClockFont.setSummary(mClockFont.getEntries()[valueIndex]);
            }
        } else {
            mClockFont.setSummary("sans-serif-light");
        }

        final int clockDisplay = Settings.System.getInt(resolver,
                Settings.System.LOCK_CLOCK_DISPLAY, Settings.System.LOCK_CLOCK_ALL);
        mClockDisplayTime.setChecked((clockDisplay & Settings.System.LOCK_CLOCK_TIME) == Settings.System.LOCK_CLOCK_TIME);
        mClockDisplayDate.setChecked((clockDisplay & Settings.System.LOCK_CLOCK_DATE) == Settings.System.LOCK_CLOCK_DATE);
        mClockDisplayAlarm.setChecked((clockDisplay & Settings.System.LOCK_CLOCK_ALARM) == Settings.System.LOCK_CLOCK_ALARM);

        mShortcuts = findPreference(KEY_SHORTCUTS);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        ContentResolver resolver = getContentResolver();
        if (preference == mSetWallpaper) {
            setKeyguardWallpaper();
            return true;
        } else if (preference == mClearWallpaper) {
            clearKeyguardWallpaper();
            Toast.makeText(getView().getContext(), getString(R.string.reset_lockscreen_wallpaper),
            Toast.LENGTH_LONG).show();
            return true;
        } else if (preference == mClockDisplayTime) {
            Settings.System.putInt(resolver,
                    Settings.System.LOCK_CLOCK_DISPLAY, getCurrentClockDisplayValue());
            return true;
        } else if (preference == mClockDisplayDate) {
            Settings.System.putInt(resolver,
                    Settings.System.LOCK_CLOCK_DISPLAY, getCurrentClockDisplayValue());
            return true;
        } else if (preference == mClockDisplayAlarm) {
            Settings.System.putInt(resolver,
                    Settings.System.LOCK_CLOCK_DISPLAY, getCurrentClockDisplayValue());
            return true;
        } else if (preference == mShortcuts) {
            ShortcutDialog d = new ShortcutDialog(getContext());
            d.show();
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private int getCurrentClockDisplayValue() {
        return (mClockDisplayTime.isChecked() ? Settings.System.LOCK_CLOCK_TIME : 0) +
            (mClockDisplayDate.isChecked() ? Settings.System.LOCK_CLOCK_DATE : 0) +
            (mClockDisplayAlarm.isChecked() ? Settings.System.LOCK_CLOCK_ALARM : 0);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getContentResolver();
        if (preference == mClockFont) {
            String value = (String) newValue;
            int valueIndex = mClockFont.findIndexOfValue(value);
            mClockFont.setSummary(mClockFont.getEntries()[valueIndex]);
            Settings.System.putString(resolver, Settings.System.LOCK_CLOCK_FONT, value);
        } else if (preference == mClockColor) {
            String hexColor = String.format("#%08X", mClockColor.getColor());
            mClockColor.setSummary(hexColor);
            Settings.System.putInt(resolver, Settings.System.LOCK_CLOCK_COLOR, mClockColor.getColor());
        } else if (preference == mClockSize) {
            Integer value = (Integer) newValue;
            mClockSize.setSummary(String.valueOf(value));
            Settings.System.putInt(resolver, Settings.System.LOCK_CLOCK_SIZE, value);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                Intent intent = new Intent();
                intent.setClassName("com.android.wallpapercropper", "com.android.wallpapercropper.WallpaperCropActivity");
                intent.putExtra("keyguardMode", "1");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    private void setKeyguardWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, IMAGE_PICK);
    }

    private void clearKeyguardWallpaper() {
        WallpaperManager wallpaperManager = null;
        wallpaperManager = WallpaperManager.getInstance(getActivity());
        wallpaperManager.clearKeyguardWallpaper();
    }

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.lockscreen_settings;
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
