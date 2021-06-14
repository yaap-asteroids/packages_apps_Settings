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

import java.util.ArrayList;
import java.util.List;

/** Normalizes duplicate/fractional mode rates without inventing unsupported display modes. */
final class RefreshRateOptions {
  private static final float MATCH_TOLERANCE_HZ = 0.1f;

  private RefreshRateOptions() {}

  static List<Float> supportedRates(float[] rates) {
    List<Float> sorted = new ArrayList<>();
    for (float rate : rates) {
      if (Float.isFinite(rate) && rate > 0) sorted.add(rate);
    }
    sorted.sort(Float::compare);
    List<Float> result = new ArrayList<>();
    for (float rate : sorted) {
      if (result.isEmpty() || !matches(result.get(result.size() - 1), rate)) {
        result.add(rate);
      }
    }
    return result;
  }

  static boolean matches(float first, float second) {
    return Math.abs(first - second) < MATCH_TOLERANCE_HZ;
  }

  static boolean isSupported(float value, List<Float> supported) {
    return supported.stream().anyMatch(rate -> matches(rate, value));
  }
}
