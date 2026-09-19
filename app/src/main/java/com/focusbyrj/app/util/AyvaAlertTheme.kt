package com.focusbyrj.app.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Elegant color presets designed for dark theme contrast.
 * Deep jewel background + bright pastel header + pure white text.
 */
data class ColorPalettePreset(
    val id: String,
    val name: String,
    val bgHex: String,
    val strokeHex: String,
    val headerHex: String,
    val accentHex: String
)

object AyvaColorPresets {
    val PRESETS = listOf(
        ColorPalettePreset("teal", "Emerald Teal", "#0F766E", "#14B8A6", "#CCFBF1", "#0D9488"),
        ColorPalettePreset("violet", "Royal Violet", "#6D28D9", "#8B5CF6", "#EDE9FE", "#7C3AED"),
        ColorPalettePreset("amber", "Warm Amber", "#C2410C", "#F97316", "#FFEDD5", "#EA580C"),
        ColorPalettePreset("indigo", "Midnight Indigo", "#3730A3", "#6366F1", "#E0E7FF", "#4338CA"),
        ColorPalettePreset("crimson", "Crimson Rose", "#BE123C", "#F43F5E", "#FFE4E6", "#E11D48"),
        ColorPalettePreset("cerulean", "Cerulean Blue", "#0284C7", "#38BDF8", "#E0F2FE", "#0284C7"),
        ColorPalettePreset("forest", "Forest Jade", "#047857", "#10B981", "#D1FAE5", "#059669"),
        ColorPalettePreset("magenta", "Deep Magenta", "#A21CAF", "#E879F9", "#FAE8FF", "#C026D3"),
        ColorPalettePreset("slate", "Charcoal Slate", "#334155", "#64748B", "#F1F5F9", "#475569")
    )

    fun getPresetById(id: String): ColorPalettePreset? = PRESETS.firstOrNull { it.id == id }
    fun getPresetByBgHex(hex: String): ColorPalettePreset? = PRESETS.firstOrNull { it.bgHex.equals(hex, ignoreCase = true) }
}

/**
 * Persistent Manager for Ayva Alert Categories, Notification toggles, and customized color themes.
 */
