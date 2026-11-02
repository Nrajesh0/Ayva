# Battery health widget

Home-screen snapshot widget, also available through **Settings → Widgets → Battery health**. A tap refreshes all battery-widget instances. Android schedules periodic updates at a requested 30-minute interval; power-saving and launcher policies can delay them. The displayed time identifies the last refresh. No monitoring service, wake lock, exact alarm, network permission or charging-history collection is added.

## Meaning of the readings

- **Maximum capacity:** system-reported estimated full-charge capacity relative to rated capacity. On Android 16+, try the public, flagged `BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH` property (10). The app still compiles with API 35, so this documented constant is locally named and runtime-gated to API 36. No hidden API/reflection or privileged `BATTERY_STATS` permission is used. Feature support varies by manufacturer/build. Accept only 1–100; missing, denied, zero, sentinel and out-of-range results render **Unavailable**. Android 12–15 always use that fallback.
- **Charge:** the actual charge percentage from Android's sticky battery broadcast. Invalid/missing level or scale renders **Unavailable**; no default percentage is invented.
- **Condition:** Android's battery condition, such as Good, Overheated or Cold. **Good does not mean 100% maximum capacity.** Unknown condition renders **Unavailable**.

On a POCO X6 Pro / HyperOS, the maximum-capacity value may therefore remain Unavailable even if Xiaomi's privileged Settings app can display it. The user explicitly selected this honest fallback rather than charging-history estimation. No claim is made that this phone exposes the Android 16 property without a real-device check.

## Existing helper repair

The previously unused `DeviceStatsHelper` generated health percentages from voltage and hard-coded sample readings when battery data was missing. Those values were not measurements. It now uses nullable readings and the documented system property. Negative measured temperatures are preserved, and missing voltage/temperature remain unavailable. It no longer claims that the app knows whether system performance management has been applied. Repository callers were checked; the helper had no production UI consumers before this widget.

## Scope

The new widget has automatic light/dark colors, a screen-reader description containing its readings, a launcher preview and no configuration requirement. Existing notes/tasks/countdown widget behavior is retained. No changes to planning, encryption, backups, or archived conversation assets are part of this feature. Previous runtime-audit fixes remain in the working tree.

## Verification

**25 targeted checks passed**, including the new widget/reading checks and existing security/settings regressions. Debug and optimized release builds passed; `git diff --check` passed. The packaged APK retains minimum Android 12 and adds neither privileged battery-statistics nor Internet permission. All 262 protected conversation/dictionary assets remain unchanged. No physical-device/HyperOS verification was available. Included in version **2.0.36 / 136**; these checks were recorded before the release version bump.

Log: `/workspace/artifacts/battery-widget-verified.log`; machine-readable result: `/workspace/artifacts/battery-widget-verification.json`.

Coverage includes Android 12 and 16 readings, unsupported/sentinel/out-of-range capacity values, missing/absent battery data, overflow-safe percentage math, valid negative temperatures, actual RemoteViews rendering, refreshing multiple instances from a widget tap, and light/dark resources. The existing security-audit battery tests now assert unavailable readings rather than fabricated defaults.

Source reference: [Android 16 BatteryManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/os/BatteryManager.java), `BATTERY_PROPERTY_STATE_OF_HEALTH`.
