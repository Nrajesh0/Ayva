package com.focusbyrj.app.util.assistant

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.util.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Event-based usage, explicitly unavailable when Android gives no observable data. */
object AssistantUsage {
    data class Report(val available: Boolean, val durations: Map<String, Long> = emptyMap(), val message: String = "")
    fun day(context: Context, offset: Int, sameTime: Boolean = false): Report {
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) return Report(false, message = "Usage Access is missing.")
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return Report(false, message = "Android usage service is unavailable.")
        val now = Calendar.getInstance()
        val start = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val end = if (offset == 0) now.timeInMillis else if (sameTime) (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }.timeInMillis
            else (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset + 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        return try {
            val events = manager.queryEvents(start - 86_400_000L, end) ?: return Report(false, message = "Android returned no usage events.")
            val event = UsageEvents.Event(); val totals = mutableMapOf<String, Long>(); var current: String? = null; var opened = start; var observed = false
            fun close(at: Long) { val pkg = current ?: return; val length = at.coerceAtMost(end) - opened.coerceAtLeast(start); if (length > 0) totals[pkg] = (totals[pkg] ?: 0L) + length }
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.timeStamp >= start && event.eventType in setOf(UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.MOVE_TO_BACKGROUND)) observed = true
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> { if (current != event.packageName) { close(event.timeStamp); current = event.packageName; opened = event.timeStamp } }
                    UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_STOPPED -> if (current == event.packageName) { close(event.timeStamp); current = null }
                    UsageEvents.Event.DEVICE_STARTUP -> { current = null } // A missing shutdown must not turn offline time into usage.
                    UsageEvents.Event.DEVICE_SHUTDOWN,
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> { close(event.timeStamp); current = null }
                }
            }
            close(end)
            val homes = context.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0).mapNotNull { it.activityInfo?.packageName }.toSet()
            val visible = totals.filterKeys { it != context.packageName && it !in homes && it !in setOf("android", "com.android.systemui", "com.android.settings") }
            if (!observed && visible.isEmpty()) Report(false, message = "Android returned no activity events for that period; usage is unknown.") else Report(true, visible)
        } catch (_: Exception) { Report(false, message = "Usage data could not be read from Android.") }
    }
    fun label(context: Context, pkg: String) = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    fun duration(ms: Long) = "${ms / 60_000}m ${ms / 1000 % 60}s"
}

