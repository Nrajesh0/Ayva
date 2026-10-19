package com.focusbyrj.app.util.assistant

import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.*
import com.focusbyrj.app.util.FocusSessionManager
import org.json.JSONObject

internal object RoutineJson {
    fun encode(s: FocusSchedule) = JSONObject().put("id", s.id).put("name", s.name).put("startHour", s.startHour).put("startMinute", s.startMinute)
        .put("endHour", s.endHour).put("endMinute", s.endMinute).put("days", s.daysOfWeek).put("mode", s.mode).put("restriction", s.restrictionMode)
        .put("time", s.timeLimitMinutes).put("clicks", s.clickLimitCount).put("apps", s.appsToBlock).put("enabled", s.isEnabled)
    fun decode(j: JSONObject) = FocusSchedule(j.getInt("id"), j.getString("name"), j.getInt("startHour"), j.getInt("startMinute"), j.getInt("endHour"), j.getInt("endMinute"), j.getString("days"), j.getString("mode"), j.getString("restriction"), j.getInt("time"), j.getInt("clicks"), j.getString("apps"), j.getBoolean("enabled"))
}
internal object AppRuleJson {
    fun encode(s: AppRestriction) = JSONObject().put("package", s.packageName).put("name", s.appName).put("restricted", s.isRestricted).put("mode", s.mode)
        .put("restriction", s.restrictionMode).put("time", s.timeLimitMinutes).put("clicks", s.clickLimitCount).put("quote", s.customQuote)
    fun decode(j: JSONObject) = AppRestriction(j.getString("package"), j.getString("name"), j.getBoolean("restricted"), j.getString("mode"), j.getString("restriction"), j.getInt("time"), j.getInt("clicks"), j.getString("quote"))
}

