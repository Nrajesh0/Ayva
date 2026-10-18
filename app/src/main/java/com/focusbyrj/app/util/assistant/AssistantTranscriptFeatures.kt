package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.util.OfflineNluEngine
import com.focusbyrj.app.util.SmartDateParser
import org.json.JSONArray
import org.json.JSONObject

/** Audio is context, not authority: every extracted task stays behind an editable preview. */
object AssistantTranscriptFeatures {
    internal fun drafts(text: String): List<Task>? {
        val pieces = text.trim().split(Regex("\\s*\\n+\\s*|\\s*;\\s*|\\s+and then\\s+", RegexOption.IGNORE_CASE)).filter(String::isNotBlank)
        if (pieces.size !in 1..5) return null
        return pieces.map { piece ->
            if (AssistantFeatureLanguage.isRequest(piece) || AssistantLanguage.actionGuard(piece) != null || !OfflineNluEngine.isExplicitCreation(piece)) return null
            AssistantTaskLanguage.draft(piece, false, false)?.takeIf { it.title.isNotBlank() && it.title.length <= 200 && it.title.none(Char::isISOControl) } ?: return null
        }.takeIf { list -> list.map { it.title.lowercase() }.distinct().size == list.size }
    }
    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, input: String): FeatureTurn? {
        val text = input.trim().trimEnd('.', '!', '?'); val lower = text.lowercase()
        val initial = lower in setOf("make tasks from what i just said", "make tasks from my transcript", "make tasks from the transcript", "preview my transcript")
        val change = Regex("(?is)^change transcript item (\\d+) to (.+)$").matchEntire(text)
        val drop = Regex("(?i)^drop transcript item (\\d+)$").matchEntire(text)
        if (!initial && change == null && drop == null) return null
        val data = state.featureState
        val transcript = data?.optJSONObject("transcript")?.takeIf { System.currentTimeMillis() - it.optLong("at") in 0..900_000L }
            ?: return FeatureTurn.Reply("I need a fresh finalized voice transcript first. Stop transcription, review its text, then ask me to preview tasks. Partial transcripts never create tasks.")
        var tasks = if (initial) drafts(transcript.getString("text")) else {
            if (state.topic != "transcript") return FeatureTurn.Reply("Request a fresh transcript task preview before editing it.")
            data.optJSONArray("transcriptDrafts")?.let { a -> (0 until a.length()).map { TaskJson.decode(a.getJSONObject(it)) } }
        }
        if (tasks == null) return FeatureTurn.Reply("I couldn't safely turn the entire transcript into tasks. Use up to five explicit requests, such as ‘remind me to call Mum tomorrow; add a task to buy milk’. Edit the text and say it again; nothing was saved.")
        if (change != null || drop != null) {
            val index = (change ?: drop)!!.groupValues[1].toIntOrNull()?.minus(1)
            if (index == null || index !in tasks.indices) return FeatureTurn.Reply("Choose an item number from the current transcript preview.")
            tasks = if (change == null) tasks.filterIndexed { i, _ -> i != index } else {
                val replacement = drafts(change.groupValues[2])?.singleOrNull() ?: return FeatureTurn.Reply("Replace one item with one explicit task request, such as ‘remind me to call Mum tomorrow’. Nothing was saved.")
                tasks.mapIndexed { i, old -> if (i == index) replacement else old }
            }
        }
        if (tasks.isEmpty()) { data.remove("transcriptDrafts"); return FeatureTurn.Reply("No task drafts remain. Nothing was saved.") }
        val existing = app.database.taskDao().getAllActiveTasksList().filter { !it.isCompleted }.map { it.title.lowercase() }.toSet()
        if (tasks.any { it.title.lowercase() in existing } || tasks.map { it.title.lowercase() }.distinct().size != tasks.size) return FeatureTurn.Reply("A proposed title duplicates an existing task or another draft. Edit the transcript to make each request distinct; nothing was saved.")
        state.switchTopic("transcript")
        data.put("transcriptDrafts", JSONArray(tasks.map(TaskJson::encode)))
        return FeatureTurn.Preview(tasks.map(AssistantAction::CreateTask), "Save these transcript tasks?\n" + tasks.mapIndexed { i, task -> "${i + 1}. **${task.title}** — ${task.dueDate?.let(SmartDateParser::formatDueDate) ?: "no due time"}" }.joinToString("\n") + "\nEdit with ‘change transcript item 2 to remind me to …’ or ‘drop transcript item 2’. Nothing is added until you confirm.")
    }
}
