package com.focusbyrj.app.util.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.R
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A scheduled full manual-format export. Local recovery snapshots remain a separate feature. */
internal object AutomaticBackups {
    private const val PERIODIC = "run_automatic_full_backup_check_v1"
    private const val IMMEDIATE = "run_automatic_full_backup_now_v1"
    private const val CHANNEL = "run_backup_health"
    private const val NOTICE = 8407
    private val operation = Mutex()
    private val active = kotlinx.coroutines.flow.MutableStateFlow(false)
    val running: kotlinx.coroutines.flow.StateFlow<Boolean> = active
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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
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

    /** All job/configuration mutations serialize; foreground manual restore still uses its existing maintenance barrier. */
    suspend fun run(context: Context, force: Boolean = false,
        store: AutomaticBackupStore = AutomaticBackupStore(context),
        destinationFactory: (String) -> BackupDestination = { SafBackupDestination(context, it) },
        createArchive: suspend (Context, Uri, String, ByteArray?) -> Result<BackupRestoreManager.BackupMetadata> = BackupRestoreManager::createAutomaticEncryptedBackup,
        requestId: String = "", expectedScope: BackupRequestScope? = null
    ): Boolean = edit {
        val app = context.applicationContext
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
        var phase = "KEY"
        active.value = true
        try {
            store.save(config.copy(lastAttempt = now))
            store.key().use { key ->
                phase = "BOOKKEEPING"
                store.repairBookkeeping()
                phase = "DESTINATION"
                val destination = destinationFactory(config.tree)
                if (destination is SafBackupDestination) destination.probe()
                val folder = File(app.noBackupFilesDir, "automatic_backup_staging").apply { check(mkdirs() || isDirectory) }
                var staged = store.staged()
                fun stageFile(value: ManagedBackup): File {
                    val file = File(Uri.parse(value.uri).path ?: error("Invalid staged backup"))
                    require(file.canonicalFile.parentFile == folder.canonicalFile && file.name.matches(Regex("[a-f0-9-]{36}\\.enc")))
                    return file
                }
                val usable = staged?.takeIf { it.stream == config.stream && it.keyId == config.keyId &&
                    it.vaultAuthorization == store.vaultAuthorizationToken() && it.created <= now + 300_000L &&
                    (requestId.isBlank() || it.requestId == requestId) && it.created >= config.lastSuccess && config.frequency.nextAfter(it.created) > now }?.let { value ->
                    runCatching { val f = stageFile(value); f.isFile && f.inputStream().use { digestBackup(it, value.size) } == EncryptedFileDigest(value.size, value.sha256) }.getOrDefault(false)
                } == true
                if (!usable) {
                    phase = "ARCHIVE"
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
                val stage = checkNotNull(staged)
                val file = stageFile(stage)
                phase = "VERIFY"
                val name = "RUN-Auto-${config.stream}-${config.keyId}-${stage.created}-${UUID.randomUUID()}.focusbackup"
                val published = AutomaticBackupFiles.publish(destination, name, EncryptedFileDigest(stage.size, stage.sha256)) { file.inputStream() }
                val record = stage.copy(uri = published.uri, name = published.name)
                // Save ownership durably before retention. An interrupted ledger write never deletes old copies.
                val records = store.records() + record
                store.records(records)
                phase = "RETENTION"
                val (kept, failures, retainedVerified) = AutomaticBackupFiles.prune(destination, store.records(), config.stream, config.keyId, config.keep)
                store.records(kept)
                if (!retainedVerified) { phase = "VERIFY"; error("The committed destination copy could no longer be verified; local retry archive retained.") }
                val nextDue = config.frequency.nextAfter(stage.created).coerceAtLeast(System.currentTimeMillis() + 60_000L)
                store.save(store.config().copy(lastSuccess = stage.created, nextDue = nextDue, lastFile = published.name,
                    problem = if (failures > 0) "RETENTION" else "",
                    detail = if (failures > 0) "New backup verified. $failures older files were kept because safe deletion could not be confirmed." else ""))
                store.staged(null); file.delete()
                // Clean uncommitted local stages only after a fresh external copy is protected.
                folder.listFiles()?.filter { it.isFile && it.name.matches(Regex("[a-f0-9-]{36}\\.enc")) }
                    ?.forEach { runCatching { it.delete() } }
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTICE)
                if (failures > 0) notifyProblem(app)
            }
            true
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val detail = when(phase) {
                "KEY" -> "The protected backup key could not be opened. Import your exported recovery key; no replacement key was generated."
                "BOOKKEEPING" -> "Automatic backup records could not be saved. Check device storage. Existing backup files were kept."
                "DESTINATION" -> "The backup folder is unavailable or its permission was lost. Reconnect it or choose a folder again."
                "ARCHIVE" -> "A complete encrypted archive could not be created. Check storage and private-vault authorization. No private notes were omitted."
                "VERIFY" -> "The destination did not return a complete matching archive. A verified encrypted local copy is kept for retry."
                else -> "A new archive was verified, but backup bookkeeping or cleanup could not finish. Check the backup folder and device storage."
            }
            runCatching { store.save(store.config().copy(problem = phase, detail = detail, lastAttempt = now)) }
            android.util.Log.w("AutomaticBackups", "Automatic full backup failed in $phase (${error.javaClass.simpleName})")
            notifyProblem(app)
            false
        } finally { active.value = false }
    }
    fun notifyProblem(context: Context) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Backup health", NotificationManager.IMPORTANCE_DEFAULT))
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val open = PendingIntent.getActivity(context, NOTICE, Intent(context, MainActivity::class.java)
                .putExtra("navigate_to", "settings_automatic_backups"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(NOTICE, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Automatic backup needs attention").setContentText("Open backup settings to check your recovery protection.")
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())
        }
    }
}

class AutomaticFullBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { return try {
        val config = AutomaticBackupStore(applicationContext).config()
        val force = inputData.getBoolean("force", false)
        val expectedScope = if (force) BackupRequestScope(inputData.getString("setup_stream").orEmpty(), inputData.getString("setup_key").orEmpty(), inputData.getString("request_epoch").orEmpty()) else null
        if (expectedScope != null && (expectedScope.stream.isBlank() || expectedScope.keyId.isBlank() || expectedScope.epoch.isBlank() || !expectedScope.matches(config, AutomaticBackupStore(applicationContext).requestEpoch()))) return Result.success()
        val now = System.currentTimeMillis()
        val zoneChanged = config.scheduleZone != java.time.ZoneId.systemDefault().id
        val clockMovedBack = config.lastAttempt > now + 300_000L
        if ((!config.enabled && !force) || !config.keyAcknowledged || config.tree.isBlank() ||
            (!force && config.nextDue > now && !zoneChanged && !clockMovedBack)) return Result.success()
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = "run_backup_progress"
        notifications.createNotificationChannel(NotificationChannel(channel, "Backup progress", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 8408, Intent(applicationContext, MainActivity::class.java)
            .putExtra("navigate_to", "settings_automatic_backups"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, channel).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Creating encrypted backup").setContentText("The archive is verified before older backups are removed.")
            .setContentIntent(open).setOngoing(true).setSilent(true).build()
        try { setForeground(ForegroundInfo(8408, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            AutomaticBackups.edit {
                val store = AutomaticBackupStore(applicationContext)
                store.save(store.config().copy(problem = "SYSTEM", detail = "Android could not start a long-running backup. Open RUN and try Back up now. Existing backups were kept."))
            }
            AutomaticBackups.notifyProblem(applicationContext)
            return Result.retry()
        }
        if (AutomaticBackups.run(applicationContext, force, requestId = inputData.getString("request_id").orEmpty(), expectedScope = expectedScope)) Result.success() else Result.retry()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { AutomaticBackups.notifyProblem(applicationContext); Result.retry() } }
}
