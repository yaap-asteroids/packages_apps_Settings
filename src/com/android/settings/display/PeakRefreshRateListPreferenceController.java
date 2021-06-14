/*
 * Copyright (C) 2020 The Android Open Source Project
 * Copyright (C) 2021 The LineageOS Project
 * Copyright (C) 2026 YAAP
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
package com.android.settings.display;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.view.Display;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Adapted from LineageOS 8f0888454c1b5653a2d608b4ce2a8f698ad9ab68. Applies a peak cap, not a fixed
 * minimum; normal platform safety/power votes remain effective.
 */
public class PeakRefreshRateListPreferenceController extends BasePreferenceController
    implements LifecycleObserver, OnStart, OnStop, Preference.OnPreferenceChangeListener {
  private static final String AUTOMATIC = "auto";
  private final Handler mHandler;
  private final DisplayManager mDisplayManager;
  private final DeviceConfigDisplaySettings mDeviceConfigDisplaySettings;
  private final ContentObserver mSettingsObserver;
  private final DisplayManager.DisplayListener mDisplayListener;
  private ListPreference mListPreference;
  private boolean mStarted;

  public PeakRefreshRateListPreferenceController(Context context, String key) {
    super(context, key);
    mHandler = new Handler(context.getMainLooper());
    mDisplayManager = context.getSystemService(DisplayManager.class);
    mDeviceConfigDisplaySettings = new DeviceConfigDisplaySettings();
    mSettingsObserver =
        new ContentObserver(mHandler) {
          @Override
          public void onChange(boolean selfChange) {
            refresh();
          }
        };
    mDisplayListener =
        new DisplayManager.DisplayListener() {
          @Override
          public void onDisplayAdded(int displayId) {
            refresh();
          }

          @Override
          public void onDisplayRemoved(int displayId) {
            refresh();
          }

          @Override
          public void onDisplayChanged(int displayId) {
            if (displayId == Display.DEFAULT_DISPLAY) refresh();
          }
        };
  }

  List<Float> getSupportedRates() {
    Display display =
        mDisplayManager == null ? null : mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
    if (display == null) return List.of();
    Display.Mode current = display.getMode();
    List<Float> rates = new ArrayList<>();
    for (Display.Mode mode : display.getSupportedModes()) {
      if (current == null
          || mode.getPhysicalWidth() == current.getPhysicalWidth()
              && mode.getPhysicalHeight() == current.getPhysicalHeight()) {
        rates.add(mode.getRefreshRate());
      }
    }
    float[] values = new float[rates.size()];
    for (int i = 0; i < values.length; i++) values[i] = rates.get(i);
    return RefreshRateOptions.supportedRates(values);
  }

  @Override
  public void displayPreference(PreferenceScreen screen) {
    super.displayPreference(screen);
    mListPreference = screen.findPreference(getPreferenceKey());
    if (mListPreference == null) return;
    // Only explicit user selection may write the system setting, not XML inflation/binding.
    mListPreference.setPersistent(false);
    mListPreference.setOnPreferenceChangeListener(this);
    refresh();
  }

  @Override
  public int getAvailabilityStatus() {
    return mContext.getResources().getBoolean(R.bool.config_show_smooth_display)
            && getSupportedRates().size() > 1
        ? AVAILABLE
        : UNSUPPORTED_ON_DEVICE;
  }

  @Override
  public void updateState(Preference preference) {
    if (!(preference instanceof ListPreference list)) return;
    List<Float> rates = getSupportedRates();
    CharSequence[] entries = new CharSequence[rates.size() + 1];
    CharSequence[] values = new CharSequence[entries.length];
    entries[0] = mContext.getString(R.string.refresh_rate_automatic);
    values[0] = AUTOMATIC;
    for (int i = 0; i < rates.size(); i++) {
      entries[i + 1] = formatRate(rates.get(i));
      // A numeric choice remains a finite cap, including the highest current rate.
      values[i + 1] = Float.toString(rates.get(i));
    }
    list.setEntries(entries);
    list.setEntryValues(values);
    list.setVisible(getAvailabilityStatus() == AVAILABLE);
    String stored =
        Settings.System.getString(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE);
    float peak = parseRate(stored);
    String selected = null;
    for (float rate : rates) {
      if (RefreshRateOptions.matches(rate, peak)) selected = Float.toString(rate);
    }
    boolean automatic = stored == null;
    boolean unrestricted = peak == Float.POSITIVE_INFINITY && !automatic;
    if (automatic) selected = AUTOMATIC;
    if (unrestricted && !rates.isEmpty()) selected = Float.toString(rates.get(rates.size() - 1));
    // Unknown external caps have an accurate summary, without highlighting a false choice.
    list.setValue(selected);
    CharSequence cap =
        automatic
            ? mContext.getString(R.string.refresh_rate_automatic_summary)
            : unrestricted
                ? mContext.getString(R.string.refresh_rate_unrestricted, formatRate(peak))
                : Float.isFinite(peak) && peak > 0
                    ? formatRate(peak)
                    : mContext.getString(R.string.refresh_rate_external_override);
    list.setSummary(mContext.getString(R.string.refresh_rate_cap_summary, cap));
  }

  private float parseRate(String value) {
    try {
      return Float.parseFloat(value);
    } catch (NumberFormatException | NullPointerException e) {
      return Float.NaN;
    }
  }

  private String formatRate(float value) {
    if (!Float.isFinite(value) || value <= 0) {
      List<Float> rates = getSupportedRates();
      if (rates.isEmpty()) return mContext.getString(R.string.refresh_rate_automatic);
      value = rates.get(rates.size() - 1);
    }
    NumberFormat formatter = NumberFormat.getNumberInstance();
    formatter.setMaximumFractionDigits(2);
    return mContext.getString(R.string.refresh_rate_hz, formatter.format(value));
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    if (!(newValue instanceof String value)) return false;
    if (AUTOMATIC.equals(value)) {
      boolean written =
          Settings.System.putString(
              mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, null);
      if (written) refresh();
      return written;
    }
    float rate = parseRate(value);
    if (!Float.isFinite(rate) || !getSupportedRates().contains(rate)) return false;
    float min =
        Settings.System.getFloat(
            mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, 0f);
    // The platform takes max(min, peak). Clear a conflicting developer minimum only on choice.
    boolean resetMin = min > rate;
    if (resetMin
        && !Settings.System.putFloat(
            mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, 0f)) return false;
    boolean written =
        Settings.System.putFloat(
            mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, rate);
    if (!written && resetMin)
      Settings.System.putFloat(
          mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, min);
    if (written) refresh();
    return written;
  }

  private void refresh() {
    if (mListPreference != null) updateState(mListPreference);
  }

  @Override
  public void onStart() {
    if (mStarted) return;
    mStarted = true;
    mDeviceConfigDisplaySettings.startListening();
    mContext
        .getContentResolver()
        .registerContentObserver(
            Settings.System.getUriFor(Settings.System.PEAK_REFRESH_RATE), false, mSettingsObserver);
    if (mDisplayManager != null)
      mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
    refresh();
  }

  @Override
  public void onStop() {
    if (!mStarted) return;
    mStarted = false;
    mDeviceConfigDisplaySettings.stopListening();
    mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
    if (mDisplayManager != null) mDisplayManager.unregisterDisplayListener(mDisplayListener);
  }

  private class DeviceConfigDisplaySettings
      implements DeviceConfig.OnPropertiesChangedListener, Executor {
    void startListening() {
      DeviceConfig.addOnPropertiesChangedListener(
          DeviceConfig.NAMESPACE_DISPLAY_MANAGER, this, this);
    }

    void stopListening() {
      DeviceConfig.removeOnPropertiesChangedListener(this);
    }

    @Override
    public void onPropertiesChanged(DeviceConfig.Properties properties) {
      refresh();
    }

    @Override
    public void execute(Runnable runnable) {
      mHandler.post(runnable);
    }
  }
}