/** Shared execution and undo for feature edits, always revalidated against their preview. */
object AssistantFeatureActions {
    suspend fun execute(context: Context, app: FocusApplication, action: AssistantAction): AssistantActionResult = when (action) {
        is AssistantAction.SaveRoutine -> {
            val before = action.expected
            val draft = action.draft
            require(draft.name.isNotBlank() && draft.name.length <= 80 && draft.name.none(Char::isISOControl)) { "Use a routine name of 1–80 characters" }
            require(draft.startHour in 0..23 && draft.endHour in 0..23 && draft.startMinute in 0..59 && draft.endMinute in 0..59)
            require(draft.startHour * 60 + draft.startMinute != draft.endHour * 60 + draft.endMinute) { "Start and end must differ" }
            val days = draft.daysOfWeek.split(',').map { it.toIntOrNull() }
            require(days.isNotEmpty() && days.all { it != null && it in 1..7 } && days.distinct().size == days.size) { "Choose valid weekdays" }
            require(draft.mode in setOf("HARD", "SOFT") && draft.restrictionMode in setOf("SIMPLE", "TIME_LIMIT", "CLICK_LIMIT"))
            if (draft.restrictionMode == "TIME_LIMIT") require(draft.timeLimitMinutes in 1..1440)
            if (draft.restrictionMode == "CLICK_LIMIT") require(draft.clickLimitCount in 1..10000)
            val packages = draft.appsToBlock.split(',').filter { it.isNotBlank() }
            require(packages.isNotEmpty() && packages.size <= 50 && packages.distinct().size == packages.size)
            packages.forEach { requireSafePackage(context, it) }
            val after = app.database.withTransaction {
                if (before == null) require(draft.id == 0) else {
                    require(draft.id == before.id && app.database.scheduleDao().getScheduleById(before.id) == before) { "That routine changed. Review it again" }
                    require(AssistantActionExecutor.canDisableRoutine(context, before)) { "An active strict routine or focus session cannot be edited from chat" }
                }
                require(app.database.scheduleDao().getAllSchedulesSync().none { it.id != draft.id && it.name.equals(draft.name, true) }) { "A routine already uses that name" }
                app.database.scheduleDao().insertSchedule(draft)
                if (draft.id != 0) draft else app.database.scheduleDao().getAllSchedulesSync().single { it.name == draft.name }
            }
            AssistantActionResult("Saved routine **${after.name}**. ${if (!after.isEnabled) "It is currently disabled." else if (after.isActiveAt()) "It is active now." else "It uses its saved scheduled windows."}", undo = listOf(JSONObject().put("kind", "routine_edit").put("before", before?.let(RoutineJson::encode) ?: JSONObject.NULL).put("after", RoutineJson.encode(after))))
        }
        is AssistantAction.SaveDictionaryWord -> {
            val sense = AssistantDictionaryStore.lookup(context, action.key).singleOrNull { it.id == action.senseId } ?: error("That dictionary sense is unavailable")
            val dao = app.vocabDatabase.vocabDao()
            val id = app.vocabDatabase.withTransaction {
                val link = app.vocabDatabase.dictionaryBookmarkDao().get(sense.id)
                require(link == null || link.wordKey == action.key)
                val currentId = link?.owsId ?: action.expectedId
                require(currentId == action.expectedId) { "That word was saved while we were talking. Look it up again" }
                val current = currentId?.let { dao.getOwsById(it) }
                require(current?.let { it.isBookmarked ?: 0 } == action.expectedBookmark) { "That bookmark changed. Review it again" }
                val targetId = if (current != null) {
                    require(current.term == sense.word && current.definition == sense.definition) { "That saved definition changed" }
                    val existingId = current.id ?: error("Saved vocabulary ID missing"); dao.setOwsBookmarked(existingId, 1); existingId
                } else {
                    require(action.expectedId == null)
                    dao.insertDictionaryOws(com.focusbyrj.app.data.Ows(term = sense.word, definition = sense.definition, partOfSpeech = sense.part, isBookmarked = 1)).toInt()
                }
                if (link == null) app.vocabDatabase.dictionaryBookmarkDao().insert(com.focusbyrj.app.data.DictionaryBookmark(sense.id, action.key, targetId))
                targetId
            }
            AssistantActionResult("Saved **${sense.word}** to vocabulary bookmarks. This doesn't mark it learned. Wordset source and license remain available through ‘dictionary credits’.",
                undo = listOf(JSONObject().put("kind", "vocab_bookmark").put("type", "ows").put("id", id).put("before", action.expectedBookmark ?: 0).put("after", 1)))
        }
        is AssistantAction.BookmarkWord -> {
            require(action.type in setOf("idiom", "ows") && action.id > 0 && action.expected in 0..1)
            val dao = app.vocabDatabase.vocabDao()
            app.vocabDatabase.withTransaction {
                val current = if (action.type == "idiom") dao.getIdiomById(action.id)?.let { it.isBookmarked ?: 0 } else dao.getOwsById(action.id)?.let { it.isBookmarked ?: 0 }
                require(current != null && current == action.expected) { "That word or bookmark changed. Ask again" }
                if (action.type == "idiom") dao.setIdiomBookmarked(action.id, if (action.bookmarked) 1 else 0) else dao.setOwsBookmarked(action.id, if (action.bookmarked) 1 else 0)
            }
            AssistantActionResult(if (action.bookmarked) "Bookmarked that saved word." else "Removed that word's bookmark.", undo = listOf(JSONObject().put("kind", "vocab_bookmark").put("type", action.type).put("id", action.id).put("before", action.expected).put("after", if (action.bookmarked) 1 else 0)))
        }
        is AssistantAction.SetAppRules -> {
            require(action.rules.size in 1..500 && action.rules.map { it.draft.packageName }.distinct().size == action.rules.size) { "Choose up to 500 distinct apps" }
            val dao = app.database.appRestrictionDao()
            val protected = com.focusbyrj.app.util.AppCatalog.protectedPackages(context)
            for (rule in action.rules) {
                if (rule.draft.isRestricted) com.focusbyrj.app.util.AppCatalog.requireEligible(context, rule.draft.packageName, protected)
                else require(rule.expected != null && rule.expected.packageName == rule.draft.packageName) { "Only a saved manual rule can be unlocked" }
                require(rule.expected == null || rule.expected.packageName == rule.draft.packageName)
                require(rule.draft.mode in setOf("HARD", "SOFT") && rule.draft.restrictionMode in setOf("SIMPLE", "TIME_LIMIT", "CLICK_LIMIT"))
                if (rule.draft.restrictionMode == "TIME_LIMIT") require(rule.draft.timeLimitMinutes in 1..1440)
                if (rule.draft.restrictionMode == "CLICK_LIMIT") require(rule.draft.clickLimitCount in 1..10000)
            }
            app.database.withTransaction {
                // Validate every snapshot and protection before the first write: bulk requests are atomic.
                requireCanChangeApps(context, app, action.rules.filter { it.expected != null }.map { it.draft.packageName }.toSet())
                for (rule in action.rules) {
                    require(dao.getRestriction(rule.draft.packageName) == rule.expected) { "${rule.draft.appName}'s rule changed. Review the request again" }
                }
                dao.insertRestrictions(action.rules.map { it.draft })
            }
            val locked = action.rules.count { it.draft.isRestricted }
            AssistantActionResult("Updated ${action.rules.size} manual app rules: $locked locked, ${action.rules.size - locked} unlocked. Active routine rules still apply.", undo = listOf(JSONObject().put("kind", "app_rules").put("rules", org.json.JSONArray(action.rules.map { rule ->
                JSONObject().put("before", rule.expected?.let(AppRuleJson::encode) ?: JSONObject.NULL).put("after", AppRuleJson.encode(rule.draft))
            }))))
        }
        is AssistantAction.SetAppRule -> {
            val draft = action.draft
            requireSafePackage(context, draft.packageName)
            require(draft.isRestricted && draft.mode in setOf("HARD", "SOFT") && draft.restrictionMode in setOf("SIMPLE", "TIME_LIMIT"))
            if (draft.restrictionMode == "TIME_LIMIT") require(draft.timeLimitMinutes in 1..1440) { "Choose a daily limit of 1–1440 minutes" }
            app.database.withTransaction {
                val current = app.database.appRestrictionDao().getRestriction(draft.packageName)
                require(current == action.expected) { "That app rule changed. Review it again" }
                if (current != null) requireCanChangeApp(context, app, current.packageName)
                app.database.appRestrictionDao().insertRestriction(draft)
            }
            AssistantActionResult("Saved **${draft.appName}**: ${if (draft.restrictionMode == "TIME_LIMIT") "daily usage limit ${draft.timeLimitMinutes} minutes (not a timed unlock)" else "blocked until you change the rule"}.", undo = listOf(JSONObject().put("kind", "app_rule").put("before", action.expected?.let(AppRuleJson::encode) ?: JSONObject.NULL).put("after", AppRuleJson.encode(draft))))
        }
        else -> error("Unsupported feature edit")
    }

