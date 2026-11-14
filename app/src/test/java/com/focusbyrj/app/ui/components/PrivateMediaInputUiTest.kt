package com.focusbyrj.app.ui.components

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.note.*
import com.focusbyrj.app.ui.screens.notes.*
import com.focusbyrj.app.util.crypto.VaultPayloadEncryptor
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivateMediaInputUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun clearProviderRootsForTheFreshApplicationSandbox() {
        // Android keeps one cache directory per process; Robolectric gives each case
        // another application directory while AndroidX's static provider cache survives.
        org.robolectric.util.ReflectionHelpers.getStaticField<MutableMap<*,*>>(androidx.core.content.FileProvider::class.java,"sCache").clear()
    }
    @Test fun quickCameraReturnDoesNotAskForAnotherPin() = capture()
    @Test fun expiredCameraReturnAsksForAPinAndKeepsThePhoto() = capture(expired=true)
    @Test fun screenOffRequiresAPinEvenOnAQuickCameraReturn() = capture(screenOff=true)
    @Test fun cancellingTheCameraReturnsToTheSameNoteWithinTheGracePeriod() = capture(cancelled=true)
    @Test fun aQuickImagePickerReturnDoesNotAskForAnotherPin() = capture(kind="image")
    @Test fun aQuickOriginalFilePickerReturnKeepsAllBytesAndThePrivateText() = capture(kind="file")

    private fun capture(expired:Boolean=false,screenOff:Boolean=false,cancelled:Boolean=false,kind:String="camera") = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FocusApplication>()
        val dao = NoteDatabase.getInstance(app).noteDao()
        dao.deleteAllNotes()
        app.getSharedPreferences(ArchiveVaultSecurity.PREFS_NAME, 0).edit().clear().commit()
        ArchiveVaultSecurity.lockVault()
        val pin = "729461"
        assertTrue(ArchiveVaultSecurity.setPasscode(app, pin))
        val key = requireNotNull(ArchiveVaultSecurity.getActiveVaultSubKey())
        val id = try { dao.insertNote(VaultPayloadEncryptor.encryptNotePayload(NoteEntity(title = "Private title", content = "Preserve original text", isArchived = true), key)) }
            finally { key.fill(0) }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[NotesViewModel::class.java]
        assertSame(app,vm.getApplication<android.app.Application>())
        assertEquals(app.filesDir.canonicalPath,compose.activity.filesDir.canonicalPath)
        vm.openExistingNote(requireNotNull(dao.getNoteByIdSync(id)))
        val session = vm.editingState.value!!.sessionId
        compose.setContent { MaterialTheme {
            val request = rememberPrivateMediaInput(vm)
            Button(onClick = { request(kind, session) }) { Text("Choose private media") }
        } }
        compose.onNodeWithText("Choose private media").performClick()
        var started: ShadowActivity.IntentForResult? = null
        try {
            compose.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); started = started ?: shadowOf(compose.activity).nextStartedActivityForResult; started != null }
        } catch(error: Throwable) { throw AssertionError("Capture launch failed: ${org.robolectric.shadows.ShadowToast.getTextOfLatestToast()}; lifecycle=${compose.activity.lifecycle.currentState}; editor=${vm.editingState.value != null}",error) }
        assertTrue(ArchiveVaultSecurity.isVaultLocked(app)); assertNull(vm.editingState.value)
        if(expired) org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(2))
        if(screenOff) { app.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF)); shadowOf(android.os.Looper.getMainLooper()).idle() }
        val intent = requireNotNull(started).intent
        val photo = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        photo.eraseColor(android.graphics.Color.BLUE)
        val original=java.io.ByteArrayOutputStream().apply { photo.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
        val result=if(kind=="camera") {
            val output = requireNotNull(intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT))
            app.contentResolver.openOutputStream(output, "wt")!!.use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            null
        } else {
            val source=Uri.parse("content://synthetic.picker/media/${java.util.UUID.randomUUID()}")
            val file=java.io.File(app.cacheDir,"synthetic-picker-${java.util.UUID.randomUUID()}.png").apply { writeBytes(original) }
            val provider=object:android.content.ContentProvider() {
                override fun onCreate()=true
                override fun getType(uri:Uri)="image/png"
                override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor =
                    android.database.MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE)).apply { addRow(arrayOf("Original.png",file.length())) }
                override fun openFile(uri:Uri,mode:String)=android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                override fun insert(uri:Uri,values:android.content.ContentValues?):Uri?=throw UnsupportedOperationException()
                override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
                override fun update(uri:Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
            }
            provider.attachInfo(app,android.content.pm.ProviderInfo().apply { authority="synthetic.picker";applicationInfo=app.applicationInfo;grantUriPermissions=true })
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("synthetic.picker",provider)
            android.content.Intent().setData(source)
        }
        photo.recycle()
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(intent, if(cancelled) Activity.RESULT_CANCELED else Activity.RESULT_OK, result) }
        try {
        if(expired || screenOff) {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Finish adding media").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app)); assertNull(vm.editingState.value)
            pin.forEach { digit -> compose.onNodeWithText(digit.toString()).performScrollTo().performClick() }
        }
        compose.waitUntil(10_000) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val state=vm.editingState.value
            state != null && if(kind=="file") NoteMediaInventory.paths(NoteEntity(content=state.content)).isNotEmpty()
                else state.imageUris.size == if(cancelled) 0 else 1
        }
        } catch(error: Throwable) {
            val dialogs=listOf("Preparing media","Finish adding media","Media selection").associateWith {
                runCatching { compose.onAllNodesWithText(it).fetchSemanticsNodes().size }.getOrDefault(-1)
            }
            val state=vm.editingState.value
            throw AssertionError("Media return failed: ${org.robolectric.shadows.ShadowToast.getTextOfLatestToast()}; editor=${state?.originalId}; images=${state?.imageUris?.size}; status=${ArchiveVaultSecurity.getVaultStatus(app)}; locked=${ArchiveVaultSecurity.isVaultLocked(app)}; dialogs=$dialogs",error)
        }
        compose.onNodeWithText("Finish adding media").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(id, vm.editingState.value!!.originalId)
            assertTrue(vm.editingState.value!!.isArchived)
            if(kind=="file") {
                assertTrue(vm.editingState.value!!.content.contains("Preserve original text"))
                val path=NoteMediaInventory.paths(NoteEntity(content=vm.editingState.value!!.content)).single()
                assertArrayEquals(original,com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(java.io.File(path)))
            } else assertEquals("Preserve original text", vm.editingState.value!!.content)
            vm.lockVault()
            assertTrue(ArchiveVaultSecurity.isVaultLocked(app))
        }
    }
}
