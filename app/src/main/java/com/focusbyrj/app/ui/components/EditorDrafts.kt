package com.focusbyrj.app.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusbyrj.app.util.backup.EncryptedSnapshotFile
import kotlinx.coroutines.delay
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Uncommitted editor data; saving a draft never writes a task or habit. */
internal class EditorDraftStore(context: Context, key: String) {
    companion object {
        private val lock = Any()
        private val editors = mutableMapOf<String, String>()
    }
    private val file = File(context.filesDir, "editor_drafts/" + MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".enc")
    private val owner = java.util.UUID.randomUUID().toString()
    private var claimed = false
    private var closed = false
    var restoreFailed: Boolean = false
        private set

    fun beginEditing(): EditorDraftStore = apply { synchronized(lock) { editors[file.path] = owner; claimed = true } }

    @Synchronized fun read(): JSONObject = runCatching {
        if (!file.exists()) JSONObject()
        else {
            require(file.length() <= 260_000) // room for UTF-8 text plus the encrypted envelope
            JSONObject(EncryptedSnapshotFile.read(file))
        }
    }.getOrElse { restoreFailed = true; JSONObject() }

    fun save(value: JSONObject) = synchronized(lock) {
        if (closed || restoreFailed) return@synchronized
        if (!claimed) beginEditing()
        if (editors[file.path] != owner) return@synchronized
        val text = value.toString()
        if (text.length > 60_000) return@synchronized
        file.parentFile?.mkdirs()
        EncryptedSnapshotFile.write(file, text)
    }

    /** Late disposal or a delayed autosave cannot resurrect a committed/discarded draft. */
    fun discard() = synchronized(lock) {
        closed = true
        if (!claimed || editors[file.path] == owner) androidx.core.util.AtomicFile(file).delete()
    }
}

@Composable
internal fun rememberEditorDraft(key: String): EditorDraftStore {
    val context = LocalContext.current
    return remember(key) { EditorDraftStore(context.applicationContext, key).beginEditing() }
}

@Composable
internal fun PersistEditorDraft(store: EditorDraftStore, value: JSONObject) {
    val context = LocalContext.current
    LaunchedEffect(store) {
        if (store.restoreFailed) android.widget.Toast.makeText(context,
            "An earlier draft couldn't be restored. It has been kept for recovery.", android.widget.Toast.LENGTH_LONG).show()
    }
    val snapshot = value.toString()
    val latest by rememberUpdatedState(snapshot)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(store, snapshot) { delay(200); store.save(JSONObject(snapshot)) }
    DisposableEffect(store, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) store.save(JSONObject(latest))
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); store.save(JSONObject(latest)) }
    }
}

/** Install inside the actual dialog/sheet window so its Back callback wins locally. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyboardFirstBackHandler(onDismiss: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val visible = WindowInsets.isImeVisible
    BackHandler {
        // Insets can change before a translucent editor recomposes after IME dismissal.
        val currentVisible = ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.ime()) ?: visible
        android.util.Log.d("EditorBack", "Keyboard visible: $currentVisible; composed: $visible")
        if (currentVisible) { keyboard?.hide(); focus.clearFocus() }
        else onDismiss()
    }
}