enum class AyvaAlertCategory(
    val categoryKey: String,
    val channelId: String,
    val channelName: String,
    val defaultTitle: String,
    val defaultBgHex: String,
    val defaultStrokeHex: String,
    val defaultHeaderTextHex: String,
    val defaultBodyTextHex: String,
    val defaultAccentNotificationHex: String,
    val iconName: String,
    val description: String
) {
    BRIEFING(
        categoryKey = "briefing",
        channelId = "ayva_briefings_channel",
        channelName = "Ayva Daily Briefings",
        defaultTitle = "Ayva Briefing",
        defaultBgHex = "#0F766E", // Deep Teal
        defaultStrokeHex = "#14B8A6",
        defaultHeaderTextHex = "#CCFBF1", // Mint Ice
        defaultBodyTextHex = "#FFFFFF",
        defaultAccentNotificationHex = "#0D9488",
        iconName = "WbSunny",
        description = "Morning and evening daily agendas, vocabulary drips, and recap digests"
    ),

    DRILL_PRACTICE(
        categoryKey = "drill_practice",
        channelId = "ayva_drills_channel",
        channelName = "Ayva Brain Drills & Practice",
        defaultTitle = "Ayva Practice",
        defaultBgHex = "#6D28D9", // Deep Royal Violet
        defaultStrokeHex = "#8B5CF6",
        defaultHeaderTextHex = "#EDE9FE", // Light Lavender
        defaultBodyTextHex = "#FFFFFF",
        defaultAccentNotificationHex = "#7C3AED",
        iconName = "Psychology",
        description = "Flashcard drills, mental math challenges, quiz reminders & streak nudges"
    ),

    EXCESSIVE_USAGE(
        categoryKey = "excessive_usage",
        channelId = "ayva_usage_caution_channel",
        channelName = "Ayva Screen Time Alerts",
        defaultTitle = "Ayva Screen Reminder",
        defaultBgHex = "#C2410C", // Deep Burnt Amber / Rust Orange
        defaultStrokeHex = "#F97316",
        defaultHeaderTextHex = "#FFEDD5", // Light Peach
        defaultBodyTextHex = "#FFFFFF",
        defaultAccentNotificationHex = "#EA580C",
        iconName = "HourglassBottom",
        description = "Gentle nudges when continuous screen time or single-app usage is high"
    ),

    BEDTIME_SLEEP(
        categoryKey = "bedtime_sleep",
        channelId = "ayva_bedtime_channel",
        channelName = "Ayva Sleep & Wind-Down",
        defaultTitle = "Ayva Wind-Down",
        defaultBgHex = "#3730A3", // Deep Midnight Indigo
        defaultStrokeHex = "#6366F1",
        defaultHeaderTextHex = "#E0E7FF", // Soft Sky/Indigo Mist
        defaultBodyTextHex = "#FFFFFF",
        defaultAccentNotificationHex = "#4338CA",
        iconName = "Bedtime",
        description = "Late-night screen reminders and restful wind-down suggestions"
    ),

    CRITICAL_LIMIT(
        categoryKey = "critical_limit",
        channelId = "ayva_interventions_channel",
        channelName = "Ayva App Interventions",
        defaultTitle = "Ayva Focus Alert",
        defaultBgHex = "#BE123C", // Deep Rich Crimson Rose
        defaultStrokeHex = "#F43F5E",
        defaultHeaderTextHex = "#FFE4E6", // Soft Rose Petal
        defaultBodyTextHex = "#FFFFFF",
        defaultAccentNotificationHex = "#E11D48",
        iconName = "Block",
        description = "Strict app block alerts, daily usage limits reached, and emergency focus"
    ),

    GENERAL(
        categoryKey = "general",
        channelId = "ayva_bubble_fg_channel",
        channelName = "Ayva Floating Bubble",
        defaultTitle = "Ayva",
        defaultBgHex = "#0284C7", // Cerulean Sky Blue
        defaultStrokeHex = "#38BDF8",
        defaultHeaderTextHex = "#E0F2FE", // Soft Arctic Ice
        defaultBodyTextHex = "#FFFFFF",
        defaultAccentNotificationHex = "#0284C7",
        iconName = "ChatBubble",
        description = "General assistant responses, task updates, and standard notifications"
    );

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("alert_enabled_$categoryKey", true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("alert_enabled_$categoryKey", enabled).apply()
    }

    fun getBgHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("alert_bg_$categoryKey", defaultBgHex) ?: defaultBgHex
    }

    fun getStrokeHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("alert_stroke_$categoryKey", defaultStrokeHex) ?: defaultStrokeHex
    }

    fun getHeaderTextHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("alert_header_$categoryKey", defaultHeaderTextHex) ?: defaultHeaderTextHex
    }

    fun getBodyTextHex(context: Context): String {
        return defaultBodyTextHex
    }

    fun getAccentNotificationHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("alert_accent_$categoryKey", defaultAccentNotificationHex) ?: defaultAccentNotificationHex
    }

    fun setCustomColors(context: Context, preset: ColorPalettePreset) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("alert_bg_$categoryKey", preset.bgHex)
            .putString("alert_stroke_$categoryKey", preset.strokeHex)
            .putString("alert_header_$categoryKey", preset.headerHex)
            .putString("alert_accent_$categoryKey", preset.accentHex)
            .apply()
    }

    fun resetToDefault(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("alert_bg_$categoryKey")
            .remove("alert_stroke_$categoryKey")
            .remove("alert_header_$categoryKey")
            .remove("alert_accent_$categoryKey")
            .remove("alert_enabled_$categoryKey")
            .apply()
    }

    fun getParsedBgColor(context: Context): Int = try {
        Color.parseColor(getBgHex(context))
    } catch (e: Exception) {
        Color.parseColor(defaultBgHex)
    }

    fun getParsedStrokeColor(context: Context): Int = try {
        Color.parseColor(getStrokeHex(context))
    } catch (e: Exception) {
        Color.parseColor(defaultStrokeHex)
    }

    fun getParsedHeaderColor(context: Context): Int = try {
        Color.parseColor(getHeaderTextHex(context))
    } catch (e: Exception) {
        Color.parseColor(defaultHeaderTextHex)
    }

    fun getParsedBodyColor(context: Context): Int = Color.parseColor(defaultBodyTextHex)

    fun getParsedNotificationAccent(context: Context): Int = try {
        Color.parseColor(getAccentNotificationHex(context))
    } catch (e: Exception) {
        Color.parseColor(defaultAccentNotificationHex)
    }

    fun getComposeNotificationAccent(context: Context): ComposeColor =
        ComposeColor(getParsedNotificationAccent(context))

    val parsedBgColor: Int get() = Color.parseColor(defaultBgHex)
    val parsedStrokeColor: Int get() = Color.parseColor(defaultStrokeHex)
    val parsedHeaderColor: Int get() = Color.parseColor(defaultHeaderTextHex)
    val parsedBodyColor: Int get() = Color.parseColor(defaultBodyTextHex)
    val parsedNotificationAccent: Int get() = Color.parseColor(defaultAccentNotificationHex)

    val composeNotificationAccent: ComposeColor get() = ComposeColor(parsedNotificationAccent)

    companion object {
        const val PREFS_NAME = "ayva_alert_color_prefs"

        /**
         * Pre-creates and registers all categorical channels in Android NotificationManager.
         */
        fun createNotificationChannels(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
                entries.forEach { category ->
                    val importance = when (category) {
                        CRITICAL_LIMIT -> android.app.NotificationManager.IMPORTANCE_HIGH
                        EXCESSIVE_USAGE, BEDTIME_SLEEP -> android.app.NotificationManager.IMPORTANCE_HIGH
                        DRILL_PRACTICE, BRIEFING -> android.app.NotificationManager.IMPORTANCE_DEFAULT
                        GENERAL -> android.app.NotificationManager.IMPORTANCE_MIN
                    }
                    val channel = android.app.NotificationChannel(category.channelId, category.channelName, importance).apply {
                        description = category.description
                        enableLights(true)
                        lightColor = category.getParsedNotificationAccent(context)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
            }
        }

        /**
         * Contextually infers the category from text content or message attributes.
         */
        fun infer(
            text: String,
            isMorning: Boolean = false,
            isEvening: Boolean = false,
            isDrill: Boolean = false,
            isStreakPrompt: Boolean = false,
            messageId: String = ""
        ): AyvaAlertCategory {
            val lower = text.lowercase()

            if (isDrill || isStreakPrompt || messageId.startsWith("drill_") || messageId.startsWith("arithmetic_") ||
                messageId.startsWith("streak_prompt_") || lower.contains("brain workout") || lower.contains("arithmetic practice") ||
                lower.contains("quiz") || lower.contains("focus streak") || lower.contains("drill")
            ) {
                return DRILL_PRACTICE
            }

            if (isMorning || isEvening || messageId.startsWith("morning_") || messageId.startsWith("evening_") ||
                lower.contains("morning brief") || lower.contains("evening brief") || lower.contains("daily recap") ||
                lower.contains("vocabulary drip") || lower.contains("good morning! let's build your vocab") ||
                lower.contains("good evening! time for your nightly vocab")
            ) {
                return BRIEFING
            }

            if (lower.contains("late-night") || lower.contains("past your bedtime") || lower.contains("unwind") ||
                lower.contains("sleep") || lower.contains("bedtime") || lower.contains("night owl") ||
                lower.contains("screens at night") || lower.contains("rest well")
            ) {
                return BEDTIME_SLEEP
            }

            if (lower.contains("screen time") || lower.contains("active on your phone") || lower.contains("continuous minutes") ||
                lower.contains("take a quick screen break") || lower.contains("digital break") || lower.contains("unplug for a brief walk") ||
                lower.contains("2 hours straight") || lower.contains("90 continuous minutes")
            ) {
                return EXCESSIVE_USAGE
            }

            if (lower.contains("limit reached") || lower.contains("blocked") || lower.contains("intervention") ||
                lower.contains("strictly locked")
            ) {
                return CRITICAL_LIMIT
            }

            return GENERAL
        }
    }
}
