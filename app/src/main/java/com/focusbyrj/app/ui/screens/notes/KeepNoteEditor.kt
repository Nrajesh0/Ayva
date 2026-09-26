/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.ui.screens.notes
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable

fun KeepNoteEditor(
    state: NotesViewModel.EditingNoteState,
    allLabels: List<String>,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onColorChange: (String) -> Unit,
    onToggleChecklistMode: () -> Unit,
    onToggleChecklistItem: (Int) -> Unit,
    onUpdateChecklistItemText: (Int, String) -> Unit,
    onAddChecklistItem: (Int?, String, String?) -> Unit = { _, _, _ -> },
    onRemoveChecklistItem: (Int) -> Unit,
    onMoveChecklistItem: (Int, Int) -> Unit,
    onAddImageUri: (Uri) -> Unit,
    onAddAttachmentUri: (Uri) -> Unit = {},
    onAddDrawing: (Bitmap) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onToggleLabel: (String) -> Unit,
    onCreateAndAddLabel: (String) -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit = {},
    onDelete: () -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onShare: () -> Unit = {},
    onCopyText: () -> Unit = {},
    onVoiceInput: (String) -> Unit = {},
    onPhotoTaken: (Bitmap) -> Unit = {},
    onStartVoiceRecording: () -> Unit = {},
    activePlayingAudioPath: String? = null,
    isAudioPlaying: Boolean = false,
    audioPositionMs: Int = 0,
    audioDurationMs: Int = 0,
    audioPlaybackSpeed: Float = 1.0f,
    onToggleAudioPlay: (String) -> Unit = {},
    onSeekAudio: (Int) -> Unit = {},
    onSetAudioPlaybackSpeed: (Float) -> Unit = {},
    onSkipAudio: (Int) -> Unit = {},
    onRemoveAudio: (String) -> Unit = {},
    onFontChange: (String) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val theme = KeepColorPalette.getColor(state.colorKey)
    val fontStyle = KeepFontPalette.getFont(state.fontKey)
    val bgColor = theme.resolveBackgroundColor(isDark)
    val textColor = theme.resolveTextColor(isDark)
    val borderColor = theme.resolveBorderColor(isDark)

    var showColorPicker by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(true) }
    var showLabelDialog by remember { mutableStateOf(false) }
    var showSketchDialog by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var viewingImageUri by remember { mutableStateOf<String?>(null) }
    var completedExpanded by remember { mutableStateOf(true) }
    var targetFocusItemId by remember { mutableStateOf<String?>(null) }

    var showNotesnookInsertSheet by remember { mutableStateOf(false) }
    var showCodeBlockDialog by remember { mutableStateOf(false) }
    var showMathDialog by remember { mutableStateOf(false) }
    var showCalloutDialog by remember { mutableStateOf(false) }
    var showEmbedDialog by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var showImageOptionsSheet by remember { mutableStateOf(false) }
    var showAttachmentOptionsSheet by remember { mutableStateOf(false) }

    var isZenMode by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showEditorialSheet by remember { mutableStateOf(false) }
    var lineSpacingPreset by remember { mutableStateOf(LineSpacingPreset.COMFORTABLE) }

    val initialParsed = remember(state.sessionId) { RichTextEngine.parse(state.content) }
    var richSpans by remember(state.sessionId) { mutableStateOf(initialParsed.second) }
    var pendingTypingStyles by remember { mutableStateOf(setOf<RichSpanType>()) }
    var selectedFontColorHex by remember { mutableStateOf<String?>(null) }
    var textAlignment by remember { mutableStateOf(TextAlign.Start) }

    val contentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var showMcqFormatPill by remember { mutableStateOf(false) }
    var detectedMcqRaw by remember { mutableStateOf<String?>(null) }
    var mcqPillDismissJob by remember { mutableStateOf<Job?>(null) }

    fun safeRequestFocus() {
        coroutineScope.launch {
            kotlinx.coroutines.delay(120)
            try {
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    var contentTfv by remember(state.sessionId) {
        mutableStateOf(
            TextFieldValue(
                text = initialParsed.first,
                selection = TextRange(initialParsed.first.length)
            )
        )
    }

    var blocks by remember(state.sessionId) {
        mutableStateOf(NotesnookBlockManager.parse(state.content))
    }

    var activeBlockIndex by remember(state.sessionId) { mutableIntStateOf(0) }
    val textBlockStates = remember(state.sessionId) { mutableStateMapOf<String, TextFieldValue>() }

    fun syncAndCommitBlocks(newBlocks: List<NotesnookBlock>) {
        blocks = newBlocks
        val serialized = NotesnookBlockManager.serialize(newBlocks)
        onContentChange(serialized)
    }

    fun getActiveTextState(): Triple<Int, TextFieldValue, List<RichSpan>>? {
        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            return Triple(0, contentTfv, richSpans)
        }
        val idx = activeBlockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
        val block = blocks.getOrNull(idx)
        if (block is NotesnookBlock.Text) {
            val tfv = textBlockStates[block.id] ?: TextFieldValue(block.text, TextRange(block.text.length))
            return Triple(idx, tfv, block.spans)
        }
        val firstTextIdx = blocks.indexOfFirst { it is NotesnookBlock.Text }
        if (firstTextIdx != -1) {
            val b = blocks[firstTextIdx] as NotesnookBlock.Text
            val tfv = textBlockStates[b.id] ?: TextFieldValue(b.text, TextRange(b.text.length))
            return Triple(firstTextIdx, tfv, b.spans)
        }
        return null
    }

    fun updateActiveTextState(
        blockIdx: Int,
        newTfv: TextFieldValue,
        newSpans: List<RichSpan>
    ) {
        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            contentTfv = newTfv
            richSpans = newSpans
            val id = blocks.firstOrNull()?.id ?: java.util.UUID.randomUUID().toString()
            val updatedBlock = NotesnookBlock.Text(id = id, text = newTfv.text, spans = newSpans)
            blocks = listOf(updatedBlock)
            onContentChange(NotesnookBlockManager.serialize(blocks))
        } else {
            val block = blocks.getOrNull(blockIdx)
            if (block is NotesnookBlock.Text) {
                textBlockStates[block.id] = newTfv
                val updatedBlock = block.copy(text = newTfv.text, spans = newSpans)
                val newBlocks = blocks.toMutableList()
                newBlocks[blockIdx] = updatedBlock
                syncAndCommitBlocks(newBlocks)
            }
        }
    }

    val currentActiveText = remember(blocks, activeBlockIndex, contentTfv, richSpans, textBlockStates) {
        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
            Triple(contentTfv.text, contentTfv.selection, richSpans)
        } else {
            val idx = activeBlockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
            val block = blocks.getOrNull(idx)
            if (block is NotesnookBlock.Text) {
                val tfv = textBlockStates[block.id] ?: TextFieldValue(block.text, TextRange(block.text.length))
                Triple(tfv.text, tfv.selection, block.spans)
            } else {
                Triple(contentTfv.text, contentTfv.selection, richSpans)
            }
        }
    }

    val activeStyles = remember(currentActiveText, pendingTypingStyles) {
        RichTextEngine.getActiveStyles(
            currentActiveText.third,
            currentActiveText.second,
            currentActiveText.first,
            pendingTypingStyles
        )
    }

    var fontSizeSp by remember { mutableFloatStateOf(16f) }
    var lineHeightSp by remember { mutableFloatStateOf(24f) }

    val documentMetrics = remember(state.title, state.content, blocks, state.checklistItems, state.isChecklist, contentTfv.text) {
        DocumentMetricsCalculator.calculate(
            title = state.title,
            content = if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) contentTfv.text else state.content,
            blocks = blocks,
            checklistItems = state.checklistItems,
            isChecklist = state.isChecklist
        )
    }
    var showDocumentStatsSheet by remember { mutableStateOf(false) }

    fun insertBlockItem(blockToInsert: NotesnookBlock) {
        val newBlocks = blocks.toMutableList()
        // In single-block mode, capture the current text and spans
        if (newBlocks.size == 1 && newBlocks[0] is NotesnookBlock.Text) {
            if (contentTfv.text.isBlank()) {
                newBlocks.clear()
            } else {
                newBlocks[0] = NotesnookBlock.Text(text = contentTfv.text, spans = richSpans)
            }
        }

        val currentIdx = activeBlockIndex.coerceIn(0, newBlocks.size)

        // If currently focused block is an empty Text block, replace it with the new widget!
        if (currentIdx < newBlocks.size && newBlocks[currentIdx] is NotesnookBlock.Text && (newBlocks[currentIdx] as NotesnookBlock.Text).text.isBlank()) {
            newBlocks[currentIdx] = blockToInsert
            val nextIdx = currentIdx + 1
            val hasTrailingText = nextIdx < newBlocks.size && newBlocks[nextIdx] is NotesnookBlock.Text
            if (!hasTrailingText) {
                newBlocks.add(nextIdx, NotesnookBlock.Text())
            }
            activeBlockIndex = nextIdx
        } else {
            // Otherwise insert after the active block (or at beginning if empty)
            val insertAt = if (newBlocks.isEmpty()) 0 else (currentIdx + 1).coerceAtMost(newBlocks.size)
            newBlocks.add(insertAt, blockToInsert)
            val nextIdx = insertAt + 1
            val hasTrailingText = nextIdx < newBlocks.size && newBlocks[nextIdx] is NotesnookBlock.Text
            if (!hasTrailingText) {
                newBlocks.add(nextIdx, NotesnookBlock.Text())
            }
            activeBlockIndex = nextIdx
        }

        // Deduplicate consecutive blank Text blocks so spacers never accumulate
        val cleanedBlocks = mutableListOf<NotesnookBlock>()
        for (b in newBlocks) {
            val last = cleanedBlocks.lastOrNull()
            if (b is NotesnookBlock.Text && b.text.isBlank() && last is NotesnookBlock.Text && last.text.isBlank()) {
                continue
            }
            cleanedBlocks.add(b)
        }

        syncAndCommitBlocks(if (cleanedBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else cleanedBlocks)
        showNotesnookInsertSheet = false
    }

    fun pasteAndFormatMcq() {
        val clipText = clipboardManager.getText()?.text
        if (clipText.isNullOrBlank()) {
            Toast.makeText(context, "Clipboard is empty. Copy question text first!", Toast.LENGTH_SHORT).show()
            return
        }
        val formatted = McqTextParser.formatIfMcq(clipText, "ABCD")
        val active = getActiveTextState()
        if (active != null) {
            val (idx, curTfv, curSpans) = active
            val sel = curTfv.selection
            val s = minOf(sel.start, sel.end)
            val e = maxOf(sel.start, sel.end)
            val prefixWithNewline = if (s > 0 && curTfv.text.getOrNull(s - 1) != '\n') "\n\n" else ""
            val insertStr = prefixWithNewline + formatted + "\n"
            val newText = curTfv.text.replaceRange(s, e, insertStr)
            val newTfv = curTfv.copy(text = newText, selection = TextRange(s + insertStr.length))
            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newText, curSpans)
            updateActiveTextState(idx, newTfv, updatedSpans)
        } else {
            val prefixWithNewline = if (contentTfv.text.isNotEmpty() && !contentTfv.text.endsWith("\n\n")) "\n\n" else ""
            val newText = contentTfv.text + prefixWithNewline + formatted + "\n"
            val newTfv = contentTfv.copy(text = newText, selection = TextRange(newText.length))
            val updatedSpans = RichTextEngine.updateSpansOnTextChange(contentTfv.text, newText, richSpans)
            richSpans = updatedSpans
            val updatedBlock = NotesnookBlock.Text(text = newText, spans = updatedSpans)
            blocks = listOf(updatedBlock)
            onContentChange(NotesnookBlockManager.serialize(blocks))
            contentTfv = newTfv
        }
        showFontPicker = true
        coroutineScope.launch {
            try {
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
        Toast.makeText(context, "Pasted & formatted as MCQ ✨", Toast.LENGTH_SHORT).show()
    }

    fun formatPastedMcq() {
        val raw = detectedMcqRaw ?: return
        val formatted = McqTextParser.formatIfMcq(raw, "ABCD")
        showMcqFormatPill = false
        detectedMcqRaw = null
        if (formatted == raw) return

        val currentText = contentTfv.text
        val exactIdx = currentText.indexOf(raw)
        val trimmedIdx = if (exactIdx == -1) currentText.indexOf(raw.trim()) else -1
        val replaceStart = when {
            exactIdx != -1 -> exactIdx
            trimmedIdx != -1 -> trimmedIdx
            else -> -1
        }
        val replaceEnd = when {
            exactIdx != -1 -> exactIdx + raw.length
            trimmedIdx != -1 -> trimmedIdx + raw.trim().length
            else -> -1
        }
        val newText = if (replaceStart != -1) {
            currentText.replaceRange(replaceStart, replaceEnd, formatted)
        } else {
            val sep = if (currentText.endsWith("\n\n")) "" else if (currentText.endsWith("\n")) "\n" else "\n\n"
            currentText + sep + formatted
        }
        val cursor = if (replaceStart != -1) replaceStart + formatted.length else newText.length
        val newTfv = contentTfv.copy(text = newText, selection = TextRange(cursor))
        val updatedSpans = RichTextEngine.updateSpansOnTextChange(contentTfv.text, newText, richSpans)
        richSpans = updatedSpans
        blocks = listOf(NotesnookBlock.Text(text = newText, spans = updatedSpans))
        onContentChange(NotesnookBlockManager.serialize(blocks))
        contentTfv = newTfv
        showFontPicker = true
        coroutineScope.launch {
            try {
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
        Toast.makeText(context, "Formatted as MCQ ✨", Toast.LENGTH_SHORT).show()
    }


    BackHandler {
        when {
            isZenMode -> isZenMode = false
            showTocSheet -> showTocSheet = false
            showExportSheet -> showExportSheet = false
            showEditorialSheet -> showEditorialSheet = false
            showDocumentStatsSheet -> showDocumentStatsSheet = false
            viewingImageUri != null -> viewingImageUri = null
            showSketchDialog -> showSketchDialog = false
            showColorPicker -> showColorPicker = false
            showAddSheet -> showAddSheet = false
            showNotesnookInsertSheet -> showNotesnookInsertSheet = false
            showCodeBlockDialog -> showCodeBlockDialog = false
            showMathDialog -> showMathDialog = false
            showCalloutDialog -> showCalloutDialog = false
            showEmbedDialog -> showEmbedDialog = false
            showTableDialog -> showTableDialog = false
            showImageOptionsSheet -> showImageOptionsSheet = false
            showAttachmentOptionsSheet -> showAttachmentOptionsSheet = false
            showLabelDialog -> showLabelDialog = false
            showMoreMenu -> showMoreMenu = false
            else -> onClose()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            uris.forEach { uri ->
                onAddImageUri(uri)
            }
        }
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            uris.forEach { uri ->
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType?.startsWith("image/") == true) {
                    onAddImageUri(uri)
                } else {
                    onAddAttachmentUri(uri)
                }
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap ->
            if (bitmap != null) {
                onPhotoTaken(bitmap)
            }
        }
    )

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    onVoiceInput(matches[0])
                }
            }
        }
    )

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to note...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    val scrollState = rememberScrollState()
    val contentBringIntoViewRequester = remember { BringIntoViewRequester() }

    val formattedTime = remember(state.updatedAt) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(state.updatedAt))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Google Keep style background illustration theme
        if (theme.isIllustratedTheme) {
            KeepThemeIllustration(
                themeType = theme.themeType,
                isDark = isDark,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 60.dp, end = 12.dp)
                    .size(width = 175.dp, height = 145.dp)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // TOP ACTION BAR (Google Keep & Article Style)
            // ==========================================
            if (isZenMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Zen Focus Mode",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.5f)
                    )
                    IconButton(
                        onClick = { isZenMode = false },
                        modifier = Modifier.testTag("exit_zen_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FullscreenExit,
                            contentDescription = "Exit Zen Mode",
                            tint = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and Back",
                            tint = textColor
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Table of Contents
                        IconButton(
                            onClick = { showTocSheet = true },
                            modifier = Modifier.testTag("editor_toc_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatListNumbered,
                                contentDescription = "Table of contents",
                                tint = textColor.copy(alpha = 0.85f)
                            )
                        }

                        // Zen Focus Mode
                        IconButton(
                            onClick = { isZenMode = true },
                            modifier = Modifier.testTag("editor_zen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fullscreen,
                                contentDescription = "Zen Focus Mode",
                                tint = textColor.copy(alpha = 0.85f)
                            )
                        }

                        // Pin Note
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.testTag("editor_pin_button")
                        ) {
                            Icon(
                                imageVector = if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (state.isPinned) "Unpin" else "Pin",
                                tint = if (state.isPinned) MaterialTheme.colorScheme.primary else textColor
                            )
                        }

                        // More Options (3-dots overflow menu)
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("editor_more_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = textColor
                            )
                        }
                    }
                }
            }

            // ==========================================
            // SCROLLABLE NOTE BODY
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // ATTACHED IMAGES (Google Keep style image collage)
                if (state.imageUris.isNotEmpty()) {
                    KeepEditorImageCollage(
                        imageUris = state.imageUris,
                        onImageClick = { uri -> viewingImageUri = uri },
                        onRemoveImage = onRemoveImage
                    )
                }

                // AUDIO ATTACHMENTS
                if (state.audioUris.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        state.audioUris.forEach { audioUri ->
                            val isPlaying = isAudioPlaying && activePlayingAudioPath == audioUri
                            val curPos = if (isPlaying) audioPositionMs else 0
                            val dur = if (isPlaying) audioDurationMs else 0
                            AudioPlayerEditorItem(
                                audioUri = audioUri,
                                isPlaying = isPlaying,
                                currentPositionMs = curPos,
                                durationMs = dur,
                                playbackSpeed = audioPlaybackSpeed,
                                onTogglePlay = { onToggleAudioPlay(audioUri) },
                                onSeek = onSeekAudio,
                                onSpeedChange = onSetAudioPlaybackSpeed,
                                onSkip = onSkipAudio,
                                onDelete = { onRemoveAudio(audioUri) },
                                textColor = textColor
                            )
                        }
                    }
                }

                // TITLE INPUT
                BasicTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input"),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = fontStyle.fontFamily
                    ),
                    cursorBrush = SolidColor(textColor),
                    decorationBox = { innerTextField ->
                        if (state.title.isEmpty()) {
                            Text(
                                text = "Title",
                                style = TextStyle(
                                    color = textColor.copy(alpha = 0.40f),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = fontStyle.fontFamily
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // NOTE CONTENT OR CHECKLIST
                if (!state.isChecklist) {
                    val primaryColor = MaterialTheme.colorScheme.primary

                    if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
                        // Standard WYSIWYG Single Text Note
                        val richVisualTransformation = remember(richSpans, textColor, primaryColor, isDark, fontSizeSp) {
                            RichTextEngine.createVisualTransformation(
                                spans = richSpans,
                                textColor = textColor,
                                accentColor = primaryColor,
                                isDark = isDark,
                                baseFontSizeSp = fontSizeSp
                            )
                        }

                        BasicTextField(
                            value = contentTfv,
                            onValueChange = { newTfv ->
                                val enterHandled = NotesnookFormattingHelper.handleEnterKey(contentTfv, newTfv)
                                val effectiveTfv = enterHandled ?: newTfv

                                val oldText = contentTfv.text
                                val newText = effectiveTfv.text
                                if (enterHandled != null || (newText.length == oldText.length + 1 && newText.getOrNull(effectiveTfv.selection.start - 1) == '\n')) {
                                    val headingTypes = setOf(
                                        RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                                        RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
                                    )
                                    pendingTypingStyles = pendingTypingStyles - headingTypes
                                }

                                if (oldText != newText) {
                                    val diff = newText.length - oldText.length
                                    if (diff >= 10) {
                                        val changeStart = oldText.zip(newText).indexOfFirst { (o, n) -> o != n }.let { if (it == -1) 0 else it }
                                        val changeEnd = changeStart + diff
                                        val inserted = newText.substring(changeStart, minOf(changeEnd, newText.length))
                                        if (McqTextParser.isLikelyMcq(inserted)) {
                                            detectedMcqRaw = inserted
                                            showMcqFormatPill = true
                                            mcqPillDismissJob?.cancel()
                                            mcqPillDismissJob = coroutineScope.launch {
                                                delay(6000)
                                                showMcqFormatPill = false
                                            }
                                        }
                                    }
                                    val updatedSpans = RichTextEngine.updateSpansOnTextChange(
                                        oldText = oldText,
                                        newText = newText,
                                        spans = richSpans,
                                        pendingTypes = pendingTypingStyles,
                                        pendingTextColor = selectedFontColorHex
                                    )
                                    richSpans = updatedSpans
                                    val updatedBlock = NotesnookBlock.Text(text = newText, spans = updatedSpans)
                                    blocks = listOf(updatedBlock)
                                    onContentChange(NotesnookBlockManager.serialize(blocks))
                                }
                                contentTfv = effectiveTfv
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 260.dp)
                                .focusRequester(contentFocusRequester)
                                .bringIntoViewRequester(contentBringIntoViewRequester)
                                .testTag("editor_content_input"),
                            visualTransformation = richVisualTransformation,
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = fontSizeSp.sp,
                                lineHeight = lineHeightSp.sp,
                                fontFamily = fontStyle.fontFamily,
                                textAlign = textAlignment
                            ),
                            cursorBrush = SolidColor(textColor),
                            decorationBox = { innerTextField ->
                                if (contentTfv.text.isEmpty()) {
                                    Text(
                                        text = "Note",
                                        style = TextStyle(
                                            color = textColor.copy(alpha = 0.40f),
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = lineHeightSp.sp,
                                            fontFamily = fontStyle.fontFamily
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    } else {
                        // Multi-Block Note (Seamless Tables, Code Blocks, Callouts, Formulas, Quotes, etc.)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            blocks.forEachIndexed { index, block ->
                                when (block) {
                                    is NotesnookBlock.Text -> {
                                        val blockId = block.id
                                        var textVal by remember(blockId) {
                                            val initTfv = textBlockStates[blockId] ?: TextFieldValue(block.text, TextRange(block.text.length))
                                            textBlockStates[blockId] = initTfv
                                            mutableStateOf(initTfv)
                                        }
                                        if (textVal.text != block.text) {
                                            val safeCursor = textVal.selection.start.coerceIn(0, block.text.length)
                                            textVal = textVal.copy(text = block.text, selection = TextRange(safeCursor))
                                            textBlockStates[blockId] = textVal
                                        }
                                        val textVisualTrans = remember(block.spans, textColor, primaryColor, isDark, fontSizeSp) {
                                            RichTextEngine.createVisualTransformation(
                                                spans = block.spans,
                                                textColor = textColor,
                                                accentColor = primaryColor,
                                                isDark = isDark,
                                                baseFontSizeSp = fontSizeSp
                                            )
                                        }
                                        BasicTextField(
                                            value = textVal,
                                            onValueChange = { newTfv ->
                                                val enterHandled = NotesnookFormattingHelper.handleEnterKey(textVal, newTfv)
                                                val eff = enterHandled ?: newTfv
                                                textVal = eff
                                                textBlockStates[blockId] = eff
                                                activeBlockIndex = index
                                                val oldT = block.text
                                                val newT = eff.text

                                                if (enterHandled != null || (newT.length == oldT.length + 1 && newT.getOrNull(eff.selection.start - 1) == '\n')) {
                                                    val headingTypes = setOf(
                                                        RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                                                        RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
                                                    )
                                                    pendingTypingStyles = pendingTypingStyles - headingTypes
                                                }

                                                if (oldT != newT) {
                                                    val updatedS = RichTextEngine.updateSpansOnTextChange(
                                                        oldText = oldT,
                                                        newText = newT,
                                                        spans = block.spans,
                                                        pendingTypes = pendingTypingStyles,
                                                        pendingTextColor = selectedFontColorHex
                                                    )
                                                    val updatedBlock = block.copy(text = newT, spans = updatedS)
                                                    val newBlocks = blocks.toMutableList()
                                                    newBlocks[index] = updatedBlock
                                                    syncAndCommitBlocks(newBlocks)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        activeBlockIndex = index
                                                    }
                                                }
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace && textVal.text.isEmpty() && blocks.size > 1) {
                                                        val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                        activeBlockIndex = (index - 1).coerceAtLeast(0)
                                                        syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                },
                                            visualTransformation = textVisualTrans,
                                            textStyle = TextStyle(
                                                color = textColor,
                                                fontSize = fontSizeSp.sp,
                                                lineHeight = lineHeightSp.sp,
                                                fontFamily = fontStyle.fontFamily,
                                                textAlign = textAlignment
                                            ),
                                            cursorBrush = SolidColor(textColor),
                                            decorationBox = { innerTextField ->
                                                if (block.text.isEmpty() && index == 0 && blocks.none { it !is NotesnookBlock.Text || (it as? NotesnookBlock.Text)?.text?.isNotEmpty() == true }) {
                                                    Text(
                                                        text = "Note",
                                                        style = TextStyle(
                                                            color = textColor.copy(alpha = 0.35f),
                                                            fontSize = fontSizeSp.sp,
                                                            lineHeight = lineHeightSp.sp,
                                                            fontFamily = fontStyle.fontFamily
                                                        )
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }
                                    is NotesnookBlock.Table -> {
                                        NotesnookTableWidget(
                                            table = block,
                                            onUpdate = {
                                                val freshTable = block.copy(
                                                    rows = block.rows,
                                                    cols = block.cols,
                                                    data = block.data.map { it.toMutableList() }.toMutableList(),
                                                    columnWidths = block.columnWidths.toMutableList()
                                                )
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = freshTable
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            textColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.Code -> {
                                        NotesnookCodeBlockWidget(
                                            block = block,
                                            onUpdate = {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark
                                        )
                                    }
                                    is NotesnookBlock.Callout -> {
                                        NotesnookCalloutWidget(
                                            block = block,
                                            onUpdate = {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.MathFormula -> {
                                        NotesnookMathWidget(
                                            block = block,
                                            onUpdate = {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.HorizontalRule -> {
                                        NotesnookHorizontalRuleWidget(
                                            block = block,
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark
                                        )
                                    }
                                    is NotesnookBlock.Quote -> {
                                        NotesnookQuoteWidget(
                                            block = block,
                                            onUpdate = {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.OutlineItem -> {
                                        NotesnookOutlineWidget(
                                            block = block,
                                            onUpdate = {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.Embed -> {
                                        NotesnookEmbedWidget(
                                            block = block,
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.Attachment -> {
                                        NotesnookAttachmentWidget(
                                            block = block,
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                    is NotesnookBlock.Image -> {
                                        NotesnookImageBlockWidget(
                                            block = block,
                                            onUpdate = {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
                                                syncAndCommitBlocks(newBlocks)
                                            },
                                            onDelete = {
                                                val newBlocks = blocks.filterIndexed { i, _ -> i != index }
                                                syncAndCommitBlocks(if (newBlocks.isEmpty()) listOf(NotesnookBlock.Text()) else newBlocks)
                                            },
                                            onClick = { viewingImageUri = block.uri },
                                            isDark = isDark,
                                            noteTextColor = textColor
                                        )
                                    }
                                }
                            }

                            if (blocks.isNotEmpty() && blocks.last() !is NotesnookBlock.Text) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newBlocks = blocks.toMutableList()
                                            newBlocks.add(NotesnookBlock.Text())
                                            syncAndCommitBlocks(newBlocks)
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "Tap to write...",
                                        style = TextStyle(
                                            color = textColor.copy(alpha = 0.35f),
                                            fontSize = fontSizeSp.sp,
                                            fontFamily = fontStyle.fontFamily
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Checklist Items
                    val uncompletedItems = remember(state.checklistItems) {
                        state.checklistItems.mapIndexedNotNull { index, item ->
                            if (!item.isChecked) Pair(index, item) else null
                        }
                    }
                    val completedItems = remember(state.checklistItems) {
                        state.checklistItems.mapIndexedNotNull { index, item ->
                            if (item.isChecked) Pair(index, item) else null
                        }
                    }
                    val completedCount = completedItems.size

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Uncompleted items
                        uncompletedItems.forEachIndexed { posInList, (globalIndex, item) ->
                            key(item.id) {
                                ChecklistRow(
                                    item = item,
                                    textColor = textColor,
                                    fontFamily = fontStyle.fontFamily,
                                    canMoveUp = posInList > 0,
                                    canMoveDown = posInList < uncompletedItems.size - 1,
                                    isTargetFocus = item.id == targetFocusItemId,
                                    onFocused = { if (targetFocusItemId == item.id) targetFocusItemId = null },
                                    onToggle = { onToggleChecklistItem(globalIndex) },
                                    onTextChange = { onUpdateChecklistItemText(globalIndex, it) },
                                    onEnterPressed = { extraText ->
                                        val newId = java.util.UUID.randomUUID().toString()
                                        targetFocusItemId = newId
                                        onAddChecklistItem(globalIndex, extraText, newId)
                                    },
                                    onDelete = { onRemoveChecklistItem(globalIndex) },
                                    onMoveUp = {
                                        if (posInList > 0) {
                                            val targetGlobalIndex = uncompletedItems[posInList - 1].first
                                            onMoveChecklistItem(globalIndex, targetGlobalIndex)
                                        }
                                    },
                                    onMoveDown = {
                                        if (posInList < uncompletedItems.size - 1) {
                                            val targetGlobalIndex = uncompletedItems[posInList + 1].first
                                            onMoveChecklistItem(globalIndex, targetGlobalIndex)
                                        }
                                    }
                                )
                            }
                        }

                        // Add new list item button row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val newId = java.util.UUID.randomUUID().toString()
                                    targetFocusItemId = newId
                                    val lastUncompletedGlobalIndex = uncompletedItems.lastOrNull()?.first
                                    onAddChecklistItem(lastUncompletedGlobalIndex, "", newId)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add item",
                                tint = textColor.copy(alpha = 0.55f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "List item",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                color = textColor.copy(alpha = 0.55f)
                            )
                        }

                        // Collapsible Completed Items Section
                        if (completedCount > 0) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = textColor.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { completedExpanded = !completedExpanded }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (completedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$completedCount Completed items",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }

                            AnimatedVisibility(
                                visible = completedExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    completedItems.forEachIndexed { posInList, (globalIndex, item) ->
                                        key(item.id) {
                                            ChecklistRow(
                                                item = item,
                                                textColor = textColor,
                                                fontFamily = fontStyle.fontFamily,
                                                canMoveUp = posInList > 0,
                                                canMoveDown = posInList < completedItems.size - 1,
                                                isTargetFocus = item.id == targetFocusItemId,
                                                onFocused = { if (targetFocusItemId == item.id) targetFocusItemId = null },
                                                onToggle = { onToggleChecklistItem(globalIndex) },
                                                onTextChange = { onUpdateChecklistItemText(globalIndex, it) },
                                                onEnterPressed = { extraText ->
                                                    val newId = java.util.UUID.randomUUID().toString()
                                                    targetFocusItemId = newId
                                                    onAddChecklistItem(globalIndex, extraText, newId)
                                                },
                                                onDelete = { onRemoveChecklistItem(globalIndex) },
                                                onMoveUp = {
                                                    if (posInList > 0) {
                                                        val targetGlobalIndex = completedItems[posInList - 1].first
                                                        onMoveChecklistItem(globalIndex, targetGlobalIndex)
                                                    }
                                                },
                                                onMoveDown = {
                                                    if (posInList < completedItems.size - 1) {
                                                        val targetGlobalIndex = completedItems[posInList + 1].first
                                                        onMoveChecklistItem(globalIndex, targetGlobalIndex)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // BOTTOM CHIPS: LABELS (Google Keep Layout)
                // ==========================================
                if (state.labels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // LABELS DISPLAY
                        state.labels.forEach { label ->
                            Surface(
                                shape = CircleShape,
                                color = textColor.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, top = 4.dp, end = 6.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove label",
                                        tint = textColor.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .clickable { onRemoveLabel(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // ==========================================
            // COLOR PALETTE DRAWER (IF OPEN)
            // ==========================================
            AnimatedVisibility(
                visible = showColorPicker,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = if (isDark) Color(0xFF101012) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_theme_palette_drawer")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // SECTION 1: COLOUR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COLOUR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(KeepColorPalette.allColors) { colorTheme ->
                                val isSelected = colorTheme.key.equals(state.colorKey, ignoreCase = true)
                                val isDefault = colorTheme.key.equals("default", ignoreCase = true)
                                val swatchBg = if (isDefault) MaterialTheme.colorScheme.surfaceVariant else colorTheme.swatchColor
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(swatchBg)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.2.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                            shape = CircleShape
                                        )
                                        .clickable { onColorChange(colorTheme.key) }
                                        .testTag("color_picker_${colorTheme.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (isDefault) {
                                        Icon(
                                            imageVector = Icons.Filled.Block,
                                            contentDescription = "No color",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // SECTION 2: BACKGROUND THEMES
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BACKGROUND",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // "None" option to reset theme to default
                            item {
                                val isNoneSelected = state.colorKey.equals("default", ignoreCase = true)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onColorChange("default") }
                                        .testTag("theme_picker_none")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                width = if (isNoneSelected) 3.dp else 1.2.dp,
                                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isNoneSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "None selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Block,
                                                contentDescription = "None",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "None",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(KeepColorPalette.allThemes) { themeItem ->
                                val isSelected = themeItem.key.equals(state.colorKey, ignoreCase = true)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onColorChange(themeItem.key) }
                                        .testTag("theme_picker_${themeItem.key}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(themeItem.swatchColor)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.2.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "${themeItem.name} selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else if (themeItem.icon != null) {
                                            Icon(
                                                imageVector = themeItem.icon,
                                                contentDescription = themeItem.name,
                                                tint = Color.White.copy(alpha = 0.9f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = themeItem.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // NOTESNOOK EDITING & TYPOGRAPHY DRAWER (IF OPEN)
            // ==========================================
            AnimatedVisibility(
                visible = showFontPicker,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                fun keepFocus() {
                    try {
                        contentFocusRequester.requestFocus()
                        keyboardController?.show()
                    } catch (_: Exception) {}
                }

                fun toggleSpanStyle(type: RichSpanType) {
                    val active = getActiveTextState()
                    if (active != null) {
                        val (idx, curTfv, curSpans) = active
                        val sel = curTfv.selection
                        if (sel.start != sel.end) {
                            val newSpans = RichTextEngine.toggleSpan(curSpans, type, sel, curTfv.text.length)
                            updateActiveTextState(idx, curTfv, newSpans)
                        } else {
                            pendingTypingStyles = if (pendingTypingStyles.contains(type)) {
                                pendingTypingStyles - type
                            } else {
                                pendingTypingStyles + type
                            }
                        }
                    }
                    keepFocus()
                }

                NotesnookEditorDrawer(
                    activeStyles = activeStyles,
                    onToggleBold = { toggleSpanStyle(RichSpanType.BOLD) },
                    onToggleItalic = { toggleSpanStyle(RichSpanType.ITALIC) },
                    onToggleUnderline = { toggleSpanStyle(RichSpanType.UNDERLINE) },
                    onToggleStrikethrough = { toggleSpanStyle(RichSpanType.STRIKETHROUGH) },
                    onToggleHighlight = { toggleSpanStyle(RichSpanType.HIGHLIGHT) },
                    onToggleCode = { toggleSpanStyle(RichSpanType.CODE) },
                    onToggleSubscript = { toggleSpanStyle(RichSpanType.SUBSCRIPT) },
                    onToggleSuperscript = { toggleSpanStyle(RichSpanType.SUPERSCRIPT) },
                    onToggleHeading = { level ->
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val cursor = curTfv.selection.start
                            val lineStart = curTfv.text.lastIndexOf('\n', startIndex = maxOf(0, cursor - 1)).let { if (it == -1) 0 else it + 1 }
                            val lineEnd = curTfv.text.indexOf('\n', startIndex = cursor).let { if (it == -1) curTfv.text.length else it }

                            val headingTypes = setOf(
                                RichSpanType.HEADING_1, RichSpanType.HEADING_2, RichSpanType.HEADING_3,
                                RichSpanType.HEADING_4, RichSpanType.HEADING_5, RichSpanType.HEADING_6
                            )
                            val selectedHeadingType = when (level) {
                                1 -> RichSpanType.HEADING_1
                                2 -> RichSpanType.HEADING_2
                                3 -> RichSpanType.HEADING_3
                                4 -> RichSpanType.HEADING_4
                                5 -> RichSpanType.HEADING_5
                                6 -> RichSpanType.HEADING_6
                                else -> null
                            }
                            pendingTypingStyles = (pendingTypingStyles - headingTypes) + (selectedHeadingType?.let { setOf(it) } ?: emptySet())

                            val newSpans = if (level == 0) {
                                curSpans.filterNot {
                                    it.start >= lineStart && it.end <= lineEnd && headingTypes.contains(it.type)
                                }
                            } else {
                                val type = selectedHeadingType!!
                                if (lineEnd > lineStart) {
                                    RichTextEngine.toggleLineStyle(curSpans, type, cursor, curTfv.text)
                                } else {
                                    curSpans
                                }
                            }
                            updateActiveTextState(idx, curTfv, newSpans)
                        }
                        keepFocus()
                    },
                    onToggleBullet = {
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.applyLinePrefix(curTfv, "- ")
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                        keepFocus()
                    },
                    onToggleNumbered = { prefix ->
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.applyLinePrefix(curTfv, prefix)
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                        keepFocus()
                    },
                    onToggleQuote = {
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val cursor = curTfv.selection.start
                            val newSpans = RichTextEngine.toggleLineStyle(curSpans, RichSpanType.QUOTE, cursor, curTfv.text)
                            updateActiveTextState(idx, curTfv, newSpans)
                        }
                        keepFocus()
                    },
                    onInsertDivider = {
                        insertBlockItem(NotesnookBlock.HorizontalRule())
                    },
                    onInsertLink = {
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.insertLinkTemplate(curTfv)
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                        keepFocus()
                    },
                    onInsertCallout = { tag ->
                        insertBlockItem(NotesnookBlock.Callout(calloutType = tag.lowercase(), text = ""))
                    },
                    onInsertTimestamp = {
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.insertTimestamp(curTfv)
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                        keepFocus()
                    },
                    onOpenInsertMenu = {
                        showNotesnookInsertSheet = true
                    },
                    onIndent = { isOutdent ->
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.indent(curTfv, isOutdent)
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                        keepFocus()
                    },
                    onClearFormatting = {
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val sel = curTfv.selection
                            val s = minOf(sel.start, sel.end)
                            val e = maxOf(sel.start, sel.end)
                            val newSpans = if (s != e) {
                                curSpans.filterNot { it.start < e && it.end > s }
                            } else {
                                val lineStart = curTfv.text.lastIndexOf('\n', startIndex = maxOf(0, s - 1)).let { if (it == -1) 0 else it + 1 }
                                val lineEnd = curTfv.text.indexOf('\n', startIndex = s).let { if (it == -1) contentTfv.text.length else it }
                                curSpans.filterNot { it.start >= lineStart && it.end <= lineEnd }
                            }
                            pendingTypingStyles = emptySet()
                            selectedFontColorHex = null
                            updateActiveTextState(idx, curTfv, newSpans)
                        }
                        keepFocus()
                    },
                    selectedFontKey = state.fontKey,
                    onFontChange = onFontChange,
                    selectedFontColorHex = selectedFontColorHex,
                    onFontColorChange = { hex ->
                        selectedFontColorHex = hex
                        val active = getActiveTextState()
                        if (active != null) {
                            val (idx, curTfv, curSpans) = active
                            val sel = curTfv.selection
                            val s = minOf(sel.start, sel.end)
                            val e = maxOf(sel.start, sel.end)
                            if (s != e) {
                                val filtered = curSpans.filterNot { it.type == RichSpanType.TEXT_COLOR && it.start < e && it.end > s }.toMutableList()
                                if (hex != null) {
                                    filtered.add(RichSpan(RichSpanType.TEXT_COLOR, s, e, payload = hex))
                                }
                                updateActiveTextState(idx, curTfv, filtered.sortedBy { it.start })
                            }
                        }
                        keepFocus()
                    },
                    textAlign = textAlignment,
                    onCycleAlignment = {
                        textAlignment = when (textAlignment) {
                            TextAlign.Start, TextAlign.Left -> TextAlign.Center
                            TextAlign.Center -> TextAlign.End
                            else -> TextAlign.Start
                        }
                        keepFocus()
                    },
                    fontSizeSp = fontSizeSp,
                    onFontSizeChange = { fontSizeSp = it },
                    lineHeightSp = lineHeightSp,
                    onLineHeightChange = { lineHeightSp = it },
                    isChecklistMode = state.isChecklist,
                    onToggleChecklistMode = onToggleChecklistMode,
                    onCloseDrawer = { showFontPicker = false },
                    isDark = isDark
                )
            }
        }

        // ==========================================
        // SMART MCQ FORMAT FLOATING PILL (FLOATS OVER NOTE BODY ABOVE TOOLBAR)
        // ==========================================
        AnimatedVisibility(
            visible = showMcqFormatPill,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showFontPicker) 58.dp else 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF1E2822) else Color(0xFFE8F5E9),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF284834) else Color(0xFFA5D6A7)),
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Quiz,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Format as MCQ?",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = if (isDark) Color(0xFF34D399) else Color(0xFF166534)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            formatPastedMcq()
                            showMcqFormatPill = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Format", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showMcqFormatPill = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // NOTESNOOK / KEEP "MORE" 3-DOT OVERFLOW SHEET
        // ==========================================
        if (showMoreMenu) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { showMoreMenu = false },
                sheetState = sheetState,
                containerColor = if (isDark) Color(0xFF161719) else Color(0xFFF9FAFB),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)
                ) {
                    // Custom centered drag handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp, bottom = 16.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF3C3F46) else Color(0xFFD0D3DC))
                    )

                    // Top Row: Note Title + Open/Zen Icon
                    val createdAtDate = remember(state.createdAt) { Date(state.createdAt) }
                    val updatedAtDate = remember(state.updatedAt) { Date(state.updatedAt) }
                    val dateTimeFormat = remember { SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()) }
                    val createdAtFormatted = remember(createdAtDate) { dateTimeFormat.format(createdAtDate) }
                    val updatedAtFormatted = remember(updatedAtDate) { dateTimeFormat.format(updatedAtDate) }

                    val displayTitle = if (state.title.isNotBlank()) {
                        state.title
                    } else {
                        "Note $createdAtFormatted"
                    }

                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = if (isDark) Color(0xFFF2F3F5) else Color(0xFF1E2024),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Date & Time Metadata Rows
                    // Row 1: Created at
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Created at",
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFF7E828C) else Color(0xFF6B7280)
                        )
                        Text(
                            text = createdAtFormatted,
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFFA2A6B0) else Color(0xFF374151)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: Last edited at
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last edited at",
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFF7E828C) else Color(0xFF6B7280)
                        )
                        Text(
                            text = updatedAtFormatted,
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFFA2A6B0) else Color(0xFF374151)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Tag and Color Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Add label +" chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDark) Color(0xFF1E2822) else Color(0xFFE8F5E9))
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF284834) else Color(0xFFA5D6A7),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    showMoreMenu = false
                                    showLabelDialog = true
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            val tagText = if (state.labels.isNotEmpty()) {
                                state.labels.first() + if (state.labels.size > 1) " +${state.labels.size - 1}" else ""
                            } else {
                                "Add label +"
                            }
                            Text(
                                text = tagText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF166534)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Color swatch circle
                        val noteColor = theme.resolveBackgroundColor(isDark)
                        val displayColor = if (state.colorKey == "default") {
                            if (isDark) Color(0xFF90CAF9) else Color(0xFF64B5F6)
                        } else {
                            noteColor
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(displayColor)
                                .border(
                                    1.5.dp,
                                    if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .clickable {
                                    showMoreMenu = false
                                    showColorPicker = true
                                }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Plus button for color picker
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF23252A) else Color(0xFFE5E7EB))
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF2E323A) else Color(0xFFD1D5DB),
                                    CircleShape
                                )
                                .clickable {
                                    showMoreMenu = false
                                    showColorPicker = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Change color",
                                tint = if (isDark) Color(0xFFA0A3AD) else Color(0xFF4B5563),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ==========================================
                    // ROW 1: PRIMARY QUICK ACTIONS (5 columns)
                    // Pin | Archive | Share | Copy text | Export
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Pin / Unpin
                        KeepOverflowGridItem(
                            icon = if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            label = if (state.isPinned) "Unpin" else "Pin",
                            isDark = isDark,
                            isActive = state.isPinned,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onTogglePin()
                            }
                        )

                        // 2. Archive / Unarchive
                        KeepOverflowGridItem(
                            icon = if (state.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            label = if (state.isArchived) "Unarchive" else "Archive",
                            isDark = isDark,
                            isActive = state.isArchived,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                if (state.isArchived) onUnarchive() else onArchive()
                            }
                        )

                        // 3. Share
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                onShare()
                            }
                        )

                        // 4. Copy text (swapped with Duplicate)
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.ContentCopy,
                            label = "Copy\ntext",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                onCopyText()
                            }
                        )

                        // 5. Export
                        KeepOverflowGridItem(
                            icon = Icons.AutoMirrored.Outlined.ExitToApp,
                            label = "Export",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showExportSheet = true
                            }
                        )
                    }

                    // Centered pill / dot indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 3.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF4C505A) else Color(0xFF9CA3AF))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 3.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF2C2F36) else Color(0xFFD1D5DB))
                        )
                    }

                    // ==========================================
                    // GRID SECTION: 5 COLUMNS (Real App Features)
                    // ==========================================

                    // Row 1 of Grid: User most-used actions first
                    // Add image | Take photo | Live transcription | Tick boxes | Drawing sketch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Add image
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.Image,
                            label = "Add\nimage",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )

                        // 2. Take photo
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.PhotoCamera,
                            label = "Take\nphoto",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                cameraLauncher.launch(null)
                            }
                        )

                        // 3. Live transcription
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.Mic,
                            label = "Live\ntranscription",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                onStartVoiceRecording()
                            }
                        )

                        // 4. Tick boxes
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.CheckBox,
                            label = if (state.isChecklist) "Hide tick\nboxes" else "Tick\nboxes",
                            isDark = isDark,
                            isActive = state.isChecklist,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                onToggleChecklistMode()
                            }
                        )

                        // 5. Drawing sketch
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.Brush,
                            label = "Drawing\nsketch",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showSketchDialog = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2 of Grid:
                    // Duplicate | Table of contents | Document stats | Editorial typography | Font style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Duplicate (swapped with Copy text)
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.CopyAll,
                            label = "Duplicate",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                onDuplicate()
                            }
                        )

                        // 2. Table of contents
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.FormatListNumbered,
                            label = "Table of\ncontents",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showTocSheet = true
                            }
                        )

                        // 3. Document statistics
                        KeepOverflowGridItem(
                            icon = Icons.AutoMirrored.Outlined.Article,
                            label = "Document\nstats",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showDocumentStatsSheet = true
                            }
                        )

                        // 4. Editorial typography
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.FormatLineSpacing,
                            label = "Editorial\ntypography",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showEditorialSheet = true
                            }
                        )

                        // 5. Font style
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.FontDownload,
                            label = "Font\nstyle",
                            isDark = isDark,
                            isActive = showFontPicker,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showFontPicker = !showFontPicker
                                showColorPicker = false
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 3 of Grid:
                    // Zen Focus mode | Attached files | Insert element | Move to trash | Spacer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Zen Focus mode
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.Fullscreen,
                            label = "Zen Focus\nmode",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                isZenMode = true
                            }
                        )

                        // 2. Attached files
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.AttachFile,
                            label = "Attached\nfiles",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showAttachmentOptionsSheet = true
                            }
                        )

                        // 3. Insert element (table, math, callout, code)
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.AddBox,
                            label = "Insert\nelement",
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                showNotesnookInsertSheet = true
                            }
                        )

                        // 4. Move to trash
                        KeepOverflowGridItem(
                            icon = Icons.Outlined.Delete,
                            label = "Move to\ntrash",
                            isDark = isDark,
                            tint = Color(0xFFEF4444), // Prominent RED trash icon matching the UI
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showMoreMenu = false
                                onDelete()
                            }
                        )

                        // 5. Empty spacer to align 4 items symmetrically in 5-column grid
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ==========================================
        // NOTESNOOK AUTHENTIC INSERT BOTTOM SHEET
        // ==========================================
        if (showNotesnookInsertSheet) {
            NotesnookInsertBottomSheet(
                onDismissRequest = { showNotesnookInsertSheet = false },
                onInsertOutlineList = {
                    insertBlockItem(NotesnookBlock.OutlineItem(level = 0, text = ""))
                },
                onInsertHorizontalRule = {
                    insertBlockItem(NotesnookBlock.HorizontalRule())
                },
                onOpenCodeBlockDialog = {
                    showCodeBlockDialog = true
                },
                onOpenMathDialog = {
                    showMathDialog = true
                },
                onOpenCalloutDialog = {
                    showCalloutDialog = true
                },
                onInsertQuote = {
                    insertBlockItem(NotesnookBlock.Quote(text = ""))
                },
                onOpenImageDialog = {
                    showImageOptionsSheet = true
                },
                onOpenAttachmentDialog = {
                    showAttachmentOptionsSheet = true
                },
                onOpenEmbedDialog = {
                    showEmbedDialog = true
                },
                onOpenTableDialog = {
                    showTableDialog = true
                },
                onPasteAsMcq = {
                    pasteAndFormatMcq()
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK TABLE BUILDER DIALOG
        // ==========================================
        if (showTableDialog) {
            NotesnookTableBuilderDialog(
                onDismiss = { showTableDialog = false },
                onInsertTable = { rows, cols, data ->
                    insertBlockItem(
                        NotesnookBlock.Table(
                            rows = rows,
                            cols = cols,
                            data = data.map { it.toMutableList() }.toMutableList()
                        )
                    )
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK MATH & FORMULAS DIALOG
        // ==========================================
        if (showMathDialog) {
            NotesnookMathDialog(
                onDismiss = { showMathDialog = false },
                onInsertFormula = { formula, isInline ->
                    insertBlockItem(
                        NotesnookBlock.MathFormula(formula = formula, isInline = isInline)
                    )
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK CODE BLOCK DIALOG
        // ==========================================
        if (showCodeBlockDialog) {
            NotesnookCodeBlockDialog(
                onDismiss = { showCodeBlockDialog = false },
                onInsertCodeBlock = { lang ->
                    insertBlockItem(
                        NotesnookBlock.Code(language = lang, code = "")
                    )
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK CALLOUT DIALOG
        // ==========================================
        if (showCalloutDialog) {
            NotesnookCalloutDialog(
                onDismiss = { showCalloutDialog = false },
                onSelectCallout = { type ->
                    insertBlockItem(
                        NotesnookBlock.Callout(calloutType = type, text = "")
                    )
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK EMBED DIALOG
        // ==========================================
        if (showEmbedDialog) {
            NotesnookEmbedDialog(
                onDismiss = { showEmbedDialog = false },
                onInsertEmbed = { type, url, title ->
                    insertBlockItem(
                        NotesnookBlock.Embed(type = type, url = url, title = title)
                    )
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK IMAGE OPTIONS SHEET
        // ==========================================
        if (showImageOptionsSheet) {
            NotesnookImageOptionsSheet(
                onDismiss = { showImageOptionsSheet = false },
                onTakePhoto = { cameraLauncher.launch(null) },
                onPickGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onInsertUrl = { url, altCaption ->
                    insertBlockItem(
                        NotesnookBlock.Image(uri = url, caption = altCaption)
                    )
                },
                isDark = isDark
            )
        }

        // ==========================================
        // NOTESNOOK ATTACHMENT OPTIONS SHEET
        // ==========================================
        if (showAttachmentOptionsSheet) {
            NotesnookAttachmentOptionsSheet(
                onDismiss = { showAttachmentOptionsSheet = false },
                onPickDocument = {
                    documentPickerLauncher.launch("*/*")
                },
                onRecordAudio = {
                    onStartVoiceRecording()
                },
                onOpenSketch = {
                    showSketchDialog = true
                },
                isDark = isDark
            )
        }

        // ==========================================
        // GOOGLE KEEP "+" ADD SHEET
        // ==========================================
        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                containerColor = if (isDark) Color(0xFF101012) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, top = 6.dp)
                ) {
                    KeepAddOptionRow(
                        icon = Icons.Outlined.PhotoCamera,
                        title = "Take photo",
                        onClick = {
                            showAddSheet = false
                            cameraLauncher.launch(null)
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Image,
                        title = "Add image",
                        onClick = {
                            showAddSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Brush,
                        title = "Drawing",
                        onClick = {
                            showAddSheet = false
                            showSketchDialog = true
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Mic,
                        title = "Live transcription",
                        onClick = {
                            showAddSheet = false
                            onStartVoiceRecording()
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.CheckBox,
                        title = if (state.isChecklist) "Hide tick boxes" else "Tick boxes",
                        onClick = {
                            showAddSheet = false
                            onToggleChecklistMode()
                        }
                    )
                }
            }
        }

        // ==========================================
        // NOTE LABELS DIALOG
        // ==========================================
        if (showLabelDialog) {
            NoteLabelsDialog(
                allLabels = allLabels,
                selectedLabels = state.labels,
                onToggleLabel = onToggleLabel,
                onCreateAndAddLabel = onCreateAndAddLabel,
                onDismiss = { showLabelDialog = false }
            )
        }

        // ==========================================
        // DRAWING / SKETCH CANVAS DIALOG
        // ==========================================
        if (showSketchDialog) {
            KeepSketchDialog(
                onDismiss = { showSketchDialog = false },
                onSaveDrawing = { bitmap ->
                    onAddDrawing(bitmap)
                    showSketchDialog = false
                }
            )
        }

        // ==========================================
        // DOCUMENT STATISTICS BOTTOM SHEET
        // ==========================================
        if (showDocumentStatsSheet) {
            DocumentStatsBottomSheet(
                metrics = documentMetrics,
                isDark = isDark,
                onDismiss = { showDocumentStatsSheet = false }
            )
        }

        // ==========================================
        // TABLE OF CONTENTS SHEET
        // ==========================================
        if (showTocSheet) {
            val tocItems = remember(state.title, blocks, state.content, contentTfv.text) {
                ArticleTocHelper.extractToc(
                    title = state.title,
                    blocks = blocks,
                    fallbackContent = contentTfv.text
                )
            }
            ArticleTocBottomSheet(
                tocItems = tocItems,
                isDark = isDark,
                onSelectTocItem = { tocItem ->
                    activeBlockIndex = tocItem.blockIndex
                    showTocSheet = false
                },
                onDismiss = { showTocSheet = false }
            )
        }

        // ==========================================
        // ARTICLE EXPORT SHEET
        // ==========================================
        if (showExportSheet) {
            ArticleExportBottomSheet(
                title = state.title,
                blocks = blocks,
                content = contentTfv.text,
                labels = state.labels,
                isChecklist = state.isChecklist,
                checklistItems = state.checklistItems,
                isDark = isDark,
                onDismiss = { showExportSheet = false }
            )
        }

        // ==========================================
        // EDITORIAL TYPOGRAPHY SHEET
        // ==========================================
        if (showEditorialSheet) {
            ArticleEditorialBottomSheet(
                currentLineSpacing = lineSpacingPreset,
                onSelectLineSpacing = { preset ->
                    lineSpacingPreset = preset
                    lineHeightSp = preset.lineHeightSp
                },
                onInsertPullQuote = {
                    insertBlockItem(NotesnookBlock.Quote(text = "Insert pull quote text..."))
                },
                onInsertFootnote = {
                    val active = getActiveTextState()
                    if (active != null) {
                        val (idx, curTfv, curSpans) = active
                        val sel = curTfv.selection
                        val cursor = sel.start.coerceIn(0, curTfv.text.length)
                        val footnoteNum = (blocks.count { it is NotesnookBlock.Text && it.text.contains("[^") } + 1)
                        val fnMarker = "[^$footnoteNum]"
                        val newText = curTfv.text.substring(0, cursor) + fnMarker + curTfv.text.substring(cursor)
                        val newTfv = TextFieldValue(newText, TextRange(cursor + fnMarker.length))
                        updateActiveTextState(idx, newTfv, curSpans)

                        val fnDefBlock = NotesnookBlock.Text(
                            text = "$fnMarker Footnote reference detail...",
                            spans = listOf(RichSpan(RichSpanType.ITALIC, 0, fnMarker.length + 28))
                        )
                        val newBlocks = blocks.toMutableList().apply { add(fnDefBlock) }
                        syncAndCommitBlocks(newBlocks)
                    }
                },
                isDark = isDark,
                onDismiss = { showEditorialSheet = false }
            )
        }

        // ==========================================
        // FULL SCREEN IMAGE VIEWER
        // ==========================================
        if (viewingImageUri != null) {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ZoomableImagePager(
                imageUris = state.imageUris,
                initialUri = viewingImageUri!!,
                onDismiss = { viewingImageUri = null },
                bottomInset = navBarBottom
            )
        }
    }
}

@Composable
private fun KeepOverflowGridItem(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isActive) {
                        if (isDark) Color(0xFF2C323D) else Color(0xFFE0E7FF)
                    } else {
                        if (isDark) Color(0xFF212328) else Color(0xFFF1F3F5)
                    }
                )
                .border(
                    1.dp,
                    if (isActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    } else {
                        if (isDark) Color(0xFF2B2E35) else Color(0xFFE2E4E9)
                    },
                    RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.replace("\n", " "),
                tint = tint ?: if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    if (isDark) Color(0xFFC7CAD2) else Color(0xFF374151)
                },
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                if (isDark) Color(0xFFA0A3AD) else Color(0xFF4B5563)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KeepAddOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistRow(
    item: com.focusbyrj.app.data.note.ChecklistItem,
    textColor: Color,
    fontFamily: FontFamily = FontFamily.Default,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isTargetFocus: Boolean,
    onFocused: () -> Unit,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnterPressed: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val textFieldBringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val rowThresholdPx = remember(density) { with(density) { 44.dp.toPx() } }

    val currentCanMoveUp by rememberUpdatedState(canMoveUp)
    val currentCanMoveDown by rememberUpdatedState(canMoveDown)
    val currentOnMoveUp by rememberUpdatedState(onMoveUp)
    val currentOnMoveDown by rememberUpdatedState(onMoveDown)

    LaunchedEffect(isTargetFocus) {
        if (isTargetFocus) {
            bringIntoViewRequester.bringIntoView()
            textFieldBringIntoViewRequester.bringIntoView()
            kotlinx.coroutines.delay(40)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
                onFocused()
            } catch (e: Exception) {
                kotlinx.coroutines.delay(80)
                runCatching {
                    bringIntoViewRequester.bringIntoView()
                    textFieldBringIntoViewRequester.bringIntoView()
                    focusRequester.requestFocus()
                    keyboardController?.show()
                    onFocused()
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .background(
                color = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 2.dp)
    ) {
        // Drag Handle with Touch / Pointer Drag Gesture
        Box(
            modifier = Modifier
                .size(34.dp)
                .pointerInput(item.id) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffsetY = 0f
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } catch (_: Exception) {}
                        },
                        onDragEnd = {
                            isDragging = false
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount
                            if (dragOffsetY > rowThresholdPx) {
                                if (currentCanMoveDown) {
                                    currentOnMoveDown()
                                    dragOffsetY -= rowThresholdPx
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } catch (_: Exception) {}
                                }
                            } else if (dragOffsetY < -rowThresholdPx) {
                                if (currentCanMoveUp) {
                                    currentOnMoveUp()
                                    dragOffsetY += rowThresholdPx
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = if (isDragging) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Square Checkbox
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (item.isChecked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (item.isChecked) "Completed" else "Incomplete",
                tint = if (item.isChecked) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Checklist Text Field (Multi-line wrapping with full content display)
        BasicTextField(
            value = item.text,
            onValueChange = { newText ->
                if (newText.contains('\n')) {
                    val split = newText.split('\n', limit = 2)
                    onTextChange(split[0])
                    val nextItemText = if (split.size > 1) split[1] else ""
                    onEnterPressed(nextItemText)
                } else {
                    onTextChange(newText)
                }
                coroutineScope.launch {
                    textFieldBringIntoViewRequester.bringIntoView()
                }
            },
            singleLine = false,
            maxLines = 20,
            modifier = Modifier
                .weight(1f)
                .bringIntoViewRequester(textFieldBringIntoViewRequester)
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                            onEnterPressed("")
                            true
                        } else if (keyEvent.key == Key.Backspace && item.text.isEmpty()) {
                            onDelete()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                color = if (item.isChecked) textColor.copy(alpha = 0.45f) else textColor,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                fontFamily = fontFamily
            ),
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { onEnterPressed("") },
                onDone = { onEnterPressed("") }
            ),
            decorationBox = { innerTextField ->
                if (item.text.isEmpty()) {
                    Text(
                        text = "List item",
                        style = TextStyle(
                            color = textColor.copy(alpha = 0.35f),
                            fontSize = 16.sp,
                            fontFamily = fontFamily
                        )
                    )
                }
                innerTextField()
            }
        )

        // Delete 'x' icon
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete item",
                tint = textColor.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}



@Composable
@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ZoomableImagePager(
    imageUris: List<String>,
    initialUri: String,
    onDismiss: () -> Unit,
    bottomInset: Dp = 0.dp
) {
    val initialPage = remember(initialUri, imageUris) {
        imageUris.indexOf(initialUri).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { imageUris.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var isZooming by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZooming
            ) { page ->
                val uri = imageUris.getOrNull(page) ?: return@HorizontalPager
                
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != page) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
                
                LaunchedEffect(scale) {
                    if (pagerState.currentPage == page) {
                        isZooming = scale > 1.05f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .then(
                            if (scale > 1.05f) {
                                Modifier.pointerInput(scale) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val oldScale = scale
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            val fractionalX = (centroid.x - offset.x) / oldScale
                                            val fractionalY = (centroid.y - offset.y) / oldScale
                                            var newOffsetX = centroid.x - (fractionalX * scale)
                                            var newOffsetY = centroid.y - (fractionalY * scale)
                                            newOffsetX += pan.x
                                            newOffsetY += pan.y
                                            
                                            val maxX = (size.width.toFloat() * scale - size.width.toFloat()) / 2f
                                            val maxY = (size.height.toFloat() * scale - size.height.toFloat()) / 2f
                                            offset = Offset(
                                                newOffsetX.coerceIn(-maxX, maxX),
                                                newOffsetY.coerceIn(-maxY, maxY)
                                            )
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        val newOffsetX = -(tapOffset.x * 2.5f - size.width.toFloat() / 2f)
                                        val newOffsetY = -(tapOffset.y * 2.5f - size.height.toFloat() / 2f)
                                        val maxX = (size.width.toFloat() * 2.5f - size.width.toFloat()) / 2f
                                        val maxY = (size.height.toFloat() * 2.5f - size.height.toFloat()) / 2f
                                        offset = Offset(
                                            newOffsetX.coerceIn(-maxX, maxX),
                                            newOffsetY.coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Full view image",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Bottom Thumbnail Gallery Strip (Quick Jump)
            if (imageUris.size > 1) {
                val effectiveBottomPadding = maxOf(bottomInset, 28.dp) + 24.dp
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = effectiveBottomPadding)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(imageUris) { index, uri ->
                        val isSelected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        ) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Thumbnail ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${imageUris.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close preview",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
