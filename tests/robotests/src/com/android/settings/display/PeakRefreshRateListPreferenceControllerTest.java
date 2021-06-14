/*
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.testing.TestableContext;
import android.view.Display;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceScreen;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.settings.core.BasePreferenceController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Regression coverage for the adapted Lineage peak-rate selector. */
@RunWith(RobolectricTestRunner.class)
public class PeakRefreshRateListPreferenceControllerTest {
  @Rule
  public final TestableContext mContext =
      new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

  @Mock private DisplayManager mDisplayManager;
  @Mock private Display mDisplay;
  private PeakRefreshRateListPreferenceController mController;
  private ListPreference mPreference;

  private Display.Mode mode(int width, int height, float rate) {
    return new Display.Mode(1, width, height, rate);
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mContext.addMockSystemService(DisplayManager.class, mDisplayManager);
    when(mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);
    when(mDisplay.getMode()).thenReturn(mode(1080, 2392, 120f));
    when(mDisplay.getSupportedModes())
        .thenReturn(
            new Display.Mode[] {
              mode(1080, 2392, 120f),
              mode(1080, 2392, 90f),
              mode(1080, 2392, 60f),
              mode(1080, 2392, 90f),
              mode(1440, 3200, 144f)
            });
    mController = new PeakRefreshRateListPreferenceController(mContext, "max_refresh_rate");
    mPreference = new ListPreference(RuntimeEnvironment.application);
    mPreference.setKey("max_refresh_rate");
    Settings.System.putString(
        mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, null);
    Settings.System.putString(
        mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, null);
  }

  @Test
  public void entriesAreSupportedCurrentResolutionAndDeduplicated() {
    mController.updateState(mPreference);
    assertThat(mController.getSupportedRates()).containsExactly(60f, 90f, 120f).inOrder();
    assertThat(mPreference.getEntryValues())
        .asList()
        .containsExactly("auto", "60.0", "90.0", "120.0")
        .inOrder();
  }

  @Test
  public void bindingDoesNotWriteDefaultPolicy() {
    PreferenceScreen screen =
        new androidx.preference.PreferenceManager(mContext).createPreferenceScreen(mContext);
    screen.addPreference(mPreference);
    mController.displayPreference(screen);
    assertThat(
            Settings.System.getString(
                mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE))
        .isNull();
    assertThat(mPreference.isPersistent()).isFalse();
  }

  @Test
  public void explicit90HzCapsPeakWithoutPinningMinimum() {
    assertThat(mController.onPreferenceChange(mPreference, "90.0")).isTrue();
    assertThat(
            Settings.System.getFloat(
                mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, -1))
        .isEqualTo(90f);
    assertThat(
            Settings.System.getFloat(
                mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, 0))
        .isEqualTo(0f);
  }

  @Test
  public void conflictingMinimumIsLoweredOnlyOnExplicitSelection() {
    Settings.System.putFloat(
        mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, Float.POSITIVE_INFINITY);
    assertThat(mController.onPreferenceChange(mPreference, "60.0")).isTrue();
    assertThat(
            Settings.System.getFloat(
                mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE, -1))
        .isEqualTo(0f);
  }

  @Test
  public void highestNumericChoiceIsFinite() {
    assertThat(mController.onPreferenceChange(mPreference, "120.0")).isTrue();
    assertThat(
            Settings.System.getFloat(
                mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, -1))
        .isEqualTo(120f);
  }

  @Test
  public void automaticRemovesOverride() {
    Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, 90f);
    assertThat(mController.onPreferenceChange(mPreference, "auto")).isTrue();
    assertThat(
            Settings.System.getString(
                mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE))
        .isNull();
  }

  @Test
  public void unsupportedOrMalformedSelectionDoesNotWrite() {
    for (String value : new String[] {"144.0", "bad", "Infinity", "NaN", "-1.0"}) {
      assertThat(mController.onPreferenceChange(mPreference, value)).isFalse();
    }
    assertThat(
            Settings.System.getString(
                mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE))
        .isNull();
  }

  @Test
  public void absentDisplayHidesControlWithoutCrashing() {
    when(mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(null);
    assertThat(mController.getAvailabilityStatus())
        .isEqualTo(BasePreferenceController.UNSUPPORTED_ON_DEVICE);
    mController.updateState(mPreference);
    assertThat(mPreference.getEntryValues()).asList().containsExactly("auto");
  }

  @Test
  public void storedCapRemainsSelectedAfterRefresh() {
    Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, 90f);
    mController.updateState(mPreference);
    assertThat(mPreference.getValue()).isEqualTo("90.0");
    assertThat(mPreference.getSummary().toString()).contains("90");
  }

  @Test
  public void unknownExternalCapIsNotFalselyHighlighted() {
    Settings.System.putFloat(mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, 75f);
    mController.updateState(mPreference);
    assertThat(mPreference.getValue()).isNull();
    assertThat(mPreference.getSummary().toString()).contains("75");
  }

  @Test
  public void inheritedInfinityIsNotLabeledAsDefaultPolicy() {
    Settings.System.putFloat(
        mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, Float.POSITIVE_INFINITY);
    mController.updateState(mPreference);
    assertThat(mPreference.getValue()).isEqualTo("120.0");
    assertThat(mPreference.getSummary().toString()).contains("Maximum available");
    assertThat(
            Settings.System.getFloat(
                mContext.getContentResolver(), Settings.System.PEAK_REFRESH_RATE, -1))
        .isPositiveInfinity();
  }

  @Test
  public void fractionalRatesNormalizeWithoutInventingModes() {
    assertThat(RefreshRateOptions.supportedRates(new float[] {60, 59.94f, 90, Float.NaN, -1, 90}))
        .containsExactly(59.94f, 90f)
        .inOrder();
  }
}
