package com.focusbyrj.app.util.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.time.*
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Tests inject a software wrapper; production has no software/constant-key fallback. */
internal class TestBackupWrapper : BackupKeyWrapper {
    private val key = SecretKeySpec(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }, "AES")
    var unavailable = false
    var corruptWrap = false
    override fun wrap(bytes: ByteArray, purpose: String): ByteArray {
        check(!unavailable); val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key); c.updateAAD(purpose.toByteArray())
        return (c.iv + c.doFinal(bytes)).also { if(corruptWrap) it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
    }
    override fun unwrap(bytes: ByteArray, purpose: String): ByteArray {
        check(!unavailable); val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));c.updateAAD(purpose.toByteArray())
        return c.doFinal(bytes,12,bytes.size-12)
    }
}
internal class MemoryBackupDestination : BackupDestination {
    val files = linkedMapOf<String, Pair<String,ByteArray>>()
    var reads = 0; var corruptOnRead: Int? = null
    var corruptWrites = false; var renameSupported = true; var refuseDelete = false; var denied = false
    override fun create(name: String): BackupDocument {
        check(!denied); val uri="memory://${UUID.randomUUID()}"; files[uri]=name to byteArrayOf(); return BackupDocument(uri,name,0)
    }
    override fun read(uri: String): InputStream { check(!denied);reads++;val bytes=files.getValue(uri).second.copyOf();if(reads==corruptOnRead && bytes.isNotEmpty())bytes[0]=(bytes[0].toInt() xor 1).toByte();return bytes.inputStream() }
    override fun write(uri: String): OutputStream { check(!denied); return object: ByteArrayOutputStream() {
        override fun close() { val bytes=toByteArray();if(corruptWrites && bytes.isNotEmpty())bytes[0]=(bytes[0].toInt() xor 1).toByte();files[uri]=files.getValue(uri).first to bytes }
    } }
    override fun info(uri: String): BackupDocument? { check(!denied);return files[uri]?.let { BackupDocument(uri,it.first,it.second.size.toLong()) } }
    override fun rename(uri: String,name: String): BackupDocument? {
        if(!renameSupported)return null
        val old=files.remove(uri)!!;val next="memory://${UUID.randomUUID()}";files[next]=name to old.second;return info(next)
    }
    override fun delete(uri: String): Boolean { check(!denied);return if(refuseDelete)false else files.remove(uri)!=null }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36], application=Application::class)
class AutomaticBackupsTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>()
    private fun store(wrapper: TestBackupWrapper=TestBackupWrapper())=AutomaticBackupStore(app,wrapper)
    private fun time(value:String)=Instant.parse(value).toEpochMilli()

    private fun readyStore():AutomaticBackupStore = store().also { store ->
        BackupRecoveryKey.generate().use {store.install(it)}
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true,keep=1))
    }
    private fun fixtureCreator(onCreate:()->Long = {System.currentTimeMillis()}):suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = { _,uri,_,_ ->
        val created=onCreate();File(uri.path!!).writeText("encrypted archive fixture $created")
        Result.success(BackupRestoreManager.BackupMetadata(2,created,"test",0,0,0,0,0,0))
    }
    @Test fun damagedRetryMetadataDoesNotPermanentlyStopFreshBackups() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination()
        app.getSharedPreferences("automatic_full_backups_v1",0).edit().putString("stage","broken retry metadata").commit()
        assertTrue(AutomaticBackups.run(app,true,store,{d},fixtureCreator()))
        assertEquals(1,store.records().size);assertNull(store.staged())
    }
    @Test fun damagedOwnershipBookkeepingKeepsUnknownFilesAndStillCreatesFreshProtection() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination();val old=d.create("precious.focusbackup")
        d.write(old.uri).use{it.write("older backup".toByteArray())}
        app.getSharedPreferences("automatic_full_backups_v1",0).edit().putString("records","broken ownership metadata").commit()
        assertTrue(AutomaticBackups.run(app,true,store,{d},fixtureCreator()))
        assertTrue(d.files.containsKey(old.uri));assertEquals(1,store.records().size);assertTrue(store.ownershipLimitReached())
    }
    @Test fun aRetryFromAnEarlierScheduleCannotReplaceTodaysCapture() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination().apply{corruptWrites=true};var captures=0
        store.save(store.config().copy(frequency=BackupFrequency(anchor=System.currentTimeMillis()-7*86_400_000L)))
        val creator=fixtureCreator {captures++;System.currentTimeMillis()-if(captures==1)3*86_400_000L else 0L}
        assertFalse(AutomaticBackups.run(app,true,store,{d},creator));assertNotNull(store.staged())
        d.corruptWrites=false
        assertTrue(AutomaticBackups.run(app,true,store,{d},creator));assertEquals(2,captures)
        assertTrue(store.config().lastSuccess>System.currentTimeMillis()-60_000L)
    }
    @Test fun retentionNeverDeletesOlderCopiesWhenTheRetainedNewestCopyDisappeared() {
        val d=MemoryBackupDestination();val c=readyStore().config()
        val records=(1..3).map {n ->val f=d.create("$n.focusbackup");val bytes="archive $n".toByteArray();d.write(f.uri).use{it.write(bytes)}
            ManagedBackup(f.uri,f.name,c.stream,c.keyId,n.toLong(),bytes.size.toLong(),bytes.inputStream().use{digestBackup(it).sha256})}
        d.files.remove(records.last().uri)
        val (kept,errors)=AutomaticBackupFiles.prune(d,records,c.stream,c.keyId,1)
        assertTrue(errors>0);assertTrue(d.files.containsKey(records.first().uri));assertTrue(d.files.containsKey(records[1].uri))
        assertEquals(records,kept)
    }
    @Test fun aDamagedWrappedReplacementCannotOverwriteTheCurrentRecoveryCredential() {
        val wrapper=TestBackupWrapper();val store=store(wrapper)
        BackupRecoveryKey.generate().use {store.install(it)}
        val previous=app.getSharedPreferences("automatic_full_backups_v1",0).all.toMap()
        wrapper.corruptWrap=true
        BackupRecoveryKey.generate().use {next ->assertThrows(Exception::class.java){store.install(next)}}
        assertEquals(previous,app.getSharedPreferences("automatic_full_backups_v1",0).all)
    }
    @Test fun malformedRecoveryDocumentsNeverExposeTheCredentialInErrors() {
        BackupRecoveryKey.generate().use { key ->
            val malformed=key.document().replace("\"keyId\":", "\"keyId\" !")
            val error=assertThrows(Exception::class.java){BackupRecoveryKey.parse(malformed)}
            assertFalse(error.stackTraceToString().contains(key.credential()))
        }
    }
    @Test fun aNewExplicitBackupRequestCannotReuseThePriorRequestsCapture() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination().apply{corruptWrites=true};var captures=0
        val creator=fixtureCreator {captures++;System.currentTimeMillis()}
        val first=UUID.randomUUID().toString();val second=UUID.randomUUID().toString()
        assertFalse(AutomaticBackups.run(app,true,store,{d},creator,requestId=first))
        assertEquals(first,store.staged()!!.requestId)
        d.corruptWrites=false
        assertTrue(AutomaticBackups.run(app,true,store,{d},creator,requestId=second))
        assertEquals(2,captures);assertEquals(second,store.records().single().requestId)
    }
    @Test fun retryingTheSameExplicitRequestReusesItsAuthenticatedCapture() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination().apply{corruptWrites=true};var captures=0
        val creator=fixtureCreator {captures++;System.currentTimeMillis()};val id=UUID.randomUUID().toString()
        assertFalse(AutomaticBackups.run(app,true,store,{d},creator,requestId=id))
        d.corruptWrites=false
        assertTrue(AutomaticBackups.run(app,true,store,{d},creator,requestId=id));assertEquals(1,captures)
    }
    @Test fun changingReadBackAfterPublicationPreservesTheLocalStageAndTheOlderOwnedArchive() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination()
        assertTrue(AutomaticBackups.run(app,true,store,{d},fixtureCreator()))
        val before=store.config();val older=store.records().single()
        d.corruptOnRead=d.reads+3 // temporary verification, rename verification, retention anchor verification
        assertFalse(AutomaticBackups.run(app,true,store,{d},fixtureCreator()))
        assertNotNull(store.staged());assertTrue(d.files.containsKey(older.uri))
        assertEquals(before.lastSuccess,store.config().lastSuccess);assertEquals("VERIFY",store.config().problem)
    }
    @Test fun uncommittedEncryptedStagesSurviveFailedCapturesAndAreOnlyCleanedAfterExternalSuccess() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination()
        val orphan=File(File(app.noBackupFilesDir,"automatic_backup_staging").apply{mkdirs()},"${UUID.randomUUID()}.enc").apply{writeText("old encrypted capture")}
        val failure:suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = {_,_,_,_->Result.failure(IOException("storage unavailable"))}
        assertFalse(AutomaticBackups.run(app,true,store,{d},failure));assertTrue(orphan.isFile)
        assertTrue(AutomaticBackups.run(app,true,store,{d},fixtureCreator()));assertFalse(orphan.exists())
    }
    @Test fun cancellationCannotDeleteOlderArchivesOrLeaveTheRunnerMarkedActive() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination()
        assertTrue(AutomaticBackups.run(app,true,store,{d},fixtureCreator()));val older=store.records().single()
        val cancelled:suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = {_,uri,_,_->File(uri.path!!).writeText("partial");throw kotlinx.coroutines.CancellationException("cancelled fixture")}
        try {AutomaticBackups.run(app,true,store,{d},cancelled);fail("Cancellation must propagate")}catch(_:kotlinx.coroutines.CancellationException){}
        assertTrue(d.files.containsKey(older.uri));assertFalse(AutomaticBackups.running.value)
        assertNull(store.staged());assertEquals(listOf(older),store.records())
    }
    @Test fun restoreSizeLimitsAndNestingAreAppliedWithoutConfusingQuotedNoteText() {
        BackupArchiveLimits.checkExport(BackupArchiveLimits.MANIFEST_BYTES,listOf(BackupArchiveLimits.MEDIA_ENTRY_BYTES,BackupArchiveLimits.MEDIA_ENTRY_BYTES))
        assertThrows(IllegalArgumentException::class.java){BackupArchiveLimits.checkExport(BackupArchiveLimits.MANIFEST_BYTES+1,emptyList())}
        assertThrows(IllegalArgumentException::class.java){BackupArchiveLimits.checkExport(0,listOf(BackupArchiveLimits.MEDIA_ENTRY_BYTES+1))}
        assertThrows(IllegalArgumentException::class.java){BackupArchiveLimits.checkExport(0,listOf(BackupArchiveLimits.MEDIA_ENTRY_BYTES,BackupArchiveLimits.MEDIA_ENTRY_BYTES,1))}
        assertThrows(IllegalArgumentException::class.java){BackupArchiveLimits.checkExport(0,List(BackupArchiveLimits.ENTRIES){0L})}
        val document=JSONObject().put("text","[".repeat(500)+"\\\""+"}".repeat(500)).toString()
        assertEquals(JSONObject(document).getString("text"),BackupArchiveLimits.parseManifest(document).getString("text"))
        assertThrows(IllegalArgumentException::class.java){BackupArchiveLimits.parseManifest("{\"unused\":"+"[".repeat(10000)+"0"+"]".repeat(10000)+"}")}
    }
    @Test fun streamsThatTemporarilyReturnZeroCannotHangVerificationOrKeyImport() {
        val bytes="bounded input".toByteArray()
        fun input()=object:ByteArrayInputStream(bytes){var zero=true;override fun read(buffer:ByteArray,offset:Int,length:Int):Int {zero=!zero;return if(!zero)0 else super.read(buffer,offset,length)}}
        assertEquals(bytes.inputStream().use{digestBackup(it)},input().use{digestBackup(it)})
        with(BackupRecoveryDocuments){assertArrayEquals(bytes,input().use{it.readBytesBounded(bytes.size)})}
    }
    @Test fun aRevokedPrivateVaultAuthorizationCannotBeReplayedUnderANewGeneration() {
        val store=readyStore();store.authorizeVault(ByteArray(32){it.toByte()})
        val prefs=app.getSharedPreferences("automatic_full_backups_v1",0)
        val revoked=prefs.getString("wrapped_vault",null)
        store.revokeVault()
        prefs.edit().putString("wrapped_vault",revoked).commit()
        assertThrows(javax.crypto.AEADBadTagException::class.java){store.vaultKey()}
        store.revokeVault();assertFalse(store.hasVaultAuthorization())
    }
    @Test fun duplicateOwnershipRecordsCannotCauseDeletionOfTheRetainedDocument() {
        val store=readyStore();val c=store.config();val d=MemoryBackupDestination();val bytes="only backup".toByteArray()
        val f=d.create("only.focusbackup");d.write(f.uri).use{it.write(bytes)}
        val record=ManagedBackup(f.uri,f.name,c.stream,c.keyId,1,bytes.size.toLong(),bytes.inputStream().use{digestBackup(it).sha256})
        val result=AutomaticBackupFiles.prune(d,listOf(record,record),c.stream,c.keyId,1)
        assertTrue(result.third);assertEquals(0,result.second);assertTrue(d.files.containsKey(record.uri))
    }
    @Test fun wrongPreferenceTypesCannotPermanentlyBreakBookkeepingRecovery() = runBlocking {
        val store=readyStore();val d=MemoryBackupDestination();val prefs=app.getSharedPreferences("automatic_full_backups_v1",0)
        prefs.edit().putInt("stage",7).putBoolean("records",false).commit()
        assertTrue(AutomaticBackups.run(app,true,store,{d},fixtureCreator()))
        assertNull(store.staged());assertEquals(1,store.records().size);assertTrue(store.ownershipLimitReached())
        prefs.edit().putInt("records",9).commit()
        store.key().use{store.install(it)}
        assertTrue(store.records().isEmpty());assertFalse(store.config().enabled)
    }
    @Test fun recoveryKeysAreRandomPortableChecksummedAndNeverPrintedByToString() {
        val ids=hashSetOf<String>();repeat(256) { BackupRecoveryKey.generate().use { key ->
            assertTrue(ids.add(key.id));assertFalse(key.toString().contains(key.credential()))
            BackupRecoveryKey.parse(key.document()).use { assertEquals(key.credential(),it.credential());assertEquals(key.id,it.id) }
            BackupRecoveryKey.parse(key.credential().lowercase().replace("-"," \n")).use { assertEquals(key.id,it.id) }
            val changed=key.credential().dropLast(1)+(if(key.credential().last()=='0')'1' else '0')
            assertThrows(IllegalArgumentException::class.java) { BackupRecoveryKey.parse(changed) }
        } }
        val closed=BackupRecoveryKey.generate();closed.close()
        assertThrows(IllegalStateException::class.java) { closed.credential() }
        assertThrows(IllegalArgumentException::class.java) { BackupRecoveryKey.parse("{".repeat(2000)+"}".repeat(2000)) }
        BackupRecoveryKey.generate().use { key ->
            assertThrows(IllegalArgumentException::class.java) { BackupRecoveryKey.parse(JSONObject(key.document()).put("keyId","0000000000000000").toString()) }
        }
    }
    @Test fun schedulesHandleMonthEndsLeapYearsDstAndLongMissedPeriods() {
        val utc=ZoneId.of("UTC")
        val monthly=BackupFrequency("MONTHS",1,9,0,time("2024-01-31T00:00:00Z"))
        assertEquals(time("2024-02-29T09:00:00Z"),monthly.nextAfter(time("2024-02-01T00:00:00Z"),utc))
        assertEquals(time("2024-03-31T09:00:00Z"),monthly.nextAfter(time("2024-03-01T00:00:00Z"),utc))
        val dst=BackupFrequency("DAYS",1,2,30,time("2026-03-07T05:00:00Z"))
        assertEquals(time("2026-03-08T07:30:00Z"),dst.nextAfter(time("2026-03-08T05:00:00Z"),ZoneId.of("America/New_York")))
        assertTrue(monthly.nextAfter(time("2040-11-04T12:00:00Z"),utc)>time("2040-11-04T12:00:00Z"))
        assertEquals(time("2026-11-11T03:00:00Z"),BackupFrequency("WEEKS",1,3,0,time("2026-11-04T00:00:00Z")).nextAfter(time("2026-11-04T04:00:00Z"),utc))
        assertEquals(time("2026-11-05T00:00:00Z"),BackupFrequency("HOURS",1,anchor=time("2026-11-05T00:00:00Z")).nextAfter(time("2026-11-04T00:00:00Z"),utc))
    }
    @Test fun settingsRequireRecoveryExportAcknowledgementAndBoundRetention() {
        assertThrows(IllegalArgumentException::class.java) { AutomaticBackupConfig(enabled=true).validate() }
        assertThrows(IllegalArgumentException::class.java) { AutomaticBackupConfig(keep=0).validate() }
        assertThrows(IllegalArgumentException::class.java) { BackupFrequency("YEARS").validate() }
        assertThrows(IllegalArgumentException::class.java) { BackupFrequency("HOURS",25).validate() }
        val c=AutomaticBackupConfig(keep=365,frequency=BackupFrequency("MONTHS",2,23,59))
        assertEquals(c,AutomaticBackupConfig.read(c.json().toString()))
    }
    @Test fun wrappedKeyCannotBeReadAfterDeviceKeyLossAndIsNeverReplacedImplicitly() {
        val wrapper=TestBackupWrapper();val store=store(wrapper)
        BackupRecoveryKey.generate().use { key ->
            store.install(key);store.key().use { assertEquals(key.credential(),it.credential()) }
            val prefs=app.getSharedPreferences("automatic_full_backups_v1",0)
            assertFalse(prefs.all.values.any { it.toString().contains(key.credential()) })
            val previous=prefs.all.toMap();wrapper.unavailable=true
            assertThrows(IllegalStateException::class.java) { store.key() }
            assertEquals(previous,prefs.all)
        }
    }
    @Test fun damagedConfigurationRetainsTheSamePortableKeyAndExplicitImportRepairsIt() {
        val store=store(); val prefs=app.getSharedPreferences("automatic_full_backups_v1",0)
        BackupRecoveryKey.generate().use { key ->
            store.install(key);store.authorizeVault(ByteArray(32){it.toByte()})
            prefs.edit().putString("config","damaged configuration").putString("records","damaged ledger").commit()
            assertThrows(Exception::class.java) {store.key()}
            store.storedKey().use { assertEquals(key.credential(),it.credential());store.install(it) }
            store.key().use {assertEquals(key.credential(),it.credential())}
            assertFalse(store.config().enabled);assertFalse(store.config().keyExported)
            assertFalse(store.hasVaultAuthorization());assertTrue(store.records().isEmpty())
            assertEquals("damaged ledger",prefs.getString("quarantined_records",null))
        }
    }
    @Test fun stalePickerAndAccidentalReplacementCannotChangeOrAcknowledgeAnExistingKey() = runBlocking {
        val store=store();BackupRecoveryKey.generate().use {store.install(it)}
        val previous=app.getSharedPreferences("automatic_full_backups_v1",0).all.toMap()
        try {AutomaticBackups.installKey(app);fail("Replacement must be explicit")}catch(_:IllegalArgumentException){}
        try {AutomaticBackups.exportKey(app,Uri.parse("content://unused/key"),false,"00000000000000000000000000000000");fail("Stale picker must fail")}catch(_:IllegalArgumentException){}
        try {AutomaticBackups.acknowledgeKey(app,true,"00000000000000000000000000000000");fail("Stale acknowledgement must fail")}catch(_:IllegalArgumentException){}
        assertEquals(previous,app.getSharedPreferences("automatic_full_backups_v1",0).all)
    }
    @Test fun calendarAnchorsKeepTheirDateAcrossTimeZonesAndDstOverlapDoesNotDuplicate() {
        val frequency=BackupFrequency("MONTHS",1,9,0,time("2026-01-31T00:00:00Z"))
        assertEquals(time("2026-02-28T17:00:00Z"),frequency.nextAfter(time("2026-02-01T00:00:00Z"),ZoneId.of("America/Los_Angeles")))
        assertEquals(time("2026-02-28T03:30:00Z"),frequency.nextAfter(time("2026-02-01T00:00:00Z"),ZoneId.of("Asia/Kolkata")))
        val dst=BackupFrequency("DAYS",1,1,30,time("2026-10-31T00:00:00Z"))
        assertEquals(time("2026-11-02T06:30:00Z"),dst.nextAfter(time("2026-11-01T05:30:00Z"),ZoneId.of("America/New_York")))
    }
    @Test fun ownershipLedgerOverflowKeepsNewestRecordsWithoutTouchingExternalFiles() {
        val store=store();BackupRecoveryKey.generate().use {store.install(it)}
        val c=store.config();val records=(1..5001).map { ManagedBackup("memory://$it","$it.focusbackup",c.stream,c.keyId,it.toLong(),1,"a".repeat(64)) }
        store.records(records)
        assertEquals(records.takeLast(5000),store.records());assertTrue(store.ownershipLimitReached())
    }
    @Test fun privateVaultAuthorizationIsPurposeBoundRevocableAndIndependentOfExportedKey() {
        val wrapper=TestBackupWrapper();val store=store(wrapper)
        BackupRecoveryKey.generate().use { store.install(it) }
        val vault=ByteArray(32){it.toByte()};store.authorizeVault(vault)
        assertArrayEquals(vault,store.vaultKey());val token=store.vaultAuthorizationToken()
        val prefs=app.getSharedPreferences("automatic_full_backups_v1",0)
        val recovery=prefs.getString("wrapped_key",null);val authorization=prefs.getString("wrapped_vault",null)
        prefs.edit().putString("wrapped_key",authorization).putString("wrapped_vault",recovery).commit()
        assertThrows(javax.crypto.AEADBadTagException::class.java) {store.key()}
        assertThrows(javax.crypto.AEADBadTagException::class.java) {store.vaultKey()}
        prefs.edit().putString("wrapped_key",recovery).putString("wrapped_vault",authorization).commit()
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true))
        store.key().use {store.install(it)}
        assertFalse(store.config().enabled);assertFalse(store.config().keyAcknowledged);assertFalse(store.hasVaultAuthorization())
        store.revokeVault();assertNull(store.vaultKey());assertNotEquals(token,store.vaultAuthorizationToken())
        assertTrue(store.hasKey())
    }
    @Test fun exportCommitSupportsRenameChangingUriAndProvidersWithoutRename() {
        for(rename in listOf(true,false)) {
            val destination=MemoryBackupDestination().apply {renameSupported=rename}
            val bytes="Authenticated encrypted archive fixture".toByteArray();val digest=bytes.inputStream().use { digestBackup(it) }
            val file=AutomaticBackupFiles.publish(destination,"archive.focusbackup",digest){bytes.inputStream()}
            assertEquals("archive.focusbackup",file.name);assertEquals(1,destination.files.size)
            assertArrayEquals(bytes,destination.read(file.uri).readBytes())
        }
    }
    @Test fun corruptedReadBackNeverCommitsAndNeverDeletesExistingBackup() {
        val destination=MemoryBackupDestination();val old=destination.create("manual.focusbackup")
        destination.write(old.uri).use { it.write("precious old backup".toByteArray()) }
        destination.corruptWrites=true
        val bytes="new archive".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { AutomaticBackupFiles.publish(destination,"new.focusbackup",bytes.inputStream().use { digestBackup(it) }){bytes.inputStream()} }
        assertEquals(setOf(old.uri),destination.files.keys)
    }
    @Test fun retentionUsesOwnershipCommitOrderAndDigestRatherThanNamesOrClock() {
        val destination=MemoryBackupDestination();val stream=UUID.randomUUID().toString();val keyId="1234567890ABCDEF1234567890ABCDEF"
        fun record(index:Int, owner:String=stream):ManagedBackup {
            val bytes="backup $index".toByteArray();val f=destination.create("backup-$index.focusbackup");destination.write(f.uri).use {it.write(bytes)}
            return ManagedBackup(f.uri,f.name,owner,keyId,(100-index).toLong(),bytes.size.toLong(),bytes.inputStream().use { digestBackup(it).sha256 })
        }
        val old=record(1);val recent=record(2);val newest=record(3);val other=record(4,UUID.randomUUID().toString())
        val (kept,failures)=AutomaticBackupFiles.prune(destination,listOf(old,recent,newest,other),stream,keyId,1)
        assertEquals(0,failures);assertEquals(listOf(newest,other),kept);assertEquals(setOf(newest.uri,other.uri),destination.files.keys)
        destination.files[newest.uri]=newest.name to "changed!".toByteArray()
        val next=record(5)
        val (protected,errors)=AutomaticBackupFiles.prune(destination,listOf(newest,next),stream,keyId,1)
        assertEquals(1,errors);assertEquals(2,protected.size);assertTrue(destination.files.containsKey(newest.uri))
    }
    @Test fun failedDeletionKeepsExtraCopiesInsteadOfPretendingRetentionSucceeded() {
        val d=MemoryBackupDestination();val stream=UUID.randomUUID().toString();val data="x".toByteArray()
        val records=(1..3).map { val f=d.create("$it.focusbackup");d.write(f.uri).use{it.write(data)};ManagedBackup(f.uri,f.name,stream,"1234567890ABCDEF1234567890ABCDEF",it.toLong(),1,data.inputStream().use{digestBackup(it).sha256}) }
        d.refuseDelete=true;val result=AutomaticBackupFiles.prune(d,records,stream,"1234567890ABCDEF1234567890ABCDEF",1)
        assertEquals(records,result.first);assertEquals(2,result.second)
    }
    @Test fun failedRemoteWriteRetainsVerifiedEncryptedStageAndRetryDoesNotRecreateArchive() = runBlocking {
        val store=store();BackupRecoveryKey.generate().use { store.install(it) }
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true,keep=1))
        val destination=MemoryBackupDestination().apply {corruptWrites=true};var created=0
        val creator:suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = { _,uri,_,_ ->
            created++;File(uri.path!!).writeBytes("encrypted stage $created".toByteArray())
            Result.success(BackupRestoreManager.BackupMetadata(2,System.currentTimeMillis(),"test",0,0,0,0,0,0))
        }
        assertFalse(AutomaticBackups.run(app,true,store,{destination},creator));assertNotNull(store.staged());assertEquals(0,store.config().lastSuccess)
        destination.corruptWrites=false
        assertTrue(AutomaticBackups.run(app,true,store,{destination},creator));assertEquals(1,created);assertNull(store.staged())
        assertEquals(1,store.records().size);assertTrue(store.config().lastSuccess>0)
    }
    @Test fun changingOneSettingPreservesTheOtherSettingsFromTheCurrentStore() = runBlocking {
        runCatching {androidx.work.WorkManager.getInstance(app)}.getOrElse {
            androidx.work.WorkManager.initialize(app,androidx.work.Configuration.Builder().build())
        }
        val store=store();BackupRecoveryKey.generate().use {store.install(it)}
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true))
        AutomaticBackups.configure(app,keep=30)
        AutomaticBackups.configure(app,charging=true)
        val frequency=BackupFrequency("WEEKS",2,4,15)
        AutomaticBackups.configure(app,frequency=frequency)
        val c=store.config();assertEquals(30,c.keep);assertTrue(c.chargingOnly);assertEquals(frequency,c.frequency)
        assertTrue(c.keyAcknowledged);assertEquals("content://test/tree/root",c.tree)
    }
    @Test fun clockRollbackRebasesDueTimeAndReplacesAFutureDatedStage() = runBlocking {
        val store=store();BackupRecoveryKey.generate().use {store.install(it)}
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true))
        val destination=MemoryBackupDestination().apply {corruptWrites=true};var created=0
        val creator:suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = { _,uri,_,_ ->
            created++;File(uri.path!!).writeText("encrypted archive $created")
            Result.success(BackupRestoreManager.BackupMetadata(2,System.currentTimeMillis(),"test",0,0,0,0,0,0))
        }
        assertFalse(AutomaticBackups.run(app,true,store,{destination},creator))
        val future=System.currentTimeMillis()+86_400_000L
        store.staged(store.staged()!!.copy(created=future))
        store.save(store.config().copy(lastAttempt=future,nextDue=future))
        destination.corruptWrites=false
        assertTrue(AutomaticBackups.run(app,false,store,{destination},creator));assertEquals(2,created)
        assertTrue(store.config().lastSuccess<future);assertEquals(1,store.records().size)
    }
    @Test fun anEarlyScheduledCheckDoesNotTryToOpenAnUnavailableDeviceKey() = runBlocking {
        val wrapper=TestBackupWrapper();val store=store(wrapper);BackupRecoveryKey.generate().use {store.install(it)}
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true,nextDue=System.currentTimeMillis()+3_600_000))
        wrapper.unavailable=true
        assertTrue(AutomaticBackups.run(app,false,store,{error("Must not touch destination before due")}))
        assertTrue(store.config().problem.isEmpty());assertEquals(0L,store.config().lastAttempt)
    }
    @Test fun revokingPrivateAuthorizationPreventsPublishingAStagedPrivateBackup() = runBlocking {
        val store=store();BackupRecoveryKey.generate().use { store.install(it) };store.authorizeVault(ByteArray(32){it.toByte()})
        store.save(store.config().copy(tree="content://test/tree/root",keyExported=true,keyAcknowledged=true,enabled=true))
        val destination=MemoryBackupDestination().apply {corruptWrites=true};var created=0
        val creator:suspend(Context,Uri,String,ByteArray?)->Result<BackupRestoreManager.BackupMetadata> = { _,uri,_,vault ->
            if(vault==null)Result.failure(IllegalStateException("Vault authorization required"))
            else {created++;File(uri.path!!).writeText("encrypted private archive");Result.success(BackupRestoreManager.BackupMetadata(2,System.currentTimeMillis(),"test",1,0,0,0,0,0))}
        }
        assertFalse(AutomaticBackups.run(app,true,store,{destination},creator));val before=store.staged()!!
        store.revokeVault();destination.corruptWrites=false
        assertFalse(AutomaticBackups.run(app,true,store,{destination},creator));assertEquals(1,created)
        assertTrue(destination.files.isEmpty());assertNull(store.staged());assertFalse(File(Uri.parse(before.uri).path!!).exists())
    }
}
