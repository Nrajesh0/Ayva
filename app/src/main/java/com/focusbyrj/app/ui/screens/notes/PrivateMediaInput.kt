package com.focusbyrj.app.ui.screens.notes

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusbyrj.app.data.note.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Lives in NotesScreen, not in the editor that disappears when the vault locks. */
@Composable
internal fun rememberPrivateMediaInput(viewModel: NotesViewModel): (String, String) -> Unit = key("notes-private-media-v1") {
    privateMediaInputState(viewModel)
}

@Composable
private fun privateMediaInputState(viewModel: NotesViewModel): (String, String) -> Unit {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf("") }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sources by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var images by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var launchPending by rememberSaveable { mutableStateOf(false) }
    var unlock by rememberSaveable { mutableStateOf(false) }
    var pinRequired by rememberSaveable { mutableStateOf(false) }
    var cancelledPick by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var alive by remember { mutableStateOf(true) }
    val messageHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    fun showMessage(message: String, duration: Int) {
        val app = context.applicationContext
        messageHandler.post { if (alive) Toast.makeText(app, message, duration).show() }
    }
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && target != null) viewModel.lockVaultForBackground()
        }
        lifecycle.addObserver(observer)
        onDispose { alive = false; lifecycle.removeObserver(observer); if (target != null) viewModel.lockVault() }
    }

    fun clear(retire: Boolean = true) {
        val nonce = target?.let { runCatching { org.json.JSONObject(it).getString("nonce") }.getOrNull() }
        if (nonce != null) ArchiveVaultSecurity.cancelMediaReturn(nonce)
        if (retire) runCatching { MediaRetirement.retire(images.toSet()) }
        cameraUri?.let { value ->
            val uri = Uri.parse(value)
            val name = uri.lastPathSegment.orEmpty()
            if (name.matches(Regex("[a-f0-9-]{36}\\.jpg"))) File(File(context.cacheDir, "note_capture"), name).delete()
        }
        target = null; sources = arrayListOf(); images = arrayListOf(); cameraUri = null; unlock = false; launchPending = false; pinRequired = false; cancelledPick = false
    }
    fun received(uris: List<Uri>) {
        if (target == null) { clear(); return }
        if (uris.isEmpty()) { cancelledPick = true; unlock = true; return }
        if (uris.size > 50 || uris.any { it.scheme !in setOf("content", "android.resource") || it.toString().length > 8192 }) {
            showMessage("Choose up to 50 files from a supported picker.", Toast.LENGTH_LONG); clear(); return
        }
        sources = ArrayList(uris.map { it.toString() }.distinct())
    }
    // A distinct registry key per operation prevents an old external result from
    // being delivered into a newer note/media request after cancellation/expiry.
    val registrationToken = target
    val gallery = key(registrationToken, "image") {
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
            if (target == registrationToken && alive) received(it)
        }
    }
    val files = key(registrationToken, "file") {
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            if (target == registrationToken && alive) received(it)
        }
    }
    val camera = key(registrationToken, "camera") {
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (target == registrationToken && alive) {
                val uri = cameraUri?.let(Uri::parse)
                if (uri != null) runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                received(if (success && uri != null) listOf(uri) else emptyList())
            }
        }
    }
    LaunchedEffect(target, launchPending, lifecycleState) {
        if (!launchPending || target == null || !alive || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        launchPending = false
        try {
            when (kind) {
                "image" -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                "file" -> files.launch("*/*")
                else -> {
                    val file = File(File(context.cacheDir, "note_capture").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraUri = uri.toString(); CaptureCleanupWorker.enqueue(context)
                    camera.launch(uri)
                }
            }
        } catch (error: Exception) {
            showMessage(if (error is android.content.ActivityNotFoundException) "No suitable app is available. Try another picker." else "The picker could not open. Try again.", Toast.LENGTH_LONG)
            clear()
        }
    }
    LaunchedEffect(target, sources.toList()) {
        val token = target ?: return@LaunchedEffect
        if (unlock) return@LaunchedEffect
        if (images.isNotEmpty()) { unlock = true; return@LaunchedEffect }
        if (sources.isEmpty()) return@LaunchedEffect
        if (kind == "file") { unlock = true; return@LaunchedEffect }
        busy = true
        val created = mutableListOf<String>()
        var retained = false
        try {
            withContext(Dispatchers.IO) {
                sources.forEach { value -> created += checkNotNull(NoteImageHelper.processAndSaveImage(context, Uri.parse(value))) {
                    "Image could not be imported. Use Original file to keep its original format."
                } }
            }
            ensureActive()
            if (target != token) return@LaunchedEffect
            images = ArrayList(created); retained = true; unlock = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showMessage(error.message ?: "Image could not be imported.", Toast.LENGTH_LONG); clear() }
        finally {
            if (!retained) runCatching { MediaRetirement.retire(created.toSet()) }
            busy = false
        }
    }
    LaunchedEffect(target) {
        val token = target ?: return@LaunchedEffect
        val started = runCatching { org.json.JSONObject(token).getLong("created") }.getOrNull()
        if (started == null) { clear(); return@LaunchedEffect }
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        delay(if (elapsed < 0) 0 else (86_400_000 - elapsed).coerceAtLeast(0))
        if (target == token) { job?.cancel(); clear(); viewModel.lockVault(); showMessage("Media selection expired. Choose it again.", Toast.LENGTH_LONG) }
    }
    if (target != null && sources.isEmpty() && images.isEmpty() && !busy && !unlock && !launchPending) {
        AlertDialog(onDismissRequest = { clear() }, title = { Text("Media selection") },
            text = { Text("Finish choosing media in the other app, or cancel this selection.") },
            confirmButton = { TextButton(onClick = { clear() }) { Text("Cancel selection") } })
    }
    if (busy) AlertDialog(onDismissRequest = {}, title = { Text("Preparing media") },
        text = { LinearProgressIndicator() }, confirmButton = {})
    fun finish() {
        unlock = false; pinRequired = false; busy = true
        val token = target
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                checkNotNull(token)
                check(alive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { "Return to the app to finish." }
                val opened = viewModel.resumePrivateMediaInput(token, images)
                if (opened && images.isNotEmpty()) showMessage("Images added above your private note.", Toast.LENGTH_SHORT)
                if (opened && kind == "file" && !cancelledPick) sources.forEach { viewModel.addAttachmentToEditor(Uri.parse(it)) }
                if (!opened) viewModel.lockVault()
                clear(retire = false)
            } catch (cancelled: CancellationException) { viewModel.lockVault(); throw cancelled }
            catch (error: Exception) {
                if (alive && target == token && ArchiveVaultSecurity.isVaultLocked(context)) { unlock = true; pinRequired = true }
                else { showMessage(error.message ?: "Media was not added. Your saved data was kept.", Toast.LENGTH_LONG); clear(); viewModel.lockVault() }
            }
            finally { busy = false }
        }
    }
    LaunchedEffect(unlock, pinRequired, lifecycleState, target, busy) {
        withContext(Dispatchers.Main.immediate) {
            val token = target ?: return@withContext
            if (!unlock || pinRequired || busy || !alive || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return@withContext
            if (viewModel.unlockPrivateMediaInputIfRecent(token)) finish() else pinRequired = true
        }
    }
    if (unlock && pinRequired && target != null) ArchiveVaultUnlockDialog(
        title = "Finish adding media",
        description = if (cancelledPick) "Unlock to return to your private note."
            else "The 2-minute picker session ended or was locked. Unlock to finish adding media.",
        showCancelButton = true,
        initialLockoutSeconds = viewModel.getRemainingLockoutSeconds(),
        onDismiss = { clear(); viewModel.lockVault() },
        onVerify = { pin ->
            val token = target
            if (token == null || !alive) ArchiveVaultSecurity.VerifyResult.Error("Selection expired. Choose it again.")
            else {
                val result = viewModel.verifyVaultPasscode(pin)
                if (!alive || target != token || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    viewModel.lockVault(); ArchiveVaultSecurity.VerifyResult.Error("Return to the app and try again.")
                } else result
            }
        },
        onSuccess = { finish() }
    )
    return { requested, expectedSession ->
        if (target != null || busy) showMessage("Finish the current media selection first.", Toast.LENGTH_SHORT)
        else {
            busy = true
            job = scope.launch(Dispatchers.Main.immediate) {
                try {
                    check(requested in setOf("image", "file", "camera"))
                    kind = requested; target = viewModel.preparePrivateMediaInput(expectedSession)
                    busy = false
                    launchPending = true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { showMessage(error.message ?: "The picker could not open.", Toast.LENGTH_LONG); clear() }
                finally { busy = false }
            }
        }
    }
}
