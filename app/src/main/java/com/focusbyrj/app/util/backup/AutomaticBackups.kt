package com.focusbyrj.app.util.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.*
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A scheduled full manual-format export. Local recovery snapshots remain a separate feature. */
internal object AutomaticBackups {
    private const val PERIODIC = "run_automatic_full_backup_check_v1"
    private const val IMMEDIATE = "run_automatic_full_backup_now_v1"
    private val operation = Mutex()
    private val active = kotlinx.coroutines.flow.MutableStateFlow(false)
    val running: kotlinx.coroutines.flow.StateFlow<Boolean> = active
    private val mutableProgress = kotlinx.coroutines.flow.MutableStateFlow("Starting backup")
    val progress: kotlinx.coroutines.flow.StateFlow<String> = mutableProgress
    fun pendingNow(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(IMMEDIATE)
        .map { work -> work.any { !it.state.isFinished } }
    suspend fun <T> edit(block: suspend () -> T): T = withContext(Dispatchers.IO) { operation.withLock { block() } }

    fun schedule(context: Context) {
        val store = AutomaticBackupStore(context)
        val config = store.config()
        val manager = WorkManager.getInstance(context)
        if (!config.enabled) { manager.cancelUniqueWork(PERIODIC); return }
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true)
            .setRequiresCharging(config.chargingOnly).build()
        // Calendar due times live in configuration. This periodic checker survives reboot/process death.
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutomaticFullBackupWorker>(15, TimeUnit.MINUTES).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
    }
    suspend fun requestNow(context: Context) = edit {
        val store = AutomaticBackupStore(context); val config = store.config()
        require(config.keyExported && config.keyAcknowledged && config.tree.isNotBlank()) { "Finish recovery-key export and destination setup first." }
        store.invalidateRequests()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutomaticFullBackupWorker>().setInputData(workDataOf("force" to true, "request_id" to UUID.randomUUID().toString(),
                "setup_stream" to config.stream, "setup_key" to config.keyId, "request_epoch" to store.requestEpoch()))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()).await()
    }
    suspend fun enable(context: Context, enabled: Boolean, store: AutomaticBackupStore = AutomaticBackupStore(context)) = edit {
        val config = store.config()
        if (enabled) { store.key().use { }; SafBackupDestination(context, config.tree).probe() }
        if (!enabled) store.invalidateRequests()
        store.save(config.copy(enabled = enabled, nextDue = if (enabled && (config.nextDue == 0L || config.lastSuccess == 0L)) System.currentTimeMillis() else config.nextDue))
        if (!enabled) withTimeout(10_000) { WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE).await() }
        schedule(context)
    }
    suspend fun configure(context: Context, frequency: BackupFrequency? = null, keep: Int? = null, charging: Boolean? = null) = edit {
        val store = AutomaticBackupStore(context); val value = store.config()
        val next = if (frequency != null && frequency != value.frequency) frequency.nextAfter(System.currentTimeMillis()) else value.nextDue
        store.save(value.copy(frequency = frequency ?: value.frequency, keep = keep ?: value.keep, chargingOnly = charging ?: value.chargingOnly, nextDue = next))
        schedule(context)
    }
    suspend fun destination(context: Context, uri: Uri) = edit {
        require(uri.scheme == "content" && android.provider.DocumentsContract.isTreeUri(uri))
        val store = AutomaticBackupStore(context); val old = store.config()
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            SafBackupDestination(context, uri.toString()).probe()
            if (old.tree != uri.toString()) store.save(old.copy(tree = uri.toString(), enabled = false,
                stream = UUID.randomUUID().toString(), nextDue = 0, lastSuccess = 0, lastFile = "", problem = "", detail = ""))
        } catch (e: Exception) {
            if (old.tree != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
            throw e
        }
        if (old.tree.isNotBlank() && old.tree != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old.tree), flags) }
        schedule(context)
    }
    suspend fun installKey(context: Context, input: String? = null, replace: Boolean = false) = edit {
        val store = AutomaticBackupStore(context)
        require(input != null || !store.hasKey() || replace) { "A recovery key already exists. Export it before explicitly creating a replacement." }
        val keyId = (input?.let(BackupRecoveryKey::parse) ?: BackupRecoveryKey.generate()).use { store.install(it, resetVaultBinding = true); it.id }
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
        schedule(context)
        keyId
    }
    suspend fun repairConfiguration(context: Context) = edit {
        val store = AutomaticBackupStore(context)
        store.storedKey().use { store.install(it) }
        store.save(store.config().copy(enabled = false, tree = "", stream = UUID.randomUUID().toString(),
            keyExported = false, keyAcknowledged = false, lastSuccess = 0, nextDue = 0, lastFile = "", problem = "", detail = ""))
        schedule(context)
    }
    suspend fun exportKey(context: Context, uri: Uri, asPdf: Boolean, expectedKeyId: String? = null,
        store: AutomaticBackupStore = AutomaticBackupStore(context)) = edit {
        require(expectedKeyId == null || store.config().keyId == expectedKeyId) { "The key changed while the file picker was open. Export the current key again." }
        val authorization = ArchiveVaultSecurity.getActiveVaultSubKey()
        try {
            if (ArchiveVaultSecurity.getVaultStatus(context) == ArchiveVaultSecurity.VaultStatus.ENABLED && authorization == null)
                throw SecurityException("Unlock your private vault before exporting a recovery key that can access its backups.")
            store.requireVaultIdentity(authorization)
        } finally { authorization?.fill(0) }
        store.key().use { BackupRecoveryDocuments.export(context, uri, it, asPdf) }
        store.save(store.config().copy(keyExported = true))
    }
    suspend fun acknowledgeKey(context: Context, acknowledged: Boolean, expectedKeyId: String? = null) = edit {
        val store = AutomaticBackupStore(context); val c = store.config()
        require(expectedKeyId == null || c.keyId == expectedKeyId) { "The recovery key changed. Check the current exported document first." }
        require(!acknowledged || c.keyExported)
        store.save(c.copy(keyAcknowledged = acknowledged, enabled = c.enabled && acknowledged)); schedule(context)
    }
    suspend fun authorizeVault(context: Context) = edit {
        val key = ArchiveVaultSecurity.getActiveVaultSubKey() ?: error("Unlock your private vault first, then authorize its automatic backup.")
        try { AutomaticBackupStore(context).authorizeVault(key) } finally { key.fill(0) }
    }

    suspend fun revokeVault(context: Context, store: AutomaticBackupStore = AutomaticBackupStore(context)): Int = edit {
        val remaining = store.revokeVault()
        // The durable epoch rejects queued requests even if WorkManager cancellation is delayed.
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
        remaining
    }

    /** All job/configuration mutations serialize; foreground manual restore still uses its existing maintenance barrier. */
    suspend fun run(context: Context, force: Boolean = false,
        store: AutomaticBackupStore = AutomaticBackupStore(context),
        destinationFactory: (String) -> BackupDestination = { SafBackupDestination(context, it) },
        createArchive: suspend (Context, Uri, String, ByteArray?) -> Result<BackupRestoreManager.BackupMetadata> = BackupRestoreManager::createAutomaticEncryptedBackup,
        requestId: String = "", expectedScope: BackupRequestScope? = null,
        startForeground: suspend () -> Unit = {},
        reportNotification: (String) -> Unit = {}
    ): Boolean = withContext(BackupProgress {
        mutableProgress.value = it
        reportNotification(it)
    }) { edit {
        val app = context.applicationContext
        val work = currentCoroutineContext()
        var config = store.config()
        // Check under the same mutex as key/folder changes, not only in the worker's preflight.
        if (expectedScope != null && !expectedScope.matches(config, store.requestEpoch())) return@edit true
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault().id
        if (config.enabled && (config.scheduleZone != zone || config.lastAttempt > now + 300_000L)) {
            val due = if (config.lastAttempt > now + 300_000L || config.lastSuccess == 0L) now else config.frequency.nextAfter(config.lastSuccess)
            config = config.copy(scheduleZone = zone, nextDue = due)
            store.save(config)
        }
        if ((!config.enabled && !force) || (!force && config.nextDue > now)) return@edit true
        if (!config.keyExported || !config.keyAcknowledged || config.tree.isBlank()) return@edit true
        var phase = "SYSTEM"
        fun stage(value: String) {
            phase = value
            work.ensureActive()
            BackupProgress.report(when (value) {
                "ARCHIVE" -> "Preparing backup"
                "VERIFY" -> "Saving and verifying"
                "RETENTION" -> "Finishing backup"
                "DESTINATION" -> "Checking backup folder"
                else -> "Starting backup"
            })
        }
        active.value = true
        val activity = BackupActivityLog.begin(app, BackupActivityLog.Operation.AUTOMATIC)
        var outcome = BackupActivityLog.Outcome.INTERRUPTED
        var reason: BackupActivityLog.Reason? = null
        try {
            startForeground()
            stage("KEY")
            store.save(config.copy(lastAttempt = now))
            store.key().use { key ->
                stage("BOOKKEEPING")
                store.repairBookkeeping()
                stage("DESTINATION")
                val destination = destinationFactory(config.tree)
                if (destination is SafBackupDestination) destination.probe()
                val folder = File(app.noBackupFilesDir, "automatic_backup_staging").apply { check(mkdirs() || isDirectory) }
                var staged = store.staged()
                fun stageFile(value: ManagedBackup): File {
                    val file = File(Uri.parse(value.uri).path ?: error("Invalid staged backup"))
                    require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.matches(Regex("[a-f0-9-]{36}\\.enc")))
                    return file
                }
                val ledger = store.records()
                val authorization = store.vaultAuthorizationToken()
                val eligible = staged?.takeIf { it.stream == config.stream && it.keyId == config.keyId &&
                    it.vaultAuthorization == authorization && it.created <= now + 300_000L &&
                    (requestId.isBlank() || it.requestId == requestId) && it.created >= config.lastSuccess &&
                    config.frequency.nextAfter(it.created) > now }
                val receipt = ledger.asReversed().firstOrNull { record ->
                    record.stream == config.stream && record.keyId == config.keyId &&
                        record.vaultAuthorization == authorization &&
                        record.created in config.lastSuccess..(now + 300_000L) &&
                        (eligible?.let { record.created == it.created && record.size == it.size && record.sha256 == it.sha256 }
                            ?: (requestId.isNotBlank() && record.requestId == requestId)) &&
                        AutomaticBackupFiles.verifies(destination, record, work::ensureActive)
                }
                val usable = eligible?.let { value ->
                    runCatching {
                        val file = stageFile(value)
                        file.isFile && file.inputStream().use { digestBackup(it, value.size) } == EncryptedFileDigest(value.size, value.sha256)
                    }.getOrDefault(false)
                } == true
                if (receipt == null && !usable) {
                    stage("ARCHIVE")
                    val file = File(folder, "${UUID.randomUUID()}.enc")
                    var installed = false
                    try {
                        val vaultKey = store.vaultKey()
                        val result = try { createArchive(app, Uri.fromFile(file), key.credential(), vaultKey) }
                            finally { vaultKey?.fill(0) }
                        val metadata = result.getOrThrow()
                        val digest = file.inputStream().use { digestBackup(it) }
                        val next = ManagedBackup(Uri.fromFile(file).toString(), file.name, config.stream, config.keyId, metadata.createdAt, digest.size, digest.sha256, store.vaultAuthorizationToken(), requestId)
                        val previous = staged
                        store.staged(next); staged = next; installed = true
                        previous?.let { runCatching { stageFile(it).delete() } }
                    } finally { if (!installed) file.delete() }
                }
                stage("VERIFY")
                val record = receipt ?: run {
                    val stage = checkNotNull(staged)
                    val file = stageFile(stage)
                    val name = "RUN-Auto-${config.stream}-${config.keyId}-${stage.created}-${UUID.randomUUID()}.focusbackup"
                    val published = AutomaticBackupFiles.publish(destination, name, EncryptedFileDigest(stage.size, stage.sha256),
                        work::ensureActive) { file.inputStream() }
                    stage.copy(uri = published.uri, name = published.name).also {
                        // Record ownership before retention. A failed write leaves older copies intact.
                        store.records(ledger + it)
                    }
                }
                stage("RETENTION")
                val ordered = ledger.filterNot { it.uri == record.uri } + record
                val (kept, failures, retainedVerified) = AutomaticBackupFiles.prune(destination, ordered, config.stream, config.keyId, config.keep, work::ensureActive)
                store.records(kept)
                if (!retainedVerified) { stage("VERIFY"); error("The committed destination copy could no longer be verified; local retry archive retained.") }
                work.ensureActive()
                val nextDue = config.frequency.nextAfter(record.created).coerceAtLeast(System.currentTimeMillis() + 60_000L)
                store.save(store.config().copy(lastSuccess = record.created, nextDue = nextDue, lastFile = record.name,
                    problem = if (failures > 0) "RETENTION" else "",
                    detail = if (failures > 0) "New backup verified. $failures older files were kept because safe deletion could not be confirmed." else ""))
                app.getSharedPreferences("ayva_backup_verification", Context.MODE_PRIVATE).edit()
                    .putLong("last_verified_export_ms", System.currentTimeMillis()).apply()
                store.staged(null)
                // Clean uncommitted local stages only after a fresh external copy is protected.
                folder.listFiles()?.filter { it.isFile && it.name.matches(Regex("[a-f0-9-]{36}\\.enc")) }
                    ?.forEach { runCatching { it.delete() } }
                outcome = if (failures > 0) BackupActivityLog.Outcome.WARNING else BackupActivityLog.Outcome.COMPLETED
                if (failures > 0) reason = BackupActivityLog.Reason.RETENTION
                AutomaticBackupNotifications(app).complete(retentionWarning = failures > 0)
            }
            true
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            outcome = BackupActivityLog.Outcome.RETRY_SCHEDULED
            reason = BackupActivityLog.Reason.entries.firstOrNull { it.name == phase }
            val detail = when(phase) {
                "SYSTEM" -> "Android could not start a long-running backup. Open Ayva and try Back up now. Existing backups were kept."
                "KEY" -> "The protected backup key could not be opened. Import your exported recovery key; no replacement key was generated."
                "BOOKKEEPING" -> "Automatic backup records could not be saved. Check device storage. Existing backup files were kept."
                "DESTINATION" -> "The backup folder is unavailable or its permission was lost. Reconnect it or choose a folder again."
                "ARCHIVE" -> "A complete encrypted archive could not be created. Check storage and private-vault authorization. No private notes were omitted."
                "VERIFY" -> "The destination did not return a complete matching archive. A verified encrypted local copy is kept for retry."
                else -> "A new archive was verified, but backup bookkeeping or cleanup could not finish. Check the backup folder and device storage."
            }
            runCatching { store.save(store.config().copy(problem = phase, detail = detail, lastAttempt = now)) }
            android.util.Log.w("AutomaticBackups", "Automatic full backup failed in $phase (${error.javaClass.simpleName})")
            AutomaticBackupNotifications(app).failure()
            false
        } finally {
            BackupActivityLog.finish(app, activity, outcome, reason)
            active.value = false
        }
    } }
    suspend fun recordWorkerFailure(context: Context) = edit {
        val activity = BackupActivityLog.begin(context, BackupActivityLog.Operation.AUTOMATIC)
        runCatching {
            val store = AutomaticBackupStore(context)
            store.save(store.config().copy(problem = "SYSTEM",
                detail = "Android could not finish checking backup setup. Open backup settings and try again. Existing backups were kept."))
        }
        BackupActivityLog.finish(context, activity, BackupActivityLog.Outcome.RETRY_SCHEDULED, BackupActivityLog.Reason.SYSTEM)
    }

}

class AutomaticFullBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val notifications = AutomaticBackupNotifications(applicationContext)
        val work = currentCoroutineContext()
        // WorkManager owns removal on success, failure and cancellation. Use a work-specific ID
        // so an older work request cannot remove another request's foreground notification.
        val notificationId = 0x40000000 or (id.hashCode() and 0x3fffffff)
        return try {
            val force = inputData.getBoolean("force", false)
            val expectedScope = if (force) BackupRequestScope(
                inputData.getString("setup_stream").orEmpty(),
                inputData.getString("setup_key").orEmpty(),
                inputData.getString("request_epoch").orEmpty()
            ) else null
            if (expectedScope != null && (expectedScope.stream.isBlank() ||
                    expectedScope.keyId.isBlank() || expectedScope.epoch.isBlank())) return Result.success()
            val succeeded = AutomaticBackups.run(
                applicationContext, force,
                requestId = inputData.getString("request_id").orEmpty(),
                expectedScope = expectedScope,
                startForeground = {
                    setForeground(ForegroundInfo(notificationId, notifications.progress(notificationId, "Preparing backup"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                },
                reportNotification = { if (work.isActive) notifications.updateProgress(notificationId, it) }
            )
            if (succeeded) Result.success() else Result.retry()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AutomaticBackups.recordWorkerFailure(applicationContext)
            notifications.failure()
            Result.retry()
        }
    }
}
