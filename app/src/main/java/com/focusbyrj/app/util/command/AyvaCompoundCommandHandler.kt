/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.util.command

import android.content.Context
import com.focusbyrj.app.util.UsageStatsHelper
import java.util.regex.Pattern

object AyvaCompoundCommandHandler {

    /**
     * Splits compound queries into separate executable commands if conjunctions are detected.
     * E.g. "add buy groceries 5pm and lock youtube" -> ["add buy groceries 5pm", "lock youtube"]
     * Protects non-command phrases like "mac and cheese", "bread and butter".
     */
    fun splitCompoundCommands(input: String): List<String> {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        val conjunctionRegex = Pattern.compile("(?i)\\s+(?:and then|then|and|&)\\s+")
        val parts = conjunctionRegex.split(trimmed).map { it.trim() }.filter { it.isNotBlank() }

        if (parts.size <= 1) return listOf(trimmed)

        // Verify if part 2 looks like an actionable command or task
        val actionableVerbs = listOf(
            "add ", "create ", "remind ", "task ", "todo ",
            "block ", "lock ", "restrict ", "limit ", "unblock ", "unlock ",
            "show ", "view ", "open ", "summary", "profile", "drill", "math",
            "reschedule ", "complete ", "delete ", "cancel ", "start ", "stop ",
            "routine ", "routines ", "schedules "
        )

        val secondPartLower = parts[1].lowercase()
        val isPart2Actionable = actionableVerbs.any { secondPartLower.startsWith(it) || secondPartLower == it.trim() }

        return if (isPart2Actionable) {
            parts
        } else {
            listOf(trimmed)
        }
    }

    /**
     * Answers reverse telemetry app usage queries.
     * E.g. "how much time on YouTube today?", "instagram usage"
     */
    fun queryAppUsageTelemetry(context: Context, query: String): String? {
        val lower = query.lowercase().trim()
        val isUsageQuery = lower.contains("usage") || lower.contains("screen time") ||
                lower.contains("time on ") || lower.contains("time did i spend") ||
                lower.contains("how long did i use") || lower.contains("how much time")

        if (!isUsageQuery) return null

        if (!UsageStatsHelper.hasUsageStatsPermission(context)) {
            return "⚠️ **Usage Access Permission Required**\n\nAyva needs Usage Access permission to read live app screen time. Please enable it in Settings."
        }

        val stats = UsageStatsHelper.getTodayUsageStats(context)

        // Check if query is about total screen time
        val isTotalQuery = lower.contains("total") || lower == "screen time" || lower == "screen time today" ||
                lower == "how much screen time today" || lower == "usage today"
        if (isTotalQuery) {
            val totalMs = stats.sumOf { it.timeInForegroundMs }
            val totalHrs = totalMs / 3600000L
            val totalMins = (totalMs % 3600000L) / 60000L
            val topApps = stats.take(3).joinToString("\n") {
                val mins = it.timeInForegroundMs / 60000L
                val h = mins / 60
                val m = mins % 60
                val tStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                "• **${it.appName}**: $tStr"
            }
            return "📱 **Today's Screen Time**: **${totalHrs}h ${totalMins}m**\n\n**Top Apps**:\n$topApps"
        }

        // Search for specific app name in query
        val appQuery = lower.replace(Regex("(?i)^(how much time (?:did i spend )?on |how long did i use |time on |usage of |usage for |what is my usage on )"), "")
            .replace(Regex("(?i)\\s+(today|yesterday|this week)\\??$"), "")
            .replace("?", "").trim()

        if (appQuery.isBlank()) return null

        val matched = stats.find {
            it.appName.contains(appQuery, ignoreCase = true) ||
            appQuery.contains(it.appName.lowercase()) ||
            it.packageName.contains(appQuery, ignoreCase = true)
        }

        return if (matched != null) {
            val mins = matched.timeInForegroundMs / 60000L
            val hrs = mins / 60
            val remMins = mins % 60
            val timeStr = if (hrs > 0) "${hrs}h ${remMins}m" else "${remMins}m"
            "⏱️ You spent **$timeStr** on **${matched.appName}** today."
        } else {
            "✨ You haven't used **$appQuery** today (0m foreground time recorded)."
        }
    }

    /**
     * Extracts time-limited block duration in minutes from input.
     * E.g. "block instagram for 30m" -> 30, "lock youtube for 2 hours" -> 120
     */
    fun extractTimeLimitMinutes(input: String): Int? {
        val matcher = Pattern.compile("(?i)(?:for|limit\\s+to)\\s+(\\d+)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours)?").matcher(input)
        if (matcher.find()) {
            val amount = matcher.group(1)?.toIntOrNull() ?: return null
            val unit = matcher.group(2)?.lowercase() ?: "m"
            return if (unit.startsWith("h")) {
                amount * 60
            } else {
                amount
            }
        }
        return null
    }
}
