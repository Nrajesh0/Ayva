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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
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
    onAddDrawing: (Bitmap) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onToggleLabel: (String) -> Unit,
    onCreateAndAddLabel: (String) -> Unit,
    onArchive: () -> Unit,
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

    val isDark = isSystemInDarkTheme()
    val theme = KeepColorPalette.getColor(state.colorKey)
    val fontStyle = KeepFontPalette.getFont(state.fontKey)
    val bgColor = theme.resolveBackgroundColor(isDark)
    val textColor = theme.resolveTextColor(isDark)
    val borderColor = theme.resolveBorderColor(isDark)

    var showColorPicker by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
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

    val initialParsed = remember(state.originalId) { RichTextEngine.parse(state.content) }
    var richSpans by remember(state.originalId) { mutableStateOf(initialParsed.second) }
    var pendingTypingStyles by remember { mutableStateOf(setOf<RichSpanType>()) }

    val contentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    fun safeRequestFocus() {
        coroutineScope.launch {
            kotlinx.coroutines.delay(120)
            try {
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    var contentTfv by remember(state.originalId) {
        mutableStateOf(
            TextFieldValue(
                text = initialParsed.first,
                selection = TextRange(initialParsed.first.length)
            )
        )
    }

    var blocks by remember(state.originalId) {
        mutableStateOf(NotesnookBlockManager.parse(state.content))
    }

    var activeBlockIndex by remember(state.originalId) { mutableIntStateOf(0) }
    val textBlockStates = remember(state.originalId) { mutableStateMapOf<String, TextFieldValue>() }

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
        if (newBlocks.size == 1 && newBlocks[0] is NotesnookBlock.Text && (newBlocks[0] as NotesnookBlock.Text).text.isBlank()) {
            newBlocks.clear()
        } else if (newBlocks.size == 1 && newBlocks[0] is NotesnookBlock.Text) {
            newBlocks[0] = NotesnookBlock.Text(text = contentTfv.text, spans = richSpans)
        }
        val insertedIdx = newBlocks.size
        newBlocks.add(blockToInsert)
        val followingText = NotesnookBlock.Text()
        newBlocks.add(followingText)
        activeBlockIndex = insertedIdx + 1
        syncAndCommitBlocks(newBlocks)
        showNotesnookInsertSheet = false
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
            showFontPicker -> showFontPicker = false
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
                onAddImageUri(uri)
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

                        // Archive
                        IconButton(
                            onClick = onArchive,
                            modifier = Modifier.testTag("editor_archive_button")
                        ) {
                            Icon(
                                imageVector = if (state.isArchived) Icons.Filled.Archive else Icons.Outlined.Archive,
                                contentDescription = "Archive",
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
                                if (oldText != newText) {
                                    val updatedSpans = RichTextEngine.updateSpansOnTextChange(
                                        oldText = oldText,
                                        newText = newText,
                                        spans = richSpans,
                                        pendingTypes = pendingTypingStyles
                                    )
                                    richSpans = updatedSpans
                                    val updatedBlock = NotesnookBlock.Text(text = newText, spans = updatedSpans)
                                    blocks = listOf(updatedBlock)
                                    onContentChange(NotesnookBlockManager.serialize(blocks))
                                }
                                contentTfv = effectiveTfv
                                coroutineScope.launch {
                                    contentBringIntoViewRequester.bringIntoView()
                                }
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
                                fontFamily = fontStyle.fontFamily
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
                                                if (oldT != newT) {
                                                    val updatedS = RichTextEngine.updateSpansOnTextChange(oldT, newT, block.spans)
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
                                                },
                                            visualTransformation = textVisualTrans,
                                            textStyle = TextStyle(
                                                color = textColor,
                                                fontSize = fontSizeSp.sp,
                                                lineHeight = lineHeightSp.sp,
                                                fontFamily = fontStyle.fontFamily
                                            ),
                                            cursorBrush = SolidColor(textColor),
                                            decorationBox = { innerTextField ->
                                                if (block.text.isEmpty()) {
                                                    Text(
                                                        text = if (index == 0) "Note" else "Continue writing...",
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
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[index] = block
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
                            val newSpans = if (level == 0) {
                                val lineStart = curTfv.text.lastIndexOf('\n', startIndex = maxOf(0, cursor - 1)).let { if (it == -1) 0 else it + 1 }
                                val lineEnd = curTfv.text.indexOf('\n', startIndex = cursor).let { if (it == -1) curTfv.text.length else it }
                                curSpans.filterNot {
                                    it.start >= lineStart && it.end <= lineEnd &&
                                            (it.type == RichSpanType.HEADING_1 || it.type == RichSpanType.HEADING_2 ||
                                                    it.type == RichSpanType.HEADING_3 || it.type == RichSpanType.HEADING_4 ||
                                                    it.type == RichSpanType.HEADING_5 || it.type == RichSpanType.HEADING_6)
                                }
                            } else {
                                val type = when (level) {
                                    1 -> RichSpanType.HEADING_1
                                    2 -> RichSpanType.HEADING_2
                                    3 -> RichSpanType.HEADING_3
                                    4 -> RichSpanType.HEADING_4
                                    5 -> RichSpanType.HEADING_5
                                    else -> RichSpanType.HEADING_6
                                }
                                RichTextEngine.toggleLineStyle(curSpans, type, cursor, curTfv.text)
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
                            updateActiveTextState(idx, curTfv, newSpans)
                        }
                        keepFocus()
                    },
                    selectedFontKey = state.fontKey,
                    onFontChange = onFontChange,
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

            // ==========================================
            // BOTTOM TOOLBAR (Authentic Google Keep Layout)
            // ==========================================
            if (!isZenMode) {
                Surface(
                    color = bgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left tools: [+] Add sheet, [Palette] Color, and [Font] Typography
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showAddSheet = true },
                                modifier = Modifier.testTag("editor_plus_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddBox,
                                    contentDescription = "Add options",
                                    tint = textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    showColorPicker = !showColorPicker
                                    if (showColorPicker) showFontPicker = false
                                },
                                modifier = Modifier.testTag("editor_palette_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = "Color palette",
                                    tint = if (showColorPicker) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    showFontPicker = !showFontPicker
                                    if (showFontPicker) showColorPicker = false
                                },
                                modifier = Modifier.testTag("editor_font_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (showFontPicker) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A",
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (showFontPicker) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.85f)
                                        )
                                    )
                                }
                            }
                        }

                        // Center: Word Count & Edited Time Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = textColor.copy(alpha = 0.07f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDocumentStatsSheet = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("editor_document_stats_pill")
                        ) {
                            Text(
                                text = if (documentMetrics.words > 0) {
                                    "${documentMetrics.words} words · ~${if (documentMetrics.readingTimeMinutes <= 1) 1 else documentMetrics.readingTimeMinutes} min"
                                } else {
                                    "Edited $formattedTime"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                                color = textColor.copy(alpha = 0.8f)
                            )
                        }

                        // Right action: Undo, Redo, 3-dots Overflow Menu
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onUndo,
                                enabled = canUndo,
                                modifier = Modifier.size(36.dp).testTag("editor_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) textColor else textColor.copy(alpha = 0.28f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onRedo,
                                enabled = canRedo,
                                modifier = Modifier.size(36.dp).testTag("editor_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) textColor else textColor.copy(alpha = 0.28f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.size(36.dp).testTag("editor_more_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                    tint = textColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // GOOGLE KEEP "MORE" 3-DOT OVERFLOW SHEET
        // ==========================================
        if (showMoreMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMoreMenu = false },
                containerColor = if (isDark) Color(0xFF101012) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp, top = 4.dp)
                ) {
                    KeepAddOptionRow(
                        icon = Icons.Outlined.FormatListNumbered,
                        title = "Table of contents",
                        onClick = {
                            showMoreMenu = false
                            showTocSheet = true
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Publish,
                        title = "Export & Publish (MD / HTML)",
                        onClick = {
                            showMoreMenu = false
                            showExportSheet = true
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.FormatLineSpacing,
                        title = "Editorial typography & elements",
                        onClick = {
                            showMoreMenu = false
                            showEditorialSheet = true
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Fullscreen,
                        title = "Zen Focus mode",
                        onClick = {
                            showMoreMenu = false
                            isZenMode = true
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Article,
                        title = "Document statistics",
                        onClick = {
                            showMoreMenu = false
                            showDocumentStatsSheet = true
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Delete,
                        title = "Delete",
                        onClick = {
                            showMoreMenu = false
                            onDelete()
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.ContentCopy,
                        title = "Make a copy",
                        onClick = {
                            showMoreMenu = false
                            onDuplicate()
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Share,
                        title = "Send",
                        onClick = {
                            showMoreMenu = false
                            onShare()
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.ContentCopy,
                        title = "Copy text",
                        onClick = {
                            showMoreMenu = false
                            onCopyText()
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.FontDownload,
                        title = "Font style",
                        onClick = {
                            showMoreMenu = false
                            showFontPicker = true
                            showColorPicker = false
                        }
                    )

                    KeepAddOptionRow(
                        icon = Icons.Outlined.Label,
                        title = "Labels",
                        onClick = {
                            showMoreMenu = false
                            showLabelDialog = true
                        }
                    )
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
                        title = "Recording",
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
