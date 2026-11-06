package com.focusbyrj.app.util.backup

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Only ciphertext and non-secret metadata cross the document-picker boundary. */
internal data class PreparedManualBackup(
    val id: String,
    val file: File,
    val metadata: BackupRestoreManager.BackupMetadata,
    val digest: EncryptedFileDigest,
    val preparedAt: Long
)

internal object ManualBackupFiles {
    const val LIFETIME_MS = 10 * 60 * 1000L
    private fun folder(context: Context) = File(context.applicationContext.noBackupFilesDir, "manual_backup_staging")
    // This process owns live preparations and their delivered ciphertext until
    // discard. Register before the first write and serialize registration/deletion
    // with pruning so cleanup can never mistake a live file for an orphan.
    private val ownershipLock = Any()
    private val liveFiles = mutableSetOf<String>()

    suspend fun prepare(
        context: Context,
        password: String,
        vaultKey: ByteArray? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        create: suspend (Context, Uri, String, ByteArray?) -> Result<BackupRestoreManager.BackupMetadata> = BackupRestoreManager::createOneTimeEncryptedBackup
    ): PreparedManualBackup {
        var file: File? = null
        var delivered = false
        try {
            val result = withContext(ioDispatcher) {
                val id = UUID.randomUUID().toString()
                val directory = folder(context)
                require(password.trim().length >= 8) { "Use a backup password with at least 8 characters." }
                check(directory.mkdirs() || directory.isDirectory) { "Not enough accessible phone storage to prepare a backup." }
                prune(context)
                val target = File(directory, "$id.enc")
                file = target
                synchronized(ownershipLock) { check(liveFiles.add(target.absolutePath)) }
                // Authenticate the archive AND verify that the restore reader accepts it.
                val metadata = create(context.applicationContext, Uri.fromFile(target), password, vaultKey).getOrThrow()
                currentCoroutineContext().ensureActive()
                val digest = target.inputStream().use { digestBackup(it) { ensureActive() } }
                check(digest.size > 0) { "The encrypted backup is empty." }
                currentCoroutineContext().ensureActive()
                PreparedManualBackup(id, target, metadata, digest, SystemClock.elapsedRealtime())
            }
            currentCoroutineContext().ensureActive()
            delivered = true
            return result
        } finally {
            // Covers prompt cancellation when withContext discards an IO result before
            // dispatching it back to the caller. Ownership has not transferred yet.
            if (!delivered) file?.let(::discardFile)
        }
    }

    fun expired(value: PreparedManualBackup, now: Long = SystemClock.elapsedRealtime()) =
        now < value.preparedAt || now - value.preparedAt >= LIFETIME_MS

    suspend fun save(context: Context, value: PreparedManualBackup, uri: Uri) {
        require(uri.scheme == "content") { "Choose a document location with the system file picker." }
        copyVerified(value,
            { context.contentResolver.openOutputStream(uri, "wt") ?: error("The selected location cannot be written.") },
            { context.contentResolver.openInputStream(uri) ?: error("The selected location cannot be read back for verification.") })
        context.applicationContext.getSharedPreferences("ayva_backup_verification", Context.MODE_PRIVATE).edit()
            .putLong("last_verified_export_ms", System.currentTimeMillis()).apply()
    }

    /** Public-provider success is not enough: compare the read-back ciphertext to the verified source. */
    internal suspend fun copyVerified(value: PreparedManualBackup, output: () -> OutputStream, readBack: () -> InputStream) = withContext(Dispatchers.IO) {
        check(!expired(value)) { "This prepared backup expired. Create a new backup and unlock the vault again if asked." }
        check(value.file.isFile && value.file.inputStream().use { digestBackup(it, value.digest.size) { ensureActive() } } == value.digest) {
            "The prepared backup is missing or changed. Create a new backup; existing backups were not changed."
        }
        currentCoroutineContext().ensureActive()
        output().use { destination ->
            value.file.inputStream().use { source ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    check(copied <= value.digest.size) { "The prepared backup changed during saving." }
                    destination.write(buffer, 0, read)
                }
                check(copied == value.digest.size) { "The prepared backup was incomplete." }
                destination.flush()
            }
        }
        currentCoroutineContext().ensureActive()
        check(readBack().use { digestBackup(it, value.digest.size) { ensureActive() } } == value.digest) {
            "The saved file did not match the verified backup. Check storage or choose another location."
        }
        currentCoroutineContext().ensureActive()
    }

    private fun discardFile(file: File) = synchronized(ownershipLock) {
        try { file.delete() }
        finally { liveFiles.remove(file.absolutePath) }
        Unit
    }

    fun discard(value: PreparedManualBackup?) { value?.file?.let(::discardFile) }

    /** Startup and periodic cleanup remove abandoned ciphertext after process death; never user documents. */
    fun prune(context: Context, now: Long = System.currentTimeMillis()) = synchronized(ownershipLock) {
        folder(context).listFiles()?.filter {
            it.absolutePath !in liveFiles && it.isFile && it.name.matches(Regex("[a-f0-9-]{36}\\.enc")) &&
                (it.lastModified() <= now - LIFETIME_MS || it.lastModified() > now + 300_000)
        }?.forEach { it.delete() }
        Unit
    }
}
