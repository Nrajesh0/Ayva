package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.util.crypto.EncryptedMediaStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AssistantChoice(val label: String, val command: String)

data class AssistantSession(
    var touchedAt: Long = System.currentTimeMillis(),
    var taskId: Long? = null,
    var habitId: Long? = null,
    var topic: String? = null,
    var expected: String? = null,
    var pending: List<AssistantAction> = emptyList(),
    var choices: List<AssistantChoice> = emptyList(),
    var candidates: List<Long> = emptyList(),
    var pendingPriority: Boolean = false,
    var pendingPersistent: Boolean = false,
    var confirmationId: String? = null,
    var undoId: String? = null,
    var undo: List<JSONObject> = emptyList(),
    var lastReply: String? = null,
    var recentInputs: List<String> = emptyList(),
    var acknowledgedActionIds: List<String> = emptyList(),
    var versions: Map<Long, Long> = emptyMap(),
    var guards: Map<String, String> = emptyMap()
) {
    fun clearPending() {
        expected = null; pending = emptyList(); choices = emptyList(); confirmationId = null
        versions = emptyMap(); guards = emptyMap(); pendingPriority = false; pendingPersistent = false
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1); put("touched", touchedAt); put("task", taskId ?: JSONObject.NULL)
        put("habit", habitId ?: JSONObject.NULL); put("topic", topic ?: JSONObject.NULL)
        put("expected", expected ?: JSONObject.NULL); put("pending", JSONArray(pending.map { it.toJson() }))
        put("choices", JSONArray(choices.map { JSONObject().put("label", it.label).put("command", it.command) }))
        put("candidates", JSONArray(candidates)); put("priority", pendingPriority); put("persistent", pendingPersistent)
        put("confirmation", confirmationId ?: JSONObject.NULL); put("undoId", undoId ?: JSONObject.NULL)
        put("undo", JSONArray(undo)); put("lastReply", lastReply ?: JSONObject.NULL)
        put("recent", JSONArray(recentInputs.takeLast(6))); put("applied", JSONArray(acknowledgedActionIds.takeLast(30)))
        put("guards", JSONObject(guards))
        put("versions", JSONObject().apply { versions.forEach { (id, version) -> put(id.toString(), version) } })
    }

    companion object {
        fun fromJson(json: JSONObject): AssistantSession {
            require(json.getInt("version") == 1)
            fun nullable(key: String): String? = if (json.isNull(key)) null else json.getString(key)
            fun array(key: String) = json.optJSONArray(key) ?: JSONArray()
            return AssistantSession(
                touchedAt = json.getLong("touched"), taskId = nullable("task")?.toLongOrNull(), habitId = nullable("habit")?.toLongOrNull(),
                topic = nullable("topic"), expected = nullable("expected"),
                pending = array("pending").let { a -> (0 until a.length()).map { AssistantAction.fromJson(a.getJSONObject(it)) } },
                choices = array("choices").let { a -> (0 until a.length()).map { AssistantChoice(a.getJSONObject(it).getString("label"), a.getJSONObject(it).getString("command")) } },
                candidates = array("candidates").longs(), pendingPriority = json.optBoolean("priority"), pendingPersistent = json.optBoolean("persistent"),
                confirmationId = nullable("confirmation"), undoId = nullable("undoId"),
                undo = array("undo").let { a -> (0 until a.length()).map { a.getJSONObject(it) } },
                lastReply = nullable("lastReply"), recentInputs = array("recent").let { a -> (0 until a.length()).map { a.getString(it) } },
                acknowledgedActionIds = array("applied").let { a -> (0 until a.length()).map { a.getString(it) } },
                versions = (json.optJSONObject("versions") ?: JSONObject()).let { v -> v.keys().asSequence().associate { it.toLong() to v.getLong(it) } },
                guards = (json.optJSONObject("guards") ?: JSONObject()).let { v -> v.keys().asSequence().associateWith { v.getString(it) } }
            )
        }
    }
}

/** Small encrypted, expiring state; deliberately independent of expiring transcript cards. */
class AssistantSessionStore(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val file get() = File(context.filesDir, "ayva_conversation_state.enc")
    fun load(): AssistantSession {
        return try {
            if (!file.exists() || file.length() > 512_000) return AssistantSession(touchedAt = clock())
            val bytes = EncryptedMediaStorage.readDecryptedBytes(file) ?: return AssistantSession(touchedAt = clock())
            val state = AssistantSession.fromJson(JSONObject(bytes.toString(Charsets.UTF_8)))
            if (clock() - state.touchedAt !in 0..900_000L) { clear(); AssistantSession(touchedAt = clock()) } else state
        } catch (_: Exception) { clear(); AssistantSession(touchedAt = clock()) }
    }
    fun save(state: AssistantSession) {
        state.touchedAt = clock()
        val bytes = state.toJson().toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 512_000) { "Conversation state is too large" }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        EncryptedMediaStorage.writeEncryptedBytes(temporary, bytes)
        check(temporary.renameTo(file)) { "Couldn't save conversation state" }
    }
    fun clear() { file.delete(); File(file.parentFile, "${file.name}.tmp").delete() }
}
