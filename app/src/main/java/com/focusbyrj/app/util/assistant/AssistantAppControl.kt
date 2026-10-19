package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.util.*
import org.json.JSONArray
import org.json.JSONObject

/** Bulk manual rules use the full eligible installed catalogue, never just already-saved restrictions. */
object AssistantAppControl {
    internal fun inventory(context: Context): String = AppCatalog.load(context).sortedBy { it.packageName }.joinToString("\n") { "${it.packageName}|${it.appName}|${it.category.name}" }
    private fun ids(json: JSONObject, key: String): Set<String> = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
    private fun text(raw: String) = raw.trim().trimEnd('.', '!', '?').replaceFirst(Regex("(?i)^please\\s+"), "")
    private data class Selection(val packages: Set<String> = emptySet(), val error: String? = null)
    private fun resolve(context: Context, raw: String, catalog: List<InstalledApp>, exclusion: Boolean = false): Selection {
        val query = text(raw).trim().trim('"')
        val normalized = AppCatalog.normalize(query)
        val exact = catalog.filter { it.packageName.equals(query, true) || AppCatalog.normalize(it.appName) == normalized }
        if (exact.size == 1) return Selection(setOf(exact.single().packageName))
        if (exact.size > 1) return Selection(error = "‘$query’ matches multiple apps. Use a package name: ${exact.joinToString { it.packageName }}.")
        val category = AppCategory.entries.filter { it != AppCategory.ALL }.firstOrNull { c -> normalized in setOf(AppCatalog.normalize(c.title), AppCatalog.normalize(c.title) + " apps", c.name.lowercase(), c.name.lowercase() + " apps") }
        if (category != null) return Selection(catalog.filter { it.category == category }.map { it.packageName }.toSet()).let { if (it.packages.isEmpty()) Selection(error = "No eligible ${category.title.lowercase()} apps are visible on this device.") else it }
        val custom = CustomCategoryManager.getCategories(context).filter { AppCatalog.normalize(it.name) == normalized }
        if (custom.size == 1) return Selection(catalog.filter { it.packageName in custom.single().packages }.map { it.packageName }.toSet()).let { if (it.packages.isEmpty()) Selection(error = "That custom category has no eligible installed apps.") else it }
        if (custom.size > 1) return Selection(error = "More than one custom category is named ‘$query’. Rename one or use app/package names.")
        if (query.contains(',' ) || Regex("(?i)\\s+and\\s+").containsMatchIn(query)) {
            val pieces = query.split(Regex("\\s*,\\s*|(?i)\\s+and\\s+"))
            if (pieces.any(String::isBlank)) return Selection(error = "Complete each app name in the list before continuing.")
            if (pieces.size > 50) return Selection(error = "Choose at most 50 named apps or use a category.")
            val packages = mutableSetOf<String>()
            for (part in pieces) { val result = resolve(context, part, catalog, exclusion); if (result.error != null) return result; packages += result.packages }
            return Selection(packages)
        }
        val protectedMatch = AppCatalog.protectedPackages(context).any { pkg ->
            pkg.equals(query, true) || runCatching { AppCatalog.normalize(context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()) == normalized }.getOrDefault(false)
        }
        if (protectedMatch) return if (exclusion) Selection() else Selection(error = "That app is a protected system control. It is already excluded from locking.")
        val suggestions = AppCatalog.filter(catalog, query = query).take(4)
        return Selection(error = "I couldn't uniquely identify ‘$query’. ${if (suggestions.isEmpty()) "Use the installed app's exact name or package, or say ‘show apps’." else "Use one of these exact names/packages: " + suggestions.joinToString { "${it.appName} (${it.packageName})" } }")
    }
    private fun all(raw: String) = text(raw).lowercase() in setOf("all", "everything", "all apps", "all the apps", "all applications", "all of the apps", "all in the app", "all apps in the app")
    private fun fresh(state: AssistantSession): JSONObject? = state.featureState?.optJSONObject("appSelection")?.takeIf { state.topic == "blocking" && System.currentTimeMillis() - it.optLong("at") in 0..900_000L }

    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val query = text(input).replaceFirst(Regex("(?i)^also (?=keep|leave)"), "")
        val root = Regex("(?i)^(?:(?:also|now|then)\\s+)?(lock|block|unlock|unblock)(?:\\s+(.+))?$").matchEntire(query)
        if (root == null && (AssistantFeatureLanguage.isRequest(query) || Regex("(?i)^add (?:an? )?(?:habit|routine|task|note)\\b").containsMatchIn(query))) return null
        val previous = fresh(state)
        val modifier = Regex("(?i)^(?:except|exclude|also exclude|excluding|leave out|remove|include|also include|add|also add|keep|leave|make|change|switch|set|only)\\b").containsMatchIn(query)
        val listing = query.lowercase() in setOf("show locked apps", "show blocked apps", "which apps are locked", "which apps are blocked", "what is locked", "my locked apps", "my blocked apps")
        val selectionReport = previous != null && query.lowercase() in setOf("which apps", "which ones", "show selection", "show those apps", "what did you lock", "what did you unlock")
        if (root == null && !listing && !selectionReport && (!modifier || previous == null)) return null
        // “Add buy milk” remains an explicit task; only app-focused follow-ups enter this grammar.
        if (root == null && Regex("(?i)^(?:add|also add|remove|include|also include)\\s+").containsMatchIn(query) && previous == null) return null
        if (!selectionReport && !listing) { AssistantConversation.suspendQuestion(state); state.clearPending() } // Never leave an older confirmation live after a failed replacement.
        if (root != null && Regex("(?i)\\b(?:for\\s+(?:\\d+(?:\\.\\d+)?|an?|one|two|half an?)\\s*(?:seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\\b|(?:until|till)\\s+(?:\\d|tomorrow|today|midnight|noon))").containsMatchIn(query))
            return FeatureTurn.Reply("Timed blocks aren't supported from chat. Manual lock/unlock changes have no automatic expiry. Use a timed focus session or a scheduled routine instead. Nothing changed.")
        val existing = app.database.appRestrictionDao().getAllRestrictionsSync().associateBy { it.packageName }
        val installed = AppCatalog.load(context)
        val extras = if (root?.groupValues?.get(1)?.lowercase() in setOf("unlock", "unblock") || previous?.optBoolean("locked") == false) existing.values.filter { old -> old.packageName !in installed.map { it.packageName } }.mapNotNull { old ->
            runCatching { val info = context.packageManager.getApplicationInfo(old.packageName, 0); InstalledApp(old.packageName, context.packageManager.getApplicationLabel(info).toString(), AppCatalog.category(info, old.packageName)) }.getOrElse { InstalledApp(old.packageName, old.appName, AppCategory.OTHERS) }
        } else emptyList()
        val catalog = AppCatalog.filter(installed + extras)
        val visible = catalog.map { it.packageName }.toSet()
        val byId = catalog.associateBy { it.packageName }
        if (listing || selectionReport) {
            val selected = if (selectionReport) ids(previous!!, "targets") else existing.values.filter { it.isRestricted }.map { it.packageName }.toSet()
            if (listing) {
                AssistantConversation.suspendQuestion(state); state.clearPending(); state.switchTopic("blocking")
                if (state.featureState == null) state.featureState = JSONObject()
                state.featureState!!.remove("appSelection")
                if (selected.size in 1..500) state.featureState!!.put("appSelection", JSONObject().put("at", System.currentTimeMillis()).put("locked", true).put("mode", "HARD").put("base", JSONArray(selected.toList())).put("targets", JSONArray(selected.toList())).put("excluded", JSONArray()).put("committed", JSONArray(selected.toList())))
            }
            val pending = state.pending.filterIsInstance<AssistantAction.SetAppRules>().singleOrNull()?.rules?.associateBy { it.draft.packageName }.orEmpty()
            val header = if (pending.isNotEmpty()) "Current selection; proposed changes still need confirmation" else if (selectionReport) "Selected apps; current manual rules" else "Current manual app locks"
            val rows = (selected + pending.keys).sortedBy { byId[it]?.appName ?: existing[it]?.appName ?: it }
            val lines = rows.take(500).joinToString("\n") { pkg ->
                val proposed = pending[pkg]?.draft?.let { "; proposed: ${if (it.isRestricted) "lock · ${it.mode}" else "unlock"}" }.orEmpty()
                "• ${byId[pkg]?.appName ?: existing[pkg]?.appName ?: pkg} ($pkg): current ${if (existing[pkg]?.isRestricted == true) "locked · ${existing.getValue(pkg).mode}" else "unlocked"}$proposed"
            }
            val actions = if (pending.isNotEmpty() && state.confirmationId != null) listOf(TalkAction.AskQuery("/ayva confirm ${state.confirmationId}", "Confirm"), TalkAction.AskQuery("/ayva cancel ${state.confirmationId}", "Cancel")) else listOf(TalkAction.AskQuery("unlock all", "Preview unlock all"), TalkAction.NavigateAppScreen("dashboard", "Manage app rules"))
            return FeatureTurn.Reply("**$header**\n${lines.ifBlank { "No manually locked apps in this view." }}${if (rows.size > 500) "\nShowing 500 of ${rows.size}; name a smaller selection to act." else ""}\nRoutine/focus blocks and temporary relief are separate; a saved rule is not proof an overlay is visible.", actions)
        }

        var locked = previous?.optBoolean("locked") ?: true
        var mode = previous?.optString("mode", "HARD") ?: "HARD"
        var base = previous?.let { ids(it, "base") }.orEmpty()
        var excluded = previous?.let { ids(it, "excluded") }.orEmpty()
        var committed = previous?.let { ids(it, "committed") }.orEmpty()
        var revise = root == null
        var forced: Pair<Set<String>, Boolean>? = null
        if (root != null) {
            locked = root.groupValues[1].lowercase() in setOf("lock", "block")
            val subject = root.groupValues[2].trim().replace(Regex("(?i)\\s+too$"), "")
            if (subject.isBlank()) return FeatureTurn.Reply("Which apps? Name them, use a category such as social, or say ‘lock all except WhatsApp’.")
            val halves = subject.split(Regex("(?i)\\s+(?:except|excluding|apart from|but not)\\s+"), limit = 2)
            val target = halves[0].replaceFirst(Regex("(?i)\\s+in (?:soft|strict|hard) mode$"), "")
            mode = if (Regex("(?i)\\bsoft mode$").containsMatchIn(halves[0])) "SOFT" else "HARD"
            val pronoun = target.lowercase() in setOf("it", "that app", "them", "those apps", "these apps", "all of them")
            if (pronoun) {
                if (previous == null) return FeatureTurn.Reply("Which apps do you mean? Name them or ask for a fresh lock/unlock selection.")
                base = ids(previous, "targets"); excluded = emptySet(); revise = false; committed = emptySet()
            } else {
                val selection = if (all(target)) Selection(visible) else resolve(context, target, catalog)
                selection.error?.let { return FeatureTurn.Reply(it) }
                if (query.startsWith("also ", true) && previous != null && locked == previous.optBoolean("locked")) {
                    base = ids(previous, "base") + selection.packages; excluded = ids(previous, "excluded") - selection.packages; committed = ids(previous, "committed"); revise = true
                } else { base = selection.packages; excluded = emptySet(); committed = emptySet() }
            }
            if (halves.size == 2) { val exception = resolve(context, halves[1], catalog, exclusion = true); exception.error?.let { return FeatureTurn.Reply(it) }; excluded = exception.packages }
        } else {
            val only = Regex("(?i)^only (.+?)(?: instead)?$").matchEntire(query)
            val modeMatch = Regex("(?i)^(?:make|change|switch|set) (?:it|them|those apps|these apps)(?: to)? (soft|strict|hard)(?: mode)?$").matchEntire(query)
            val keep = Regex("(?i)^(?:keep|leave) (.+?) (locked|blocked|unlocked|unblocked)$").matchEntire(query)
            when {
                only != null -> {
                    val selection = resolve(context, only.groupValues[1], catalog); selection.error?.let { return FeatureTurn.Reply(it) }
                    base = selection.packages; excluded = excluded.intersect(base)
                }
                modeMatch != null -> mode = if (modeMatch.groupValues[1].equals("soft", true)) "SOFT" else "HARD"
                keep != null -> {
                    val selection = resolve(context, keep.groupValues[1], catalog); selection.error?.let { return FeatureTurn.Reply(it) }
                    val lock = keep.groupValues[2].lowercase() in setOf("locked", "blocked")
                    if (lock == locked) { base += selection.packages; excluded -= selection.packages }
                    else excluded += selection.packages
                    forced = selection.packages to lock
                }
                else -> {
                    if (Regex("(?i)^(?:make|change|switch|set)\\b.*\\b(?:minutes?|hours?)$").containsMatchIn(query)) return FeatureTurn.Reply("Manual app locks have no automatic expiry. Choose soft/strict mode, or start a timed focus session instead. Nothing in this preview was applied.")
                    val change = Regex("(?i)^(except|exclude|also exclude|excluding|leave out|remove|include|also include|add|also add)\\s+(.+?)(?:\\s+too)?$").matchEntire(query) ?: return null
                    val selection = resolve(context, change.groupValues[2], catalog, exclusion = change.groupValues[1].lowercase() !in setOf("include", "also include", "add", "also add"))
                    if (selection.error != null && change.groupValues[1].equals("add", true)) return null
                    selection.error?.let { return FeatureTurn.Reply(it) }
                    if (change.groupValues[1].lowercase() in setOf("include", "also include", "add", "also add")) { base += selection.packages; excluded -= selection.packages }
                    else excluded += selection.packages
                }
            }
        }
        if ((base + excluded).any { it !in visible }) return FeatureTurn.Reply("An app in that selection is no longer available. Ask for a fresh lock/unlock request; nothing changed.")
        val targets = base - excluded
        if (base.size > 500) return FeatureTurn.Reply("That selection has ${base.size} apps. Choose a smaller category or named selection (up to 500); I won't silently lock only part of it.")
        if (base.isEmpty()) return FeatureTurn.Reply("No eligible installed apps match. Say ‘show apps’ to see what Android makes available to Ayva.")
        val desired = linkedMapOf<String, Boolean>()
        targets.forEach { desired[it] = locked }
        if (revise) (committed - targets).forEach { desired[it] = !locked }
        forced?.let { (packages, lock) -> packages.forEach { desired[it] = lock } }
        val rules = desired.mapNotNull { (pkg, lock) ->
            val old = existing[pkg]
            if (!lock && old?.isRestricted != true) null
            else {
                val draft = if (lock) (old ?: AppRestriction(pkg, byId.getValue(pkg).appName)).copy(isRestricted = true, mode = mode, restrictionMode = "SIMPLE") else old!!.copy(isRestricted = false)
                if (draft == old) null else AssistantAction.SetAppRule(draft, old)
            }
        }
        val plan = JSONObject().put("at", System.currentTimeMillis()).put("locked", locked).put("mode", mode).put("base", JSONArray(base.toList())).put("excluded", JSONArray(excluded.toList())).put("targets", JSONArray(targets.toList())).put("committed", JSONArray(if (revise) committed.toList() else emptyList<String>()))
        if (state.featureState == null) state.featureState = JSONObject()
        state.featureState!!.put("appSelection", plan)
        state.clearPending(); state.switchTopic("blocking")
        if (rules.isEmpty()) { applied(state); return FeatureTurn.Reply("No manual rule changes are needed. ${targets.size} selected apps already have the requested state. Routine/focus blocks are separate.") }
        val detail = rules.joinToString("\n") { "• ${if (it.draft.isRestricted) "Lock" else "Unlock"} ${it.draft.appName} (${it.draft.packageName})" }
        val exceptions = excluded.takeIf { it.isNotEmpty() }?.joinToString { byId.getValue(it).appName }?.let { "\nExcluded: $it. Initial exclusions keep their existing rules." }.orEmpty()
        val description = "${rules.size} manual app-rule changes${if (locked) " · ${if (mode == "SOFT") "Soft" else "Strict"} blocking, no automatic expiry" else " · unlock manual rules"}.\n$detail$exceptions\nAyva and essential system controls are excluded from new locks. Active routine/focus protections are still enforced. Review and confirm, or revise with ‘except [app]’, ‘include [app]’ or ‘make them soft’."
        if (description.length > 45_000) return FeatureTurn.Reply("That preview is too large to review safely. Choose a smaller category or named selection; nothing changed.")
        return FeatureTurn.Preview(listOf(AssistantAction.SetAppRules(rules)), description)
    }
    fun applied(state: AssistantSession) {
        val plan = state.featureState?.optJSONObject("appSelection") ?: return
        plan.put("committed", JSONArray(ids(plan, "targets").toList())).put("at", System.currentTimeMillis())
    }
}