object AssistantDeviceFeatures {
    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.trim().trimEnd('.', '!', '?'); val lower = text.lowercase()
        if (lower in setOf("check device health", "diagnose reminders", "why didn't my reminder arrive", "why didn't my reminder arrive?", "why are apps not blocking", "diagnose blocking", "check permissions")) {
            val usage = UsageStatsHelper.hasUsageStatsPermission(context)
            val overlay = Settings.canDrawOverlays(context)
            val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() && PermissionUtils.hasNotificationPermission(context)
            val alarms = if (android.os.Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager)?.canScheduleExactAlarms() else true
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val battery = power?.isIgnoringBatteryOptimizations(context.packageName)
            @Suppress("DEPRECATION")
            val running = runCatching { (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getRunningServices(100).any { it.service.className == "com.focusbyrj.app.service.FocusBlockerService" } }.getOrNull()
            state.switchTopic("diagnostics")
            return FeatureTurn.Reply("Device checks (permissions and services):\n• Usage Access: ${if (usage) "allowed" else "missing"}\n• Display over other apps: ${if (overlay) "allowed" else "missing"}\n• Notifications: ${if (notifications) "allowed" else "disabled"}\n• Exact alarms: ${when (alarms) { true -> "allowed"; false -> "not allowed; delivery may be delayed"; null -> "unknown" }}\n• Battery optimization exemption: ${when (battery) { true -> "allowed"; false -> "not exempt"; null -> "unknown" }}\n${if (running != null) "• Blocker service: ${if (running) "listed by Android" else "not listed by Android (not proof of absence)"}\n" else ""}These checks cannot prove why a past alert failed. Check its saved due time, reminder settings and device notification channels too.", listOf(TalkAction.NavigateAppScreen("security", "Open permissions"), TalkAction.NavigateAppScreen("settings", "Reminder settings")))
        }
        Regex("(?i)^(?:why is (.+?) blocked|what blocks (.+)|show blocking rules for (.+))$").matchEntire(text)?.let { match ->
            val name = match.groupValues.drop(1).first { it.isNotBlank() }
            val restrictions = app.database.appRestrictionDao().getAllRestrictionsSync()
            val candidates = (AssistantFeatureLanguage.apps(context) + restrictions.map { it.packageName to it.appName }).distinctBy { it.first }.filter { it.first.equals(name, true) || it.second.equals(name, true) }
            if (candidates.size != 1) return FeatureTurn.Reply("Name one installed app exactly, or use its package name. Say ‘show apps’ to find it.")
            val pkg = candidates.single().first; val rule = restrictions.firstOrNull { it.packageName == pkg }
            val schedules = app.database.scheduleDao().getAllSchedulesSync().filter { pkg in it.appsToBlock.split(',') }
            val active = schedules.filter { it.isActiveAt() }
            FocusSessionManager.refresh(context)
            val lines = mutableListOf<String>()
            if (TemporaryUnlockManager.isUnlocked(context, pkg)) lines += "A temporary relief window is active; that can suppress the overlay right now."
            if (rule?.isRestricted == true) lines += describeRule(rule.restrictionMode, rule.timeLimitMinutes, rule.clickLimitCount, "Manual ${rule.mode} rule")
            active.forEach { lines += "Routine **${it.name}** (${it.mode}), ${AssistantFeatureLanguage.timeText(it.startHour * 60 + it.startMinute, it.endHour * 60 + it.endMinute)}: " + describeRule(it.restrictionMode, it.timeLimitMinutes, it.clickLimitCount, "saved rule") }
            if (FocusSessionManager.active.value && rule != null) lines += "The active focus session also protects this configured app, even if its manual restriction toggle is off."
            if (lines.isEmpty()) lines += "No manual block, active routine or focus-session rule was found for this app."
            val usage = if (AssistantUsage.day(context, 0).available) runCatching { UsageStatsHelper.getTodayUsageMinutesForPackage(context, pkg) to UsageStatsHelper.getTodayLaunchCountForPackage(context, pkg) }.getOrNull() else null
            if (rule?.restrictionMode in setOf("TIME_LIMIT", "CLICK_LIMIT") || active.any { it.restrictionMode != "SIMPLE" }) lines += if (usage == null) "Limit activation is unknown without readable usage data." else "Android readings: ${usage.first} foreground minutes, ${usage.second} opens today. A time limit activates at its threshold; an open limit activates after its threshold."
            lines += "A configured rule is not proof an overlay is currently visible. Permissions, service state and relief windows also matter."
            state.switchTopic("blocking"); return FeatureTurn.Reply("**${candidates.single().second}**\n" + lines.joinToString("\n"), listOf(TalkAction.AskQuery("check device health", "Check device health")))
        }
        if (lower in setOf("compare my screen time", "compare today and yesterday", "what distracted me most today", "show my usage", "show my screen time")) {
            state.switchTopic("usage"); state.featureState?.remove("usageApp")
            val today = AssistantUsage.day(context, 0)
            if (!today.available) return FeatureTurn.Reply(today.message + " I won't treat unavailable usage as zero.", listOf(TalkAction.OpenSystemSetting(Settings.ACTION_USAGE_ACCESS_SETTINGS, "Usage Access")))
            val top = today.durations.entries.sortedByDescending { it.value }.take(5)
            state.switchTopic("usage")
            if (state.featureState == null) state.featureState = JSONObject()
            if (top.isNotEmpty()) state.featureState!!.put("usageApp", JSONObject().put("package", top.first().key).put("at", System.currentTimeMillis())) else state.featureState!!.remove("usageApp")
            val yesterday = if (lower.startsWith("compare")) AssistantUsage.day(context, -1, sameTime = true) else null
            val comparison = if (yesterday == null) "" else if (!yesterday.available) "\nYesterday's comparison is unavailable: ${yesterday.message}" else {
                val difference = today.durations.values.sum() - yesterday.durations.values.sum()
                "\nCompared with yesterday up to the same local time: ${AssistantUsage.duration(kotlin.math.abs(difference))} ${if (difference >= 0) "more" else "less"} foreground usage."
            }
            return FeatureTurn.Reply("Foreground usage reconstructed from Android events today: ${AssistantUsage.duration(today.durations.values.sum())}.\n" + top.joinToString("\n") { "• ${AssistantUsage.label(context, it.key)} — ${AssistantUsage.duration(it.value)}" } + comparison + "\nEvent-based estimates can differ from Settings, especially with split-screen or missing OEM events. Usage does not tell me whether an app was a distraction. You can choose a daily limit for the leading app.", if (top.isEmpty()) emptyList() else listOf(TalkAction.AskQuery("set a daily limit for app ${top.first().key} to 30 minutes", "Preview 30-minute daily limit")))
        }
        val limit = Regex("(?i)^set (?:a )?daily limit for (?:app )?(.+?) to (\\d+) minutes?$").matchEntire(text)
        if (lower == "restrict that app" || limit != null) {
            val target = limit?.groupValues?.get(1)
            val ref = state.featureState?.optJSONObject("usageApp")
            val pkg = if (target == null || target.equals("that app", true)) ref?.takeIf { state.topic == "usage" && System.currentTimeMillis() - it.optLong("at") in 0..900_000L }?.optString("package") else {
                val catalog = AssistantFeatureLanguage.apps(context)
                if (runCatching { AssistantFeatureActions.requireSafePackage(context, target) }.isSuccess) target else catalog.filter { it.second.equals(target, true) }.singleOrNull()?.first
            }
            if (pkg.isNullOrBlank()) return FeatureTurn.Reply("Choose an app by exact name or package, or request a fresh usage report first.")
            val minutes = limit?.groupValues?.get(2)?.toIntOrNull()
            if (limit != null && (minutes == null || minutes !in 1..1440)) return FeatureTurn.Reply("Use a daily limit of 1–1440 whole minutes.")
            val old = app.database.appRestrictionDao().getRestriction(pkg)
            val draft = (old ?: AppRestriction(pkg, AssistantUsage.label(context, pkg))).copy(isRestricted = true, mode = "HARD", restrictionMode = if (limit == null) "SIMPLE" else "TIME_LIMIT", timeLimitMinutes = minutes ?: 0)
            return FeatureTurn.Preview(listOf(AssistantAction.SetAppRule(draft, old)), "Save **${draft.appName}** as ${if (limit == null) "a HARD manual block with no automatic expiry" else "a HARD daily usage limit of $minutes minutes"}? This is not a temporary timed lock.")
        }
        if (lower in setOf("backup status", "my backup status", "show backup status")) {
            val verified = context.getSharedPreferences("ayva_backup_verification", 0).getLong("last_verified_export_ms", 0)
            val daily = context.getSharedPreferences("auto_backup_scheduler_prefs", 0).getLong("last_daily_backup_ms", 0)
            fun date(t: Long) = DateFormat.getDateTimeInstance().format(Date(t))
            state.switchTopic("backup")
            return FeatureTurn.Reply("Encrypted export: ${if (verified > 0 && verified <= System.currentTimeMillis()) "last verified immediately after creation on ${date(verified)}" else "no verified export receipt recorded on this installation"}.\nLocal daily snapshot: ${if (daily > 0 && daily <= System.currentTimeMillis()) "last reported successful on ${date(daily)}" else "no successful run recorded"}.\nA receipt does not prove the external file still exists or remains readable. Open Backup to select, create or inspect an encrypted archive; passwords and vault contents stay outside chat.", listOf(TalkAction.NavigateAppScreen("security", "Open Backup & Restore")))
        }
        return null
    }
    private fun describeRule(kind: String, time: Int, clicks: Int, origin: String) = when (kind) { "TIME_LIMIT" -> "$origin: daily $time-minute threshold."; "CLICK_LIMIT" -> "$origin: more than $clicks opens."; else -> "$origin: immediate block." }
}
