package com.focusbyrj.app.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.*
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
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Uncommitted editor data; saving a draft never writes a task or habit. */
internal class EditorDraftStore(
    private val context: Context,
    key: String,
    previousKeys: List<String> = emptyList(),
) {
    companion object {
        const val MAX_ENCRYPTED_BYTES = 650_000L
        const val MAX_CHARACTERS = 160_000
        private val lock = Any()
        private val editors = mutableMapOf<String, String>()

        private fun path(context: Context, key: String) =
            File(
                context.filesDir,
                "editor_drafts/" +
                    MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(
                        ""
                    ) {
                        "%02x".format(it)
                    } +
                    ".enc",
            )

        private fun exists(file: File) = file.exists() || File(file.path + ".bak").exists()
    }

    private val file = path(context, key)
    private val predecessors =
        previousKeys.map { path(context, it) }.distinct().filter { it != file }
    private val owner = java.util.UUID.randomUUID().toString()
    private var claimed = false
    private var closed = false
    private var predecessorRead: File? = null
    var restoreFailed: Boolean = false
        private set

    fun beginEditing(): EditorDraftStore = apply {
        synchronized(lock) {
            editors[file.path] = owner
            // Transfer ownership so a late disposal from an earlier identifier cannot write over
            // the handover.
            predecessors.filter(::exists).forEach { editors[it.path] = owner }
            claimed = true
        }
    }

    fun read(): JSONObject =
        synchronized(lock) {
            runCatching {
                    val source =
                        file.takeIf(::exists)
                            ?: predecessors.filter(::exists).maxByOrNull {
                                maxOf(it.lastModified(), File(it.path + ".bak").lastModified())
                            }
                            ?: return@runCatching JSONObject()
                    require(
                        maxOf(source.length(), File(source.path + ".bak").length()) <=
                            MAX_ENCRYPTED_BYTES
                    )
                    predecessorRead = source.takeIf { it != file }
                    JSONObject(EncryptedSnapshotFile.read(source))
                }
                .getOrElse {
                    restoreFailed = true
                    JSONObject()
                }
        }

    private fun retirePredecessors(candidates: List<File> = predecessors) {
        candidates
            .filter { editors[it.path] == owner }
            .forEach {
                androidx.core.util.AtomicFile(it).delete()
                editors.remove(it.path)
            }
    }

    fun save(value: JSONObject) =
        synchronized(lock) {
            if (closed || restoreFailed) return@synchronized
            if (!claimed) beginEditing()
            if (editors[file.path] != owner) return@synchronized
            val text = value.toString()
            require(text.length <= MAX_CHARACTERS) {
                "This draft is too large to autosave. Save your changes before leaving."
            }
            file.parentFile?.mkdirs()
            EncryptedSnapshotFile.writeEditorDraft(context, file, text)
            // The codec syncs and verifies the new ciphertext before an earlier copy is removed.
            retirePredecessors(listOfNotNull(predecessorRead))
            predecessorRead = null
        }

    /** Late disposal or a delayed autosave cannot resurrect a committed/discarded draft. */
    fun discard() =
        synchronized(lock) {
            closed = true
            if (!claimed || editors[file.path] == owner) {
                androidx.core.util.AtomicFile(file).delete()
                retirePredecessors()
            }
        }
}

@Composable
internal fun rememberEditorDraft(
    key: String,
    session: Int = 0,
    previousKeys: List<String> = emptyList(),
): EditorDraftStore {
    val context = LocalContext.current
    // A live row revision changing must not replace the original draft's ownership/baseline.
    return remember(key, session) {
        EditorDraftStore(context.applicationContext, key, previousKeys).beginEditing()
    }
}

@Composable
internal fun PersistEditorDraft(store: EditorDraftStore, value: JSONObject) {
    val context = LocalContext.current
    LaunchedEffect(store) {
        if (store.restoreFailed)
            android.widget.Toast.makeText(
                    context,
                    "An earlier draft couldn't be restored. It has been kept for recovery.",
                    android.widget.Toast.LENGTH_LONG,
                )
                .show()
    }
    val snapshot = value.toString()
    val latest by rememberUpdatedState(snapshot)
    val saveFailureShown = remember(store) { java.util.concurrent.atomic.AtomicBoolean(false) }
    fun saveDraft(content: String) {
        try {
            store.save(JSONObject(content))
            saveFailureShown.set(false)
        } catch (error: Exception) {
            // A recoverable autosave error must not terminate an editor or its host app.
            // The atomic store keeps the last saved draft; current text stays in memory.
            android.util.Log.e("EditorDraft", "Could not save encrypted editor draft", error)
            if (saveFailureShown.compareAndSet(false, true))
                android.widget.Toast.makeText(
                        context,
                        (if (error is IllegalArgumentException) error.message else null)
                            ?: "Couldn't save your draft. Keep this editor open and try again.",
                        android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(store, snapshot) {
        delay(200)
        saveDraft(snapshot)
    }
    DisposableEffect(store, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) saveDraft(latest)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            saveDraft(latest)
        }
    }
}

/** Shared by the system Back callback and the chat header's Back control. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberKeyboardFirstBackAction(onDismiss: () -> Unit): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val visible = WindowInsets.isImeVisible
    return {
        // Insets can change before a translucent editor recomposes after IME dismissal.
        val currentVisible =
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())
                ?: visible
        android.util.Log.d("EditorBack", "Keyboard visible: $currentVisible; composed: $visible")
        if (currentVisible) {
            keyboard?.hide()
            focus.clearFocus()
        } else onDismiss()
    }
}

/** Install inside the actual dialog/sheet window so its Back callback wins locally. */
@Composable
internal fun KeyboardFirstBackHandler(onDismiss: () -> Unit) {
    BackHandler(onBack = rememberKeyboardFirstBackAction(onDismiss))
}

@Composable
internal fun DraftRestoredBanner(enabled: Boolean = true, onDiscard: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            "Draft restored",
            modifier = androidx.compose.ui.Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        androidx.compose.material3.TextButton(enabled = enabled, onClick = onDiscard) {
            androidx.compose.material3.Text("Discard draft")
        }
    }
}
