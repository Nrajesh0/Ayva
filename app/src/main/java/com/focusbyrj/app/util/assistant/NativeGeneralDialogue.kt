package com.focusbyrj.app.util.assistant

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream

/** Stateless, read-only language fallback. Never owns dialogue memory or app actions. */
internal object NativeGeneralDialogue {
    private val lock = ReentrantLock()
    private val requests = NativeDialogueRequests(SystemClock::elapsedRealtime)
    private val reader = Executors.newSingleThreadExecutor { r -> Thread(r, "run-dialogue-output").apply { isDaemon = true } }
    private val meanings = mapOf(
        "RUNADVICE" to "what could I try", "RUNWHY" to "why that", "RUNMORE" to "tell me more",
        "RUNEXAMPLE" to "give me an example", "RUNSHORT" to "shorter", "RUNSUMMARY" to "what do we have so far",
        "RUNALTERNATIVE" to "something else", "RUNCROWDS" to "crowds wear me out",
        "RUNQUIET" to "I prefer quiet evenings", "RUNSTUDYSTRESS" to "I am worried about studying",
        "RUNWORKSTRESS" to "I am stressed about work", "RUNTIRED" to "I feel tired"
    )
    // Conservative boundary: uncertainty, negation, quotations and mixed instructions stay
    // with the richer Kotlin parser. A meaning token must never erase those qualifiers.
    internal fun eligible(text: String): Boolean = text.toByteArray(Charsets.UTF_8).size in 1..2000 &&
        Regex("^(?:could|can|would|any|some|what|where|why|how|explain|elaborate|sum|summarize|recap|something|anything|another|different|crowded|crowds|peaceful|quiet|calm|i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text.trim()) &&
        !text.any { it == '\n' || it == '\r' || it == '\u0000' || it == '"' || it == '“' || it == '`' } &&
        !text.trimStart().startsWith(":") && !text.trimStart().startsWith("/") &&
        !Regex("\\b(?:not|no|never|don't|doesn't|didn't|can't|cannot|without|maybe|perhaps|might|if|suppose|imagine|actually|instead|but|and|or|he|she|they|friend|said|says)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text.replace('’', '\''))

    internal fun decode(output: String): String? {
        val tokens = output.lineSequence().filter { it.startsWith("RUNNATIVE1:") }.toList()
        return tokens.singleOrNull()?.removePrefix("RUNNATIVE1:")?.trim()?.let(meanings::get)
    }

    fun interpret(context: Context, text: String): String? {
        if (Build.VERSION.SDK_INT < 26 || !android.os.Process.is64Bit() || requests.coolingDown() || Looper.myLooper() == Looper.getMainLooper() || !eligible(text)) return null
        val executable = File(context.applicationInfo.nativeLibraryDir ?: return null, "librun_dialogue.so")
        if (!executable.canExecute() || !lock.tryLock()) return null
        var process: Process? = null
        var scratch: File? = null
        try {
            // The coordinator has early and late fallback routes. Reuse classification
            // only: the current Kotlin frame still determines the actual response.
            if (requests.coolingDown()) return null
            requests.cached(text)?.let { return it.meaning }
            val assets = assets(context)
            scratch = File(context.cacheDir, "general-native-turn")
            scratch.deleteRecursively() // Also removes residue after app/process death.
            check(scratch.mkdirs())
            listOf("LOGS", "USERS", "TMP").forEach { File(scratch, it).mkdirs() }
            val child = ProcessBuilder(executable.absolutePath, assets.absolutePath, scratch.absolutePath)
                .directory(scratch).redirectErrorStream(true).start()
            process = child
            val output = reader.submit<String> {
                child.inputStream.use { stream ->
                    val bytes = stream.readBytesBounded(16 * 1024)
                    String(bytes, Charsets.UTF_8)
                }
            }
            try {
                child.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text); it.newLine() }
                val result = output.get(10, TimeUnit.SECONDS)
                check(child.waitFor(1, TimeUnit.SECONDS) && child.exitValue() == 0)
                return decode(result).also { requests.completed(text, it) }
            } finally {
                child.destroyForcibly()
                output.cancel(true)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (_: Exception) {
            requests.failed()
            return null // Missing assets, unsupported ABI, timeout or crash: Kotlin still answers.
        } finally {
            process?.destroyForcibly()
            scratch?.deleteRecursively()
            lock.unlock()
        }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(out.size() + count <= limit)
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun assets(context: Context): File {
        val expected = context.assets.open("conversation/native-v1.sha256").bufferedReader().use { it.readText().trim() }
        check(expected.matches(Regex("[a-f0-9]{64}")))
        val parent = File(context.noBackupFilesDir, "general-native-assets")
        val target = File(parent, expected)
        if (File(target, ".ready").isFile) return target
        parent.mkdirs()
        parent.listFiles()?.forEach { it.deleteRecursively() } // Public data only; never session memory.
        val staging = File(parent, "staging").apply { mkdirs() }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var entries = 0
            context.assets.open("conversation/native-v1.zip").use { raw ->
                java.security.DigestInputStream(raw, digest).use { checked ->
                    ZipInputStream(checked).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            check(++entries <= 1000)
                            val dest = File(staging, entry.name)
                            check(dest.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                            if (entry.isDirectory) { dest.mkdirs(); continue }
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { out ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= 100L * 1024 * 1024)
                                    out.write(buffer, 0, count)
                                }
                            }
                        }
                        // Digest the central directory/trailer as well as entry data.
                        while (checked.read() != -1) { }
                        check(digest.digest().joinToString("") { "%02x".format(it) } == expected)
                    }
                }
            }
            File(staging, ".ready").writeText("1")
            check(staging.renameTo(target))
            return target
        } finally { staging.deleteRecursively() }
    }
}


/** Small process-local cache of stateless meanings, including recognized abstention.
 * Keys are digests; neither user wording nor conversation frames are retained here.
 * The adapter's lock serializes cache access; the failure deadline is also read before locking.
 */
internal class NativeDialogueRequests(private val now: () -> Long) {
    data class Cached(val meaning: String?, val expires: Long)
    private val recent = linkedMapOf<String, Cached>()
    @Volatile private var retryAfter = 0L

    fun coolingDown(): Boolean = now() < retryAfter

    fun cached(text: String): Cached? {
        val time = now()
        recent.entries.removeAll { it.value.expires <= time }
        return recent[key(text)]
    }

    fun completed(text: String, meaning: String?) {
        val digest = key(text)
        recent.remove(digest)
        recent[digest] = Cached(meaning, now() + 10_000L)
        while (recent.size > 4) recent.remove(recent.keys.first())
    }

    fun failed() {
        recent.clear()
        retryAfter = now() + 60_000L
    }

    private fun key(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
