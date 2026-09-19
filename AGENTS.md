-e 
# CRITICAL SYSTEM RULE: App Blocking Logic
- **NEVER** modify the core logic of `BlockOverlayManager.kt` or `FocusBlockerService.kt` to use Activity launches (`startActivity`) as the primary blocking mechanism.
- The app **MUST** use the `WindowManager.addView` direct overlay method (`TYPE_APPLICATION_OVERLAY`) to bypass Android 14+ Background Activity Launch (BAL) restrictions.
- If the user requests UI changes to the block screen, you must modify the programmatic View construction (ScrollView, LinearLayout, etc.) inside `BlockOverlayManager.kt` directly. Do NOT convert it to Jetpack Compose or an XML Activity.
- If a request might break this overlay logic, **STOP**, refuse the change, and remind the user of the BAL Android 16 restriction errors.
