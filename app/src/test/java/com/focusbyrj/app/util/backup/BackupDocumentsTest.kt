package com.focusbyrj.app.util.backup

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as D
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** A real DocumentsContract/ContentResolver path over test files; no broad storage access. */
class TestBackupDocumentsProvider : DocumentsProvider() {
    private lateinit var root: File
    private val entries=linkedMapOf<String,Pair<String,File>>()
    var denied=false
    override fun onCreate():Boolean {root=File(context!!.cacheDir,"document-provider").apply{mkdirs()};return true}
    override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(arrayOf(D.Root.COLUMN_ROOT_ID,D.Root.COLUMN_DOCUMENT_ID,D.Root.COLUMN_TITLE,D.Root.COLUMN_FLAGS)).apply {addRow(arrayOf("root","root","Test backups",D.Root.FLAG_SUPPORTS_CREATE))}
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor {
        check(!denied)
        val columns=projection?:arrayOf(D.Document.COLUMN_DOCUMENT_ID,D.Document.COLUMN_DISPLAY_NAME,D.Document.COLUMN_MIME_TYPE,D.Document.COLUMN_FLAGS,D.Document.COLUMN_SIZE)
        return MatrixCursor(columns).apply {
            if(documentId!="root" && documentId !in entries)return@apply
            val entry=entries[documentId]
            addRow(columns.map { column -> when(column) {
                D.Document.COLUMN_DOCUMENT_ID->documentId
                D.Document.COLUMN_DISPLAY_NAME->entry?.first?:"Test backups"
                D.Document.COLUMN_MIME_TYPE->if(documentId=="root")D.Document.MIME_TYPE_DIR else "application/octet-stream"
                D.Document.COLUMN_FLAGS->if(documentId=="root")D.Document.FLAG_DIR_SUPPORTS_CREATE else D.Document.FLAG_SUPPORTS_WRITE or D.Document.FLAG_SUPPORTS_DELETE or D.Document.FLAG_SUPPORTS_RENAME
                D.Document.COLUMN_SIZE->entry?.second?.length()?:0L
                else->null
            } }.toTypedArray())
        }
    }
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor=MatrixCursor(arrayOf(D.Document.COLUMN_DOCUMENT_ID))
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
        check(!denied);val file=entries.getValue(documentId).second
        return ParcelFileDescriptor.open(file,if(mode.contains('w'))ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
        check(!denied && parentDocumentId=="root");val id=UUID.randomUUID().toString();entries[id]=displayName to File(root,id).apply{createNewFile()};return id
    }
    override fun deleteDocument(documentId:String) {check(!denied);entries.remove(documentId)?.second?.delete()}
    override fun renameDocument(documentId:String,displayName:String):String {val old=entries.getValue(documentId);entries[documentId]=displayName to old.second;return documentId}
    override fun isChildDocument(parentDocumentId:String,documentId:String)=parentDocumentId=="root" && (documentId=="root" || documentId in entries)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class,shadows=[BackupDocumentsTest.PdfContainerShadow::class])
class BackupDocumentsTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var provider:TestBackupDocumentsProvider
    private val tree=Uri.parse("content://run.tests.documents/tree/root")
    @Before fun setup() {
        provider=TestBackupDocumentsProvider()
        provider.attachInfo(app,ProviderInfo().apply {authority="run.tests.documents";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission=readPermission})
        ShadowContentResolver.registerProviderInternal("run.tests.documents",provider)
    }
    @Test fun actualSafOperationsCreateReadBackRenameAndDeleteOnlyInsideChosenTree() {
        val destination=SafBackupDestination(app,tree.toString());destination.probe()
        val bytes="encrypted archive fixture".toByteArray()
        val file=AutomaticBackupFiles.publish(destination,"safe.focusbackup",bytes.inputStream().use{digestBackup(it)}){bytes.inputStream()}
        assertArrayEquals(bytes,destination.read(file.uri).readBytes())
        assertTrue(destination.delete(file.uri))
        assertThrows(IllegalArgumentException::class.java) {destination.delete("content://run.tests.documents/tree/other/document/old")}
        assertThrows(IllegalArgumentException::class.java) {SafBackupDestination(app,"file:///sdcard")}
    }
    @Test fun recoveryPdfAndKeyFileRoundTripWithoutAppSecretsAndDetectModifiedOrOversizedDocuments() {
        val destination=SafBackupDestination(app,tree.toString())
        BackupRecoveryKey.generate().use { original ->
            for(pdf in listOf(false,true)) {
                val file=destination.create(if(pdf)"recovery.pdf" else "recovery.runkey")
                BackupRecoveryDocuments.export(app,Uri.parse(file.uri),original,pdf)
                BackupRecoveryDocuments.read(app,Uri.parse(file.uri)).use {assertEquals(original.credential(),it.credential())}
                if(pdf) {
                    val exported=destination.read(file.uri).readBytes()
                    File("build/reports/automatic-backups/container-marker-test.pdf").apply{parentFile!!.mkdirs();writeBytes(exported)}
                    val modified=exported.toString(Charsets.ISO_8859_1).replace("%RUN-RECOVERY-KEY-V1","%REMOVED-KEY-MARKER").toByteArray(Charsets.ISO_8859_1)
                    destination.write(file.uri).use{it.write(modified)}
                    assertThrows(IllegalArgumentException::class.java) {BackupRecoveryDocuments.read(app,Uri.parse(file.uri))}
                }
            }
            val large=destination.create("large.runkey");destination.write(large.uri).use{it.write(ByteArray(2*1024*1024+1))}
            assertThrows(IllegalArgumentException::class.java) {BackupRecoveryDocuments.read(app,Uri.parse(large.uri))}
        }
    }
    @Test fun revokedFolderAccessFailsWithoutClaimingAKeyExportSucceeded() {
        val d=SafBackupDestination(app,tree.toString());val file=d.create("key.runkey");provider.denied=true
        BackupRecoveryKey.generate().use { key -> assertThrows(Exception::class.java) {BackupRecoveryDocuments.export(app,Uri.parse(file.uri),key,false)} }
    }
    @Test fun aNullProviderQueryIsNotMistakenForADeletedArchive() {
        val unavailable=object:android.content.ContentProvider() {
            override fun onCreate()=true
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
            override fun getType(uri:Uri):String?=null
            override fun insert(uri:Uri,values:android.content.ContentValues?):Uri?=null
            override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
            override fun update(uri:Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        }
        unavailable.attachInfo(app,ProviderInfo().apply{authority="run.tests.documents";exported=true;grantUriPermissions=true})
        ShadowContentResolver.registerProviderInternal("run.tests.documents",unavailable)
        val d=SafBackupDestination(app,tree.toString())
        assertThrows(IllegalStateException::class.java){d.info(D.buildDocumentUriUsingTree(tree,"root").toString())}
    }
    @Test fun recoveryKeyExportCannotBypassALockedPrivateVault() = kotlinx.coroutines.runBlocking {
        val store=AutomaticBackupStore(app,TestBackupWrapper())
        BackupRecoveryKey.generate().use{store.install(it)};store.authorizeVault(ByteArray(32){it.toByte()})
        app.getSharedPreferences(com.focusbyrj.app.data.note.ArchiveVaultSecurity.PREFS_NAME,0).edit().putString(com.focusbyrj.app.data.note.ArchiveVaultSecurity.KEY_STATUS,"enabled").commit()
        com.focusbyrj.app.data.note.ArchiveVaultSecurity.lockVault()
        val d=SafBackupDestination(app,tree.toString());val file=d.create("must-not-export.runkey")
        try {
            try {AutomaticBackups.exportKey(app,Uri.parse(file.uri),false,store.config().keyId,store);fail("Locked private vault must block recovery-key export")}
            catch(error:SecurityException){assertTrue(error.message!!.contains("private vault"))}
            assertEquals(0,d.read(file.uri).readBytes().size);assertFalse(store.config().keyExported)
        } finally {app.getSharedPreferences(com.focusbyrj.app.data.note.ArchiveVaultSecurity.PREFS_NAME,0).edit().clear().commit()}
    }
    @Test @Config(application=com.focusbyrj.app.FocusApplication::class)
    fun anAuthenticatedVaultMayExportAndReadBackItsRecoveryDocument() = kotlinx.coroutines.runBlocking {
        val vault=com.focusbyrj.app.data.note.ArchiveVaultSecurity
        val notes=com.focusbyrj.app.data.note.NoteDatabase.getInstance(app).noteDao()
        notes.deleteAllNotes();vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit()
        try {
            assertTrue(vault.setPasscode(app,"123456"))
            val store=AutomaticBackupStore(app,TestBackupWrapper())
            BackupRecoveryKey.generate().use {key ->
                store.install(key)
                val d=SafBackupDestination(app,tree.toString());val file=d.create("authenticated.runkey")
                AutomaticBackups.exportKey(app,Uri.parse(file.uri),false,key.id,store)
                BackupRecoveryDocuments.read(app,Uri.parse(file.uri)).use{assertEquals(key.credential(),it.credential())}
                assertTrue(store.config().keyExported)
                val active=vault.getActiveVaultSubKey();assertNotNull(active);active!!.fill(0)
            }
        } finally {vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit();notes.deleteAllNotes()}
    }
    @Test fun disablingTheVaultCannotExposeAPreviouslyPrivateRecoveryKey() = kotlinx.coroutines.runBlocking {
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        store.authorizeVault(ByteArray(32){it.toByte()})
        val vault=com.focusbyrj.app.data.note.ArchiveVaultSecurity
        app.getSharedPreferences(vault.PREFS_NAME,0).edit().putString(vault.KEY_STATUS,"disabled").commit();vault.lockVault()
        val d=SafBackupDestination(app,tree.toString());val file=d.create("private-after-reset.runkey")
        try {
            try{AutomaticBackups.exportKey(app,Uri.parse(file.uri),false,store.config().keyId,store);fail("Vault reset must not remove recovery-key protection")}
            catch(_:SecurityException){}
            assertEquals(0,d.read(file.uri).readBytes().size)
        } finally {app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit()}
    }
    @Test @Config(application=com.focusbyrj.app.FocusApplication::class)
    fun aDifferentVaultCannotTakeOwnershipOfAnOlderPrivateBackupKey() = kotlinx.coroutines.runBlocking {
        val vault=com.focusbyrj.app.data.note.ArchiveVaultSecurity
        val notes=com.focusbyrj.app.data.note.NoteDatabase.getInstance(app).noteDao();notes.deleteAllNotes()
        vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit()
        try {
            assertTrue(vault.setPasscode(app,"123456"))
            val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
            val first=vault.getActiveVaultSubKey()!!;try{store.authorizeVault(first)}finally{first.fill(0)}
            assertTrue(vault.disablePasscode(app));assertTrue(vault.setPasscode(app,"654321"))
            val d=SafBackupDestination(app,tree.toString());val file=d.create("old-private-key.runkey")
            try{AutomaticBackups.exportKey(app,Uri.parse(file.uri),false,store.config().keyId,store);fail("New vault PIN must not expose the old vault's backup key")}
            catch(_:SecurityException){}
            assertEquals(0,d.read(file.uri).readBytes().size)
        } finally {vault.lockVault();app.getSharedPreferences(vault.PREFS_NAME,0).edit().clear().commit();notes.deleteAllNotes()}
    }
    private fun manager():androidx.work.WorkManager {
        return runCatching{androidx.work.WorkManager.getInstance(app)}.getOrElse{
            androidx.work.WorkManager.initialize(app,androidx.work.Configuration.Builder().build());androidx.work.WorkManager.getInstance(app)
        }
    }
    private fun readyScheduledStore(charging:Boolean=false):AutomaticBackupStore {
        val store=AutomaticBackupStore(app,TestBackupWrapper());BackupRecoveryKey.generate().use{store.install(it)}
        store.save(store.config().copy(tree=tree.toString(),keyExported=true,keyAcknowledged=true,chargingOnly=charging))
        return store
    }
    @Test fun initialAutomaticBackupHonorsChargingInsteadOfCreatingAnUnconstrainedForcedJob() = kotlinx.coroutines.runBlocking {
        val manager=manager();val store=readyScheduledStore(charging=true)
        try {
            AutomaticBackups.enable(app,true,store)
            val scheduled=manager.getWorkInfosForUniqueWork("run_automatic_full_backup_check_v1").get(10,java.util.concurrent.TimeUnit.SECONDS).single{!it.state.isFinished}
            assertTrue(scheduled.constraints.requiresCharging());assertTrue(scheduled.constraints.requiresBatteryNotLow());assertTrue(scheduled.constraints.requiresStorageNotLow())
            val immediate=manager.getWorkInfosForUniqueWork("run_automatic_full_backup_now_v1").get(10,java.util.concurrent.TimeUnit.SECONDS)
            assertFalse("Automatic setup bypassed charging through a forced job",immediate.any{!it.state.isFinished})
        } finally {store.save(store.config().copy(enabled=false));manager.cancelAllWork().result.get(10,java.util.concurrent.TimeUnit.SECONDS)}
    }
    @Test fun pausingBackupCancelsAnAlreadyQueuedExplicitRequest() = kotlinx.coroutines.runBlocking {
        val manager=manager();val store=readyScheduledStore()
        try {
            store.save(store.config().copy(enabled=true));AutomaticBackups.requestNow(app)
            assertTrue(manager.getWorkInfosForUniqueWork("run_automatic_full_backup_now_v1").get(10,java.util.concurrent.TimeUnit.SECONDS).any{!it.state.isFinished})
            AutomaticBackups.enable(app,false,store)
            assertFalse("A forced retry survived pause",manager.getWorkInfosForUniqueWork("run_automatic_full_backup_now_v1").get(10,java.util.concurrent.TimeUnit.SECONDS).any{!it.state.isFinished})
        } finally {store.save(store.config().copy(enabled=false));manager.cancelAllWork().result.get(10,java.util.concurrent.TimeUnit.SECONDS)}
    }
    @Test fun explicitRequestsCarryCurrentSetupIdentityAndRepairDamagedRequestMetadata() = kotlinx.coroutines.runBlocking {
        val manager=manager();val store=readyScheduledStore()
        val prefs=app.getSharedPreferences("automatic_full_backups_v1",0);prefs.edit().putInt("request_epoch",12).commit()
        try {
            AutomaticBackups.requestNow(app)
            val work=manager.getWorkInfosForUniqueWork("run_automatic_full_backup_now_v1").get(10,java.util.concurrent.TimeUnit.SECONDS).single{!it.state.isFinished}
            val data=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
                androidx.work.impl.WorkManagerImpl.getInstance(app).workDatabase.workSpecDao().getWorkSpec(work.id.toString())!!.input
            }
            assertEquals(store.config().stream,data.getString("setup_stream"));assertEquals(store.config().keyId,data.getString("setup_key"))
            assertEquals(store.requestEpoch(),data.getString("request_epoch"));assertNotEquals("invalid",store.requestEpoch())
            assertTrue(data.getBoolean("force",false));assertNotNull(data.getString("request_id"))
            assertFalse(work.constraints.requiresCharging());assertTrue(work.constraints.requiresStorageNotLow())
        } finally {manager.cancelAllWork().result.get(10,java.util.concurrent.TimeUnit.SECONDS)}
    }
    /** Android PDF drawing is mocked, while a valid independent PDF container exercises marker/xref preservation. */
    @Implements(PdfDocument::class)
    class PdfContainerShadow {
        @Implementation fun __constructor__()=Unit
        @Implementation fun startPage(info:PdfDocument.PageInfo):PdfDocument.Page=PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java,PdfDocument.PageInfo::class.java).apply{isAccessible=true}.newInstance(Canvas(),info)
        @Implementation fun finishPage(page:PdfDocument.Page)=Unit
        @Implementation fun close()=Unit
        @Implementation fun writeTo(out:OutputStream) {javaClass.classLoader!!.getResourceAsStream("automatic-backups/container.pdf")!!.use{it.copyTo(out)}}
    }
}
