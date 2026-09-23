-e 
# CRITICAL SYSTEM RULE: App Blocking Logic
- **NEVER** modify the core logic of `BlockOverlayManager.kt` or `FocusBlockerService.kt` to use Activity launches (`startActivity`) as the primary blocking mechanism.
- The app **MUST** use the `WindowManager.addView` direct overlay method (`TYPE_APPLICATION_OVERLAY`) to bypass Android 14+ Background Activity Launch (BAL) restrictions.
- If the user requests UI changes to the block screen, you must modify the programmatic View construction (ScrollView, LinearLayout, etc.) inside `BlockOverlayManager.kt` directly. Do NOT convert it to Jetpack Compose or an XML Activity.
- If a request might break this overlay logic, **STOP**, refuse the change, and remind the user of the BAL Android 16 restriction errors.
# Git Operations Policy
- **NEVER** run `git push` or push commits to GitHub/remote repositories automatically.
- All commits MUST remain local on the user's machine.
- Only run `git push` when the user explicitly instructs: "push to github" or "git push".

# Device Deployment & Testing Policy
- **NEVER** attempt to launch, configure, or wait for Android Virtual Devices (AVD / emulator).
- All on-device testing and installation MUST target the user's connected **physical phone** via ADB (`adb install -r`).
- Unit tests remain local via Robolectric (`./gradlew testDebugUnitTest`).