    suspend fun undo(context: Context, app: FocusApplication, record: JSONObject) {
        when (record.getString("kind")) {
            "routine_edit" -> app.database.withTransaction {
                val after = RoutineJson.decode(record.getJSONObject("after"))
                require(app.database.scheduleDao().getScheduleById(after.id) == after) { "That routine changed since this edit" }
                require(AssistantActionExecutor.canDisableRoutine(context, after)) { "An active strict routine or focus session cannot be undone from chat" }
                if (record.isNull("before")) app.database.scheduleDao().deleteSchedule(after) else {
                    val before = RoutineJson.decode(record.getJSONObject("before"))
                    require(app.database.scheduleDao().getAllSchedulesSync().none { it.id != before.id && it.name.equals(before.name, true) }) { "The previous routine name is now in use" }
                    app.database.scheduleDao().insertSchedule(before)
                }
            }
            "app_rules" -> app.database.withTransaction {
                val rows = record.getJSONArray("rules")
                require(rows.length() in 1..500)
                val after = (0 until rows.length()).map { AppRuleJson.decode(rows.getJSONObject(it).getJSONObject("after")) }
                requireCanChangeApps(context, app, after.map { it.packageName }.toSet())
                for (rule in after) {
                    require(app.database.appRestrictionDao().getRestriction(rule.packageName) == rule) { "An app rule changed since this action. Nothing was undone" }
                }
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    if (row.isNull("before")) app.database.appRestrictionDao().deleteRestriction(after[i].packageName)
                    else app.database.appRestrictionDao().insertRestriction(AppRuleJson.decode(row.getJSONObject("before")))
                }
            }
            "app_rule" -> app.database.withTransaction {
                val after = AppRuleJson.decode(record.getJSONObject("after"))
                require(app.database.appRestrictionDao().getRestriction(after.packageName) == after) { "That app rule changed since this edit" }
                requireCanChangeApp(context, app, after.packageName)
                if (record.isNull("before")) app.database.appRestrictionDao().deleteRestriction(after.packageName) else app.database.appRestrictionDao().insertRestriction(AppRuleJson.decode(record.getJSONObject("before")))
            }
            "vocab_bookmark" -> app.vocabDatabase.withTransaction {
                val dao = app.vocabDatabase.vocabDao(); val id = record.getInt("id"); val idiom = record.getString("type") == "idiom"
                val current = if (idiom) dao.getIdiomById(id)?.let { it.isBookmarked ?: 0 } else dao.getOwsById(id)?.let { it.isBookmarked ?: 0 }
                require(current == record.getInt("after")) { "That bookmark changed since this edit" }
                if (idiom) dao.setIdiomBookmarked(id, record.getInt("before")) else dao.setOwsBookmarked(id, record.getInt("before"))
            }
        }
    }
    private suspend fun requireCanChangeApps(context: Context, app: FocusApplication, packages: Set<String>) {
        if (packages.isEmpty()) return
        FocusSessionManager.refresh(context)
        require(!FocusSessionManager.active.value && app.database.scheduleDao().getAllSchedulesSync().none { routine -> routine.mode == "HARD" && routine.isActiveAt() && routine.appsToBlock.split(',').any { it in packages } }) { "An active focus session or strict routine protects an app in this selection. Nothing changed" }
    }
    private suspend fun requireCanChangeApp(context: Context, app: FocusApplication, pkg: String) {
        FocusSessionManager.refresh(context)
        require(!FocusSessionManager.active.value && app.database.scheduleDao().getAllSchedulesSync().none { it.mode == "HARD" && it.isActiveAt() && pkg in it.appsToBlock.split(',') }) { "An active focus session or strict routine protects that app rule" }
    }
    fun requireSafePackage(context: Context, pkg: String) {
        com.focusbyrj.app.util.AppCatalog.requireEligible(context, pkg)
    }
}
