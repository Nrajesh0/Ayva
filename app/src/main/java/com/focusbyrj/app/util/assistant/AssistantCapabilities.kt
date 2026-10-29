package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.TalkAction

/** Descriptive contracts never grant authority; execution still validates current domain state. */
enum class CapabilityAccess { READ, WRITE, NAVIGATE, CONVERSATION, UNAVAILABLE }
enum class CapabilityReview { NONE, GROUP, ALWAYS }
enum class CapabilityUndo { GUARDED, SAME_DAY_GUARDED, NONE }
enum class AssistantSettingGroup(val id: String) {
    BLOCKING("blocking"), REMINDERS("reminders"), BUBBLE("bubble"), APPEARANCE("appearance"), PRIVACY("privacy")
}

data class AssistantCapability(
    val id: String,
    val domain: String,
    val title: String,
    val access: CapabilityAccess,
    val owner: String,
    val examples: List<String>,
    val required: List<String> = emptyList(),
    val review: CapabilityReview = CapabilityReview.NONE,
    val undo: CapabilityUndo = CapabilityUndo.NONE,
    val limitation: String = ""
)

data class CapabilityAvailability(val available: Boolean?, val reason: String = "")

/** A setting's read, preview, validation, help and UI command share its actual preference schema. */
class AssistantSettingCapability internal constructor(val spec: AssistantPreferenceSpec) {
    val readContract get() = AssistantCapability("settings.read.${spec.key}", "settings", "Read ${spec.title}", CapabilityAccess.READ,
        "AssistantPreferences/${spec.owner}", listOf("/talk explain ${spec.aliases.first()}"))
    val writeContract get() = AssistantCapability("settings.set.${spec.key}", "settings", "Change ${spec.title}", CapabilityAccess.WRITE,
        "AssistantPreferences/${spec.owner}", listOf("/talk change ${spec.aliases.first()}"), listOf("value"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED)
    fun current(context: Context) = AssistantPreferences.read(context, spec)
    fun action(type: String, value: String) = AssistantAction.SetPreference(spec.key, spec.type, AssistantPreferences.validate(spec.key, type, value))
    fun query(type: String, value: String): String {
        val checked = action(type, value).value
        val text = if (spec.type == "boolean") if (checked == "true") "on" else "off" else checked.ifBlank { "random" }
        return "/talk set ${spec.aliases.first()} to $text"
    }
    fun hint(): String = when {
        spec.type == "boolean" -> "Choose On or Off."
        spec.unit == "clock" -> "Choose a time such as 9:30am or 18:00.${if (spec.key == "streak_notification_time") " Or choose random alerts." else ""}"
        spec.range != null -> "Supported: ${spec.range.first}–${spec.range.last} ${spec.unit}."
        else -> "Choices: ${spec.values.joinToString { AssistantPreferences.display(spec, it) }}."
    }
    fun preview(context: Context, value: String): FeatureTurn {
        val proposed = action(spec.type, value)
        val before = current(context)
        if (proposed.value == before) return FeatureTurn.Reply("${spec.title} is already **${AssistantPreferences.display(spec, before)}**. No change is needed.", settingKey = spec.key)
        return FeatureTurn.Preview(listOf(proposed), "Change **${spec.title}**?\nCurrent: ${AssistantPreferences.display(spec, before)}\nProposed: **${AssistantPreferences.display(spec, proposed.value)}**")
    }
}

object AssistantCapabilities {
    val domains: Map<String, String> = linkedMapOf("tasks" to "Tasks", "planning" to "Planning", "habits" to "Habits", "routines" to "Routines",
        "apps" to "App controls", "focus" to "Focus", "learning" to "Learning and vocabulary", "settings" to "Settings",
        "device" to "Permissions and usage", "backup" to "Backup status", "conversation" to "Conversation")

    private fun capability(id: String, domain: String, title: String, access: CapabilityAccess, owner: String, example: String,
        required: List<String> = emptyList(), review: CapabilityReview = CapabilityReview.NONE, undo: CapabilityUndo = CapabilityUndo.NONE, limitation: String = "") =
        AssistantCapability(id, domain, title, access, owner, listOf(example), required, review, undo, limitation)

