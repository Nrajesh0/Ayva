package com.focusbyrj.app.util.assistant

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

data class DictionarySense(val id: String, val word: String, val part: String, val definition: String, val example: String, val synonyms: List<String>, val labels: List<String>)

/** Hash index in the APK. At most four <256 KiB shards, no unpacked database/download. */
object AssistantDictionaryStore {
    private val cache = object : LinkedHashMap<String, JSONObject>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?) = size > 4
    }
    fun normalize(word: String) = Normalizer.normalize(word.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    @Synchronized fun lookup(context: Context, word: String): List<DictionarySense> {
        val key = normalize(word)
        if (key.length !in 1..120) return emptyList()
        val shard = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).first().let { "%02x".format(it) }
        val json = cache[shard] ?: context.assets.open("dictionary/$shard.dict").use { stream ->
            GZIPInputStream(stream).use { input ->
                val buffer = ByteArray(262_145); var count = 0
                while (count < buffer.size) { val n = input.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                require(count <= 262_144) { "Dictionary shard exceeds the lookup budget" }
                JSONObject(String(buffer, 0, count, Charsets.UTF_8))
            }
        }.also { cache[shard] = it }
        val entries = json.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i); val meanings = entry.getJSONArray("meanings")
                val entryLabels = entry.optJSONArray("labels")
                for (j in 0 until meanings.length()) {
                    val meaning = meanings.getJSONObject(j)
                    val labels = entryLabels?.let { a -> (0 until a.length()).mapNotNull { n -> a.optJSONObject(n)?.optString("name")?.takeIf(String::isNotBlank) } }.orEmpty() +
                        meaning.optJSONArray("labels")?.let { a -> (0 until a.length()).mapNotNull { n -> a.optJSONObject(n)?.optString("name")?.takeIf(String::isNotBlank) } }.orEmpty()
                    val synonyms = meaning.optJSONArray("synonyms")?.let { a -> (0 until minOf(a.length(), 8)).map { n -> a.getString(n) } }.orEmpty()
                    add(DictionarySense(meaning.getString("id"), entry.getString("word"), meaning.optString("speech_part"), meaning.getString("def"), meaning.optString("example").takeUnless { it == "null" }.orEmpty(), synonyms, labels.distinct()))
                }
            }
        }.distinctBy { it.id }
    }
    fun relatedForms(context: Context, word: String): List<String> {
        if (!word.matches(Regex("[a-z]{3,24}"))) return emptyList()
        val candidates = buildList {
            if (word.endsWith("ies")) add(word.dropLast(3) + "y")
            if (word.endsWith("es")) add(word.dropLast(2))
            if (word.endsWith("s")) add(word.dropLast(1))
            for (suffix in listOf("ed", "ing")) if (word.endsWith(suffix)) {
                val base = word.removeSuffix(suffix); add(base); add(base + "e")
                if (base.length > 2 && base.last() == base[base.lastIndex - 1]) add(base.dropLast(1))
            }
        }.distinct().filter { it.length > 2 }
        return candidates.filter { lookup(context, it).isNotEmpty() }.take(3)
    }
}
