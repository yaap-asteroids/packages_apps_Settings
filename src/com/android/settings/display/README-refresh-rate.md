# YAAP refresh-rate selector

Adapted from LineageOS Android 17 commit
[8f0888454c1b5653a2d608b4ce2a8f698ad9ab68](https://github.com/LineageOS/android_packages_apps_Settings/commit/8f0888454c1b5653a2d608b4ce2a8f698ad9ab68),
“Settings: Add peak refresh rate list preference,” authored by Michael Bestas
<mkbestas@lineageos.org>, 2021-06-14.

YAAP differences:

- Replaces the boolean Smooth Display widget, including its Catalyst hierarchy
  entry. This screen remains an incomplete hybrid hierarchy, so the distinct
  `max_refresh_rate` XML controller stays active.
- Offers only reported modes at the current default-display resolution; filters
  invalid and duplicate/fractional equivalent rates.
- Automatic removes the peak override and leaves the device policy in control.
  Numeric options, including the highest, write a finite peak cap rather than
  forcing a high minimum or making “120 Hz” mean unlimited after mode changes.
- Preserves inherited Infinity as highest-available, clearly distinguished from
  Automatic/default; binding never changes the stored setting.
- Clears a conflicting developer minimum only after explicit numeric selection;
  attempts to restore it if writing the new peak cap fails.
- Observes display changes and system settings while the screen is active, and
  removes observers on stop.
- Uses the existing `config_show_smooth_display` device gate, not a new default-on
  feature across every product. No panel timing, HWC or safety policy is modified.

The cap is a user preference: thermal, battery-saver, app requests and platform
render/physical-rate policies can use lower rates. This is not a fixed-FPS switch
or a guarantee of panel cadence. Asteroids panel source advertises 60/90/120 Hz;
actual entries depend on the booted display's advertised modes.

`PeakRefreshRateListPreferenceControllerTest` covers binding without writes,
supported-rate filtering, numeric caps, conflicting minima, Automatic, invalid
input, missing display, external overrides and inherited Infinity. Focused host
normalization checks and stub-assisted controller compilation do not substitute
for full Settings/Robolectric or on-device validation.