    // Owners are existing domain implementations. This registry does not reimplement their rules.
    private val featureContracts = listOf(
        capability("tasks.list", "tasks", "Find and select saved tasks", CapabilityAccess.READ, "AssistantTaskLanguage/AssistantDialogue", "show my tasks"),
        capability("tasks.create", "tasks", "Create tasks", CapabilityAccess.WRITE, "AssistantActionExecutor", "buy milk tomorrow", listOf("title"), undo = CapabilityUndo.GUARDED),
        capability("tasks.edit", "tasks", "Edit tasks", CapabilityAccess.WRITE, "AssistantActionExecutor", "move the second task to Friday", listOf("task IDs", "field", "value"), CapabilityReview.GROUP, CapabilityUndo.GUARDED),
        capability("tasks.complete", "tasks", "Complete tasks", CapabilityAccess.WRITE, "AssistantActionExecutor", "complete the first task", listOf("task IDs"), CapabilityReview.GROUP, CapabilityUndo.GUARDED),
        capability("tasks.trash", "tasks", "Move tasks to Trash", CapabilityAccess.WRITE, "AssistantActionExecutor/TaskRecoveryStore", "delete all tasks", listOf("task IDs"), CapabilityReview.GROUP, CapabilityUndo.GUARDED),
        capability("tasks.subtasks", "tasks", "Review checklist edits", CapabilityAccess.WRITE, "AssistantChecklist/AssistantActionExecutor", "add checklist item Pack charger to task Trip preparation", listOf("parent task", "stable checklist IDs", "edit"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED, "Checklist children have no separate reminders; completing a child does not complete its parent."),
        capability("planning.suggest", "planning", "Suggest an order and work blocks from saved tasks", CapabilityAccess.READ, "AssistantPlanning", "plan my day", limitation = "Work blocks are suggestions, not estimates of completion. Saved tasks are unchanged."),
        capability("planning.projects", "planning", "Draft day, exam and trip preparation plans", CapabilityAccess.READ, "AssistantGuidedPlanning", "help me plan my trip", limitation = "Uses supplied steps, an optional shared deadline and budget. No invented syllabus, duration estimates or current travel facts."),
        capability("habits.read", "habits", "Inspect habit goals, schedules and progress", CapabilityAccess.READ, "AssistantHabitConversation", "show my habits"),
        capability("habits.create", "habits", "Create a habit", CapabilityAccess.WRITE, "AssistantActionExecutor", "add habit reading", listOf("title", "daily target"), CapabilityReview.ALWAYS),
        capability("habits.log", "habits", "Log habit progress", CapabilityAccess.WRITE, "AssistantActionExecutor", "log habit reading", listOf("habit ID", "count"), undo = CapabilityUndo.SAME_DAY_GUARDED),
        capability("habits.edit", "habits", "Edit habit goals, schedule and archive state", CapabilityAccess.WRITE, "AssistantHabitEditing", "change habit reading goal to 3", listOf("habit snapshot", "field", "value"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED),
        capability("routines.read", "routines", "Inspect saved routines and schedules", CapabilityAccess.READ, "AssistantRoutineConversation", "show my routines"),
        capability("routines.save", "routines", "Create or edit a routine", CapabilityAccess.WRITE, "AssistantFeatureActions", "create routine Study", listOf("name", "days", "time window", "apps", "mode"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED, "Active strict routines and focus protections still apply."),
        capability("routines.toggle", "routines", "Enable or disable saved routines", CapabilityAccess.WRITE, "AssistantActionExecutor", "enable routine Study", listOf("routine ID", "enabled"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED),
        capability("apps.read", "apps", "Inspect apps and blocking causes", CapabilityAccess.READ, "AssistantAppControl/AssistantDeviceFeatures", "show apps"),
        capability("apps.rule", "apps", "Set a manual app block or daily limit", CapabilityAccess.WRITE, "AssistantFeatureActions", "set a daily limit for app Reader to 30 minutes", listOf("app", "rule", "expected state"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED),
        capability("apps.group", "apps", "Lock or unlock app groups and exceptions", CapabilityAccess.WRITE, "AssistantAppControl/AssistantFeatureActions", "lock all except WhatsApp", listOf("eligible app IDs", "exceptions", "expected rules"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED, "Unlocking manual rules does not disable active routine rules."),
        capability("apps.timed", "apps", "Temporary timed app locks", CapabilityAccess.UNAVAILABLE, "No expiry owner", "", limitation = "Manual locks do not expire automatically. Daily usage limits are a different feature."),
        capability("focus.read", "focus", "Inspect the running countdown", CapabilityAccess.READ, "FocusSessionManager", "time remaining"),
        capability("focus.start", "focus", "Start a focus countdown", CapabilityAccess.WRITE, "FocusSessionManager", "start focus for 25 minutes", listOf("minutes"), limitation = "Cannot replace a running countdown. Does not change existing app restrictions."),
        capability("focus.stop", "focus", "Stop the countdown", CapabilityAccess.WRITE, "FocusSessionManager", "stop focus", review = CapabilityReview.ALWAYS),
        capability("learning.launch", "learning", "Open native practice tests", CapabilityAccess.NAVIGATE, "AssistantLearningLanguage/native test windows", "start a hard drill with 20 questions"),
        capability("learning.results", "learning", "Review actual saved mistakes and results", CapabilityAccess.READ, "AssistantLearningFeatures", "review my mistakes"),
        capability("vocabulary.lookup", "learning", "Look up bundled idioms and one-word substitutions", CapabilityAccess.READ, "AssistantVocabularyFeatures", "look up break the ice", limitation = "Coverage is the existing vocabulary dataset, not a full English dictionary."),
        capability("vocabulary.bookmark", "learning", "Bookmark a vocabulary entry", CapabilityAccess.WRITE, "AssistantFeatureActions", "bookmark that idiom", listOf("entry ID", "entry type", "expected bookmark"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED),
        capability("dictionary.expand", "learning", "Bundled English dictionary and supported sentence patterns", CapabilityAccess.READ, "AssistantDictionary/AssistantDictionaryStore", "what does reluctant mean", limitation = "About 108,000 source entries, with multiple senses and source examples where available. Plain-English alternatives cover selected senses. Sentence explanations cover explicit supported patterns, not arbitrary sentences or sarcasm."),
        capability("dictionary.save", "learning", "Save a dictionary sense to vocabulary bookmarks", CapabilityAccess.WRITE, "AssistantFeatureActions/VocabDatabase", "save this word", listOf("source word key", "sense ID", "current bookmark state"), CapabilityReview.ALWAYS, CapabilityUndo.GUARDED),
        capability("device.read", "device", "Check permissions and device health", CapabilityAccess.READ, "AssistantDeviceFeatures", "check device health", limitation = "Permission state does not prove that Android delivered an overlay or reminder."),
        capability("usage.read", "device", "Inspect local foreground-usage estimates", CapabilityAccess.READ, "AssistantUsage", "show my usage", limitation = "Requires Android usage access. Missing usage remains unknown."),
        capability("device.permissions", "device", "Open native permission settings", CapabilityAccess.NAVIGATE, "TalkAction.OpenSystemSetting", "check permissions"),
        capability("backup.status", "backup", "Read backup success receipts", CapabilityAccess.READ, "AssistantDeviceFeatures", "backup status", limitation = "Reads timestamps only. Chat cannot inspect, create or restore archives."),
        capability("backup.open", "backup", "Open native Backup & Restore", CapabilityAccess.NAVIGATE, "TalkAction.NavigateAppScreen", "/talk backups"),
        capability("conversation.basic", "conversation", "Greetings, limited small talk and conversation repairs", CapabilityAccess.CONVERSATION, "AssistantDialogue/AssistantConversationSupport", "where were we", limitation = "Open-ended conversation and arbitrary reasoning remain limited."),
        capability("personalization.read", "conversation", "Inspect explicit assistant preferences", CapabilityAccess.READ, "AssistantPersonalization", "what do you remember about me", limitation = "Only allowlisted preferences are retained; chat does not build a private biography."),
        capability("personalization.set", "conversation", "Save an explicit assistant preference", CapabilityAccess.WRITE, "AssistantPersonalization", "call me Raj", listOf("allowlisted preference", "value")),
        capability("personalization.forget", "conversation", "Forget assistant preferences", CapabilityAccess.WRITE, "AssistantPersonalization", "forget my preferences", listOf("explicit forget request"), limitation = "Does not delete saved tasks, habits or private data."),
        capability("actions.undo", "conversation", "Undo the last reversible assistant change", CapabilityAccess.WRITE, "AssistantActionExecutor.undo", "undo", listOf("unchanged action receipt"), limitation = "Refuses to overwrite subsequent edits; some actions have no undo."),
        capability("conversation.breathe", "conversation", "Open the existing breathing exercise", CapabilityAccess.NAVIGATE, "AyvaTalkEngine", "breathe"),
        capability("conversation.calculate", "conversation", "Bounded arithmetic and time conversion", CapabilityAccess.READ, "AssistantCalculator", "convert 90 minutes to hours"),
        capability("transcript.preview", "tasks", "Preview tasks from finalized live transcription", CapabilityAccess.READ, "AssistantTranscriptFeatures", "make tasks from my transcript", limitation = "Partial captions never execute actions. Saving tasks needs a reviewed request."),
        capability("transcript.open", "conversation", "Open phone live transcription", CapabilityAccess.NAVIGATE, "OnDeviceTranscriber", "/transcribe", limitation = "Uses the phone speech service, which may process audio online; no model download in Ayva."),
        capability("transcript.capture", "conversation", "Capture new speech on this device", CapabilityAccess.READ, "OnDeviceTranscriber", "/transcribe", limitation = "Uses current microphone input only, never saved attachments. Language support is checked when recording starts."),
        capability("rewards.read", "device", "Inspect saved rewards and streak facts", CapabilityAccess.READ, "AssistantDeviceFeatures", "what can i claim"),
        capability("private_data.excluded", "conversation", "Private Notes and vault data", CapabilityAccess.UNAVAILABLE, "Native private screens only", "", limitation = "Chat cannot read, search, create or change private Notes/vault data. Generic help and opening the native screen do not grant data access.")
    )

    fun setting(key: String): AssistantSettingCapability = AssistantSettingCapability(
        AssistantPreferences.specs.singleOrNull { it.key == key } ?: error("That setting isn't editable from chat"))
    val settings by lazy { AssistantPreferences.specs.map { setting(it.key) } }
    val all by lazy { featureContracts + settings.flatMap { listOf(it.readContract, it.writeContract) } }
    private fun contract(id: String) = featureContracts.single { it.id == id }

    /** Exhaustive mapping forces a deliberate contract whenever a new typed action is added. */
    fun forAction(action: AssistantAction): AssistantCapability = when (action) {
        is AssistantAction.CreateTask -> contract("tasks.create")
        is AssistantAction.EditTasks -> contract(if (action.field == TaskField.CHECKLIST) "tasks.subtasks" else "tasks.edit")
        is AssistantAction.CompleteTasks -> contract("tasks.complete")
        is AssistantAction.TrashTasks -> contract("tasks.trash")
        is AssistantAction.CreateHabit -> contract("habits.create")
        is AssistantAction.LogHabit -> contract("habits.log")
        is AssistantAction.EditHabit -> contract("habits.edit")
        is AssistantAction.SaveRoutine -> contract("routines.save")
        is AssistantAction.SetRoutine -> contract("routines.toggle")
        is AssistantAction.SetAppRule -> contract("apps.rule")
        is AssistantAction.SetAppRules -> contract("apps.group")
        is AssistantAction.SaveDictionaryWord -> contract("dictionary.save")
        is AssistantAction.BookmarkWord -> contract("vocabulary.bookmark")
        is AssistantAction.SetPreference -> setting(action.key).writeContract
        is AssistantAction.StartFocus -> contract("focus.start")
        AssistantAction.StopFocus -> contract("focus.stop")
    }

    fun requiresReview(action: AssistantAction): Boolean = when (forAction(action).review) {
        CapabilityReview.ALWAYS -> true
        CapabilityReview.NONE -> false
        CapabilityReview.GROUP -> when (action) {
            is AssistantAction.EditTasks -> action.ids.distinct().size > 1
            is AssistantAction.CompleteTasks -> action.ids.distinct().size > 1
            is AssistantAction.TrashTasks -> action.ids.distinct().size > 1
            else -> error("Missing group policy")
        }
    }

    fun validate(action: AssistantAction) {
        require(forAction(action).access == CapabilityAccess.WRITE) { "This capability cannot change data" }
        if (action is AssistantAction.SetPreference) setting(action.key).action(action.type, action.value)
    }

    /** Both legacy and current chip renderers use this conversion. No mutation occurs here. */
    fun reviewedChip(action: TalkAction): TalkAction = when (action) {
        is TalkAction.DirectPrefUpdate -> TalkAction.AskQuery(setting(action.prefKey).query(action.prefType, action.targetValue), action.label, action.emoji)
        is TalkAction.RoutineToggle -> {
            require(action.scheduleId > 0) { "Choose an existing routine" }
            TalkAction.AskQuery("/talk ${if (action.isEnabled) "enable" else "disable"} routine id:${action.scheduleId}", action.label, action.emoji)
        }
        else -> action
    }

    /** A current prerequisite observation, never a promise of successful OS delivery. */
    fun availability(context: Context, id: String): CapabilityAvailability {
        val item = all.singleOrNull { it.id == id } ?: return CapabilityAvailability(false, "Unknown capability")
        if (item.access == CapabilityAccess.UNAVAILABLE) return CapabilityAvailability(false, item.limitation)
        return when (id) {
            "usage.read" -> if (com.focusbyrj.app.util.UsageStatsHelper.hasUsageStatsPermission(context)) CapabilityAvailability(true)
                else CapabilityAvailability(false, "Usage Access is missing. Enable it in Android settings to read usage.")
            "transcript.capture" -> when {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED -> CapabilityAvailability(false, "Microphone permission is missing.")
                else -> runCatching {
                    if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) CapabilityAvailability(true)
                    else CapabilityAvailability(false, "No phone speech service is available.")
                }.getOrElse { CapabilityAvailability(null, "Speech-service availability could not be checked.") }
            }
            else -> CapabilityAvailability(true)
        }
    }

    fun help(context: Context, state: AssistantSession, text: String): FeatureTurn? {
        val lower = text.lowercase(java.util.Locale.ROOT).trim().trimEnd('?', '.', '!')
        val domain = Regex("^(?:capabilities|show capabilities for|what can you do with) (.+)$").matchEntire(lower)?.groupValues?.get(1)
        if (lower !in setOf("capabilities", "show capabilities", "what features can you control") && domain == null) return null
        state.clearPending(); state.switchTopic("app_help")
        if (domain == null) return FeatureTurn.Reply("I can help with these parts of Ayva. Choose one to see supported actions and limits. Changes still use their normal review steps; private Notes stay outside chat.", domains.map { (id, title) -> TalkAction.AskQuery("/talk capabilities $id", title) })
        if (domain !in domains) return FeatureTurn.Reply("Which part of Ayva do you mean?", domains.map { (id, title) -> TalkAction.AskQuery("/talk capabilities $id", title) })
        if (domain == "settings") return FeatureTurn.Reply("I can read ${settings.size} supported settings and preview validated changes. The current values, choices and limits come from the same definitions used to save them.", AssistantSettingGroup.entries.map { TalkAction.AskQuery("/talk settings ${it.id}", it.id.replaceFirstChar(Char::uppercaseChar)) })
        val entries = featureContracts.filter { it.domain == domain }
        val body = entries.joinToString("\n") { item ->
            val kind = when (item.access) {
                CapabilityAccess.READ -> "Read"
                CapabilityAccess.WRITE -> "Action${if (item.review == CapabilityReview.ALWAYS) " · review required" else if (item.review == CapabilityReview.GROUP) " · groups reviewed" else ""}"
                CapabilityAccess.NAVIGATE -> "Open native feature"
                CapabilityAccess.CONVERSATION -> "Conversation"
                CapabilityAccess.UNAVAILABLE -> "Unavailable in chat"
            }
            val current = if (item.access == CapabilityAccess.UNAVAILABLE) "" else availability(context, item.id).reason
            "• **${item.title}** ($kind). ${item.limitation}${if (current.isNotBlank()) " Current prerequisite: $current" else ""}"
        }
        val examples = entries.filter { it.access != CapabilityAccess.UNAVAILABLE }.mapNotNull { it.examples.firstOrNull()?.takeIf(String::isNotBlank) }.take(3)
        return FeatureTurn.Reply("**${domains.getValue(domain)}**\n$body\nTry: ${examples.joinToString("; ")}")
    }
}
