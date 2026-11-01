package com.focusbyrj.app.util

/** Shared constraints preserve values accepted by older UI/chat versions. */
object SettingsPreferenceRules {
    val softWait = 5..120
    val softRelief = 1..60
    val bubbleAutoHide = 1..300
    val reminderInterval = 1..1440
    val reminderSteps = listOf(1, 5, 10, 15, 30, 60, 120, 180, 240, 300, 360, 720, 1440)

    fun nextReminderInterval(current: Int, increase: Boolean): Int =
        if (increase) reminderSteps.firstOrNull { it > current } ?: reminderInterval.last
        else reminderSteps.lastOrNull { it < current } ?: reminderInterval.first

    fun datePreview(input: String, enabled: Boolean) =
        if (enabled && input.isNotBlank()) SmartDateParser.parse(input) else null

    val settingIds = mapOf(
        "soft_lock_duration" to "focus.wait", "soft_unlock_duration" to "focus.relief",
        "persistent_reminder_interval" to "notifications.repeat", "auto_hide_duration_sec" to "bubble.auto_hide",
        "app_theme_mode" to "appearance.theme", "app_theme_color" to "appearance.accent", "overlay_theme_mode" to "appearance.block_theme",
        "task_notification_style" to "notifications.tasks", "default_start_tab" to "focus.launch", "bubble_enabled" to "bubble.enabled",
        "hide_in_landscape" to "bubble.landscape", "vacation_mode" to "bubble.vacation", "secure_recents" to "privacy.screenshots",
        "routine_notifications" to "notifications.routines", "streak_notification_enabled" to "bubble.practice",
        "streak_notification_time" to "bubble.practice_time", "morning_brief_time" to "bubble.morning", "evening_brief_time" to "bubble.evening",
        "auto_hide_enabled" to "bubble.auto_hide", "smart_regex_enabled" to "bubble.date_preview", "bubble_hidden_opacity" to "bubble.opacity",
        "bubble_hidden_amount" to "bubble.depth", "bubble_glow_intensity" to "bubble.glow", "habit_top_notifications" to "notifications.habits"
    )

    fun routeFor(key: String): String = when (key) {
        "soft_lock_duration", "soft_unlock_duration", "default_start_tab" -> "settings_focus"
        "persistent_reminder_interval", "routine_notifications", "task_notification_style", "habit_top_notifications" -> "settings_notifications"
        "secure_recents" -> "security"
        "app_theme_mode", "app_theme_color", "overlay_theme_mode" -> "settings_appearance"
        else -> "bubble_settings"
    }

    fun taskStyleValue(value: String): String = when (value.lowercase()) {
        "notification only", "notification bar" -> "Notification Only"
        "floating bar", "both", "notification bar + floating popup", "notification + popup" -> "Both"
        else -> error("Choose Notification bar or Notification bar + floating popup")
    }
    fun taskStyleLabel(value: String): String = if (value.equals("Notification Only", true))
        "Notification bar" else "Notification + popup"
}
