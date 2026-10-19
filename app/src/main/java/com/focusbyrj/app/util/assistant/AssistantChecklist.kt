package com.focusbyrj.app.util.assistant

import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Subtask
import org.json.JSONArray
import org.json.JSONObject

/** Stable child IDs scoped to one explicitly resolved parent. Every edit is reviewed. */
internal object AssistantChecklist {
    fun validate(before: String, payload: String): String {
        require(payload.length <= 40_000)
        val value = JSONObject(payload)
        require(value.getString("before") == before) { "That checklist changed; ask again" }
        val a = value.getJSONArray("items"); require(a.length() <= 50)
        val ids = mutableSetOf<String>()
        for (i in 0 until a.length()) {
            val item = a.getJSONObject(i)
            require(item.getString("id").length in 1..80 && ids.add(item.getString("id"))) { "Checklist IDs must be unique" }
            require(item.getString("title").isNotBlank() && item.getString("title").length <= 200)
            item.getBoolean("isDone")
        }
        return a.toString()
    }

    suspend fun answer(app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.trim().trimEnd('?', '.', '!')
        val show = Regex("(?i)^(?:show|list) (?:the )?(?:checklist|subtasks)(?: (?:of|for) (.+))?$").matchEntire(text)
        val add = Regex("(?i)^add (?:checklist item|subtask) (.+?)(?: to task (.+))?$").matchEntire(text)
        val edit = Regex("(?i)^(complete|uncheck|remove|rename) (?:checklist item|subtask) (\\d+)(?: of task (.+?))?(?: to (.+))?$").matchEntire(text)
        if (show == null && add == null && edit == null) return null
        val subject = show?.groupValues?.get(1)?.ifBlank { "it" } ?: add?.groupValues?.get(2)?.ifBlank { "it" } ?: edit!!.groupValues[3].ifBlank { "it" }
        val all = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted }
        val selected = AssistantTaskLanguage.select(subject, all, state)
        val task = selected.tasks.singleOrNull() ?: return FeatureTurn.Reply(selected.clarification ?: "Choose one task for that checklist. Say ‘show checklist for’ followed by its exact title.")
        val items = Subtask.listFromJson(task.subtasksJson)
        if (items.map { it.id }.distinct().size != items.size) return FeatureTurn.Reply("This checklist has duplicate item IDs. Repair it in the task editor before changing it through chat.")
        state.focusTask(task.id)
        if (show != null) return FeatureTurn.Reply("**${task.title}** checklist:\n" + if (items.isEmpty()) "No checklist items yet." else items.mapIndexed { i, item -> "${i + 1}. ${if (item.isDone) "[done]" else "[ ]"} ${item.title}" }.joinToString("\n") + "\nChecklist items share the parent deadline; completing one doesn't complete the parent.")
        val next = items.toMutableList()
        val description: String
        if (add != null) {
            val title = add.groupValues[1].trim()
            if (title.length !in 1..200 || next.size >= 50) return FeatureTurn.Reply("Use 1–200 characters per item and at most 50 checklist items.")
            next += Subtask(title = title); description = "Add checklist item **$title** to **${task.title}**?"
        } else {
            val index = edit!!.groupValues[2].toIntOrNull()?.minus(1)
            if (index == null || index !in items.indices) return FeatureTurn.Reply("Choose an item number from this task's current checklist.")
            val item = items[index]
            val op = edit.groupValues[1].lowercase()
            when (op) {
                "complete", "uncheck" -> next[index] = item.copy(isDone = op == "complete")
                "remove" -> next.removeAt(index)
                "rename" -> {
                    val title = edit.groupValues[4].trim()
                    if (title.length !in 1..200) return FeatureTurn.Reply("Say ‘rename checklist item ${index + 1} to’ followed by its new title.")
                    next[index] = item.copy(title = title)
                }
            }
            description = "${op.replaceFirstChar(Char::uppercaseChar)} checklist item **${item.title}** in **${task.title}**${if (op == "rename") " to **${next[index].title}**" else ""}?"
        }
        val payload = JSONObject().put("before", task.subtasksJson).put("items", JSONArray(Subtask.listToJson(next))).toString()
        return FeatureTurn.Preview(listOf(AssistantAction.EditTasks(listOf(task.id), TaskField.CHECKLIST, payload)), description)
    }
}
