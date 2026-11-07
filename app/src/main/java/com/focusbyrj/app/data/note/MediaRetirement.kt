package com.focusbyrj.app.data.note

import android.content.Context
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Only proven removals become garbage-collection candidates. Unknown files are retained. */
internal object MediaRetirement {
    private val lock = Any()
    private var root: File? = null
    private val livePaths = hashSetOf<String>()

    fun initialize(context: Context) = synchronized(lock) {
        val next = context.applicationContext.filesDir.canonicalFile
        if (root != next) { root = next; livePaths.clear() }
    }

    /** Creation and write intents retain files for this process, covering edits and undo. */
    fun protect(file: File) = synchronized(lock) { livePaths.add(file.canonicalPath); Unit }
    fun protect(note: NoteEntity) {
        runCatching { references(note) }.getOrNull()?.forEach { protect(File(it)) }
    }

    private fun ledger() = File(checkNotNull(root), "media_retirement.enc")
    private fun read(): LinkedHashSet<String> {
        val file = ledger()
        if (!file.exists()) return linkedSetOf()
        require(file.length() <= 2_000_000) { "Media retirement inventory is too large" }
        val array = JSONObject(EncryptedSnapshotFile.read(file)).getJSONArray("paths")
        return (0 until array.length()).mapTo(linkedSetOf()) { array.getString(it) }
    }
    private fun write(paths: Set<String>) {
        val text = JSONObject().put("paths", JSONArray(paths.toList())).toString()
        require(text.toByteArray().size <= 2_000_000) { "Media retirement inventory is too large" }
        EncryptedSnapshotFile.write(ledger(), text)
    }

    /** Persist before changing the row. A failed row change leaves references that prevent deletion. */
    fun beforeChange(previous: NoteEntity?, next: NoteEntity?) {
        // DAO writes can run while the vault's credential monitor is held by a
        // caller on another thread. Never acquire that monitor inside a DB write.
        // Opaque payloads cannot prove retirement and are conservatively retained.
        if ((previous != null && VaultPayloadEncryptor.isVaultEncrypted(previous)) ||
            (next != null && VaultPayloadEncryptor.isVaultEncrypted(next))) return
        if (previous == null) { next?.let(::protect); return }
        next?.let(::protect)
        try {
            val old = references(previous)
            val retained = next?.let(::references).orEmpty()
            if (next != null) old.forEach { protect(File(it)) } // Undo can still refer to removed media.
            retire(old - retained)
        } catch (_: Exception) {
            // Cleanup bookkeeping must never destroy data or prevent a primary note save.
            android.util.Log.w("MediaRetirement", "Media reclamation deferred; files retained")
        }
    }

    fun retire(paths: Set<String>): Boolean = synchronized(lock) {
        val base = root ?: return@synchronized false
        val eligible = paths.map { File(it).canonicalFile }.filter { file ->
            listOf("keep_images", "keep_audio", "keep_drawings").any { folder ->
                file.path.startsWith(File(base, folder).path + File.separator)
            }
        }.map { it.path }
        if (eligible.isEmpty()) return@synchronized true
        val prior = read()
        val changed = prior.addAll(eligible)
        if (changed) write(prior)
        true
    }

    fun candidates(context: Context): Set<String> = synchronized(lock) { initialize(context); read() }
    fun forget(path: String) = synchronized(lock) {
        val paths = read()
        if (paths.remove(path)) write(paths)
    }

    fun reclaimIfUnused(file: File): Boolean = synchronized(lock) {
        if (file.canonicalPath in livePaths) return@synchronized false
        // All supported media writes are authenticated ciphertext. Unlinking does not
        // promise forensic erasure on flash storage; zero overwrites cannot promise it either.
        !file.exists() || (file.isFile && file.delete())
    }

    /** Relative/URI representations cannot silently become unrelated working-directory paths. */
    fun references(note: NoteEntity, vaultKey: ByteArray? = null): Set<String> =
        NoteMediaInventory.paths(note, vaultKey).mapTo(linkedSetOf()) { value ->
            val file = File(value)
            require(file.isAbsolute) { "Unsupported media reference; retain files" }
            file.canonicalPath
        }
}
