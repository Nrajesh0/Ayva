package com.focusbyrj.app.util.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as D
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

internal data class BackupDocument(val uri: String, val name: String, val size: Long)
internal interface BackupDestination {
    fun create(name: String): BackupDocument
    fun read(uri: String): InputStream
    fun write(uri: String): OutputStream
    fun info(uri: String): BackupDocument?
    fun rename(uri: String, name: String): BackupDocument?
    fun delete(uri: String): Boolean
}

/** Only a persistently granted tree is accepted; no broad filesystem permission is needed. */
internal class SafBackupDestination(context: Context, tree: String) : BackupDestination {
    private val resolver = context.applicationContext.contentResolver
    private val treeUri = Uri.parse(tree).also { require(it.scheme == "content" && D.isTreeUri(it)) { "Choose a writable backup folder." } }
    private val root = D.buildDocumentUriUsingTree(treeUri, D.getTreeDocumentId(treeUri))
    private fun checked(value: String): Uri {
        val uri = Uri.parse(value)
        require(uri.scheme == "content" && uri.authority == treeUri.authority && D.isTreeUri(uri) && D.getDocumentId(uri).isNotBlank())
        require(D.getTreeDocumentId(uri) == D.getTreeDocumentId(treeUri)) { "Backup document is outside its granted tree." }
        return uri
    }
    override fun create(name: String): BackupDocument {
        require(name.length in 1..180 && !name.contains('/') && !name.contains('\\'))
        val uri = D.createDocument(resolver, root, "application/octet-stream", name) ?: error("Folder refused to create a backup.")
        return info(uri.toString()) ?: error("The new document could not be read back.")
    }
    override fun read(uri: String) = resolver.openInputStream(checked(uri)) ?: error("Backup cannot be read.")
    override fun write(uri: String) = resolver.openOutputStream(checked(uri), "wt") ?: error("Backup cannot be written.")
    override fun info(uri: String): BackupDocument? = checkNotNull(resolver.query(checked(uri), arrayOf(D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_SIZE), android.os.Bundle.EMPTY, null)) {
        "The backup provider did not return document information. Existing files were kept."
    }.use {
        if (!it.moveToFirst()) null else BackupDocument(uri, it.getString(0), if (it.isNull(1)) -1 else it.getLong(1))
    }
    override fun rename(uri: String, name: String): BackupDocument? = runCatching { D.renameDocument(resolver, checked(uri), name)?.let { info(it.toString()) } }.getOrNull()
    override fun delete(uri: String) = D.deleteDocument(resolver, checked(uri))
    fun probe() {
        val file = create("RUN-folder-check-${UUID.randomUUID()}.tmp")
        try {
            val marker = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            write(file.uri).use { it.write(marker) }
            val actual = read(file.uri).use { digestBackup(it, 32) }
            require(actual == marker.inputStream().use { digestBackup(it) }) { "Folder did not preserve the test document." }
        } finally { check(delete(file.uri)) { "Folder does not support safe cleanup. Choose another folder." } }
    }
}

internal data class EncryptedFileDigest(val size: Long, val sha256: String)
internal fun digestBackup(input: InputStream, maxBytes: Long = BackupArchiveLimits.ENCRYPTED_BYTES, checkpoint: () -> Unit = {}): EncryptedFileDigest {
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024); var size = 0L
    while (true) {
        checkpoint()
        val count = input.read(buffer); if (count < 0) break
        if (count == 0) {
            val next = input.read(); if (next < 0) break
            size++; require(size <= maxBytes) { "Backup exceeds its verification limit." }; digest.update(next.toByte()); continue
        }
        size += count; require(size <= maxBytes) { "Backup exceeds its verification limit." }; digest.update(buffer, 0, count)
    }
    return EncryptedFileDigest(size, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
}

/** External files are committed only after read-back equality with an authenticated local archive. */
internal object AutomaticBackupFiles {
    fun publish(destination: BackupDestination, name: String, expected: EncryptedFileDigest, source: () -> InputStream): BackupDocument {
        var pending: BackupDocument? = null
        var fallback: BackupDocument? = null
        fun copy(document: BackupDocument) {
            source().use { input -> destination.write(document.uri).use { output -> input.copyTo(output, 64 * 1024); output.flush() } }
            val actual = destination.read(document.uri).use { digestBackup(it, expected.size) }
            require(actual == expected) { "Destination verification failed; previous backups were kept." }
        }
        try {
            pending = destination.create("$name.incomplete"); copy(pending)
            val renamed = destination.rename(pending.uri, name)
            if (renamed != null) {
                pending = renamed
                require(destination.read(renamed.uri).use { digestBackup(it, expected.size) } == expected)
                return renamed
            }
            // Some providers have no rename. Keep the verified temporary copy until a final copy verifies.
            fallback = destination.create(name); copy(fallback)
            runCatching { destination.delete(pending.uri) }
            return fallback
        } catch (error: Exception) {
            fallback?.let { runCatching { destination.delete(it.uri) } }
            pending?.let { runCatching { destination.delete(it.uri) } }
            throw error
        }
    }

    /** Operates only on the local ownership ledger, never a filename/glob scan of the chosen folder. */
    fun prune(destination: BackupDestination, records: List<ManagedBackup>, stream: String, keyId: String, keep: Int): Triple<List<ManagedBackup>, Int, Boolean> {
        require(keep in 1..365)
        val owned = records.filter { it.stream == stream && it.keyId == keyId }
            .asReversed().distinctBy { it.uri }.asReversed()
        val candidates = owned.dropLast(keep) // Ledger order is commit order, independent of wall-clock changes.
        // Recheck the newest committed copy after bookkeeping. A missing/corrupt retained
        // archive must never cause deletion of the only older recoverable copies.
        val anchor = owned.lastOrNull() ?: return Triple(records, 0, false)
        try {
            require(destination.info(anchor.uri)?.name == anchor.name)
            require(destination.read(anchor.uri).use { digestBackup(it, anchor.size) } == EncryptedFileDigest(anchor.size, anchor.sha256))
        } catch (_: Exception) { return Triple(records, candidates.size.coerceAtLeast(1), false) }
        val removed = hashSetOf<String>(); var failures = 0
        for (record in candidates) {
            try {
                val info = destination.info(record.uri)
                if (info == null) { removed.add(record.uri); continue }
                require(info.name == record.name)
                require(destination.read(record.uri).use { digestBackup(it, record.size) } == EncryptedFileDigest(record.size, record.sha256))
                if (destination.delete(record.uri)) removed.add(record.uri) else failures++
            } catch (_: Exception) { failures++ } // Missing access or changed files must never trigger broader deletion.
        }
        return Triple(records.filterNot { it.uri in removed }, failures, true)
    }
}
