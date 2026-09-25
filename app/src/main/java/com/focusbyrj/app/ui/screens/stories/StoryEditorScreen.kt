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

package com.focusbyrj.app.ui.screens.stories

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.focusbyrj.app.ui.screens.notes.NotesnookBlock
import com.focusbyrj.app.ui.screens.notes.NotesnookBlockManager
import com.focusbyrj.app.ui.screens.notes.NotesnookCalloutWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookCodeBlockWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookFormattingHelper
import com.focusbyrj.app.ui.screens.notes.NotesnookHorizontalRuleWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookImageBlockWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookMathWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookQuoteWidget
import com.focusbyrj.app.ui.screens.notes.NotesnookTableWidget
import com.focusbyrj.app.ui.screens.notes.RichSpan
import com.focusbyrj.app.ui.screens.notes.RichSpanType
import com.focusbyrj.app.ui.screens.notes.RichTextEngine
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun StoryEditorScreen(
    state: EditingStoryState,
    onTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSetCoverImage: (Uri?) -> Unit,
    onRemoveCoverImage: () -> Unit,
    onSetFont: (String) -> Unit,
    onPublish: () -> Unit,
    onUnpublish: () -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val bgColor = if (isDark) Color(0xFF0D1117) else Color(0xFFFAFAF9)
    val textColor = if (isDark) Color(0xFFE6EDF3) else Color(0xFF1C1917)
    val subtitleColor = if (isDark) Color(0xFF8B949E) else Color(0xFF78716C)
    val accentGreen = Color(0xFF10B981)

    val isSerif = state.fontKey == "serif"
    val editorFont = if (isSerif) FontFamily.Serif else FontFamily.Default

    val initialParsed = remember(state.sessionId) { RichTextEngine.parse(state.content) }
    var richSpans by remember(state.sessionId) { mutableStateOf(initialParsed.second) }
    var pendingTypingStyles by remember { mutableStateOf(setOf<RichSpanType>()) }

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
            val id = blocks.firstOrNull()?.id ?: UUID.randomUUID().toString()
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

    fun insertBlockItem(blockToInsert: NotesnookBlock) {
        val newBlocks = blocks.toMutableList()
        if (newBlocks.size == 1 && newBlocks[0] is NotesnookBlock.Text) {
            if (contentTfv.text.isBlank()) {
                newBlocks.clear()
            } else {
                newBlocks[0] = NotesnookBlock.Text(text = contentTfv.text, spans = richSpans)
            }
        }

        val currentIdx = activeBlockIndex.coerceIn(0, newBlocks.size)
        if (currentIdx < newBlocks.size && newBlocks[currentIdx] is NotesnookBlock.Text && (newBlocks[currentIdx] as NotesnookBlock.Text).text.isBlank()) {
            newBlocks[currentIdx] = blockToInsert
            val nextIdx = currentIdx + 1
            if (nextIdx >= newBlocks.size || newBlocks[nextIdx] !is NotesnookBlock.Text) {
                newBlocks.add(nextIdx, NotesnookBlock.Text())
            }
            activeBlockIndex = nextIdx
        } else {
            val insertAt = if (newBlocks.isEmpty()) 0 else (currentIdx + 1).coerceAtMost(newBlocks.size)
            newBlocks.add(insertAt, blockToInsert)
            val nextIdx = insertAt + 1
            if (nextIdx >= newBlocks.size || newBlocks[nextIdx] !is NotesnookBlock.Text) {
                newBlocks.add(nextIdx, NotesnookBlock.Text())
            }
            activeBlockIndex = nextIdx
        }
        syncAndCommitBlocks(newBlocks)
    }

    // Photo picker for cover image
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onSetCoverImage(uri)
            }
        }
    )

    // Photo picker for inline images
    val inlineImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                insertBlockItem(NotesnookBlock.Image(uri = uri.toString(), caption = ""))
            }
        }
    )

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("story_editor_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP EDITORIAL TOOLBAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back & Auto-save status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("story_editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and Close",
                            tint = textColor
                        )
                    }

                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = subtitleColor.copy(alpha = 0.65f)
                    )
                }

                // Right Actions: Font toggle, Reader Preview, Publish button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Typography toggle (Serif vs Sans)
                    OutlinedButton(
                        onClick = { onSetFont(if (isSerif) "default" else "serif") },
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, subtitleColor.copy(alpha = 0.3f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(
                            text = if (isSerif) "Serif" else "Sans",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default
                            ),
                            color = textColor
                        )
                    }

                    // Preview in Reader View
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier.size(36.dp).testTag("story_editor_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Preview in Reader",
                            tint = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Publish Button
                    Button(
                        onClick = if (state.isPublished) onUnpublish else onPublish,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("story_publish_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isPublished) Color(0xFF1E293B) else accentGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isPublished) Icons.Default.Check else Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (state.isPublished) "Published" else "Publish",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // SCROLLABLE ARTICLE CANVAS (Centered Medium Layout)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(bottom = 72.dp)
                ) {
                    // HERO COVER BANNER
                    if (!state.coverImageUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(state.coverImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Story Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Action buttons overlay
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            coverPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Change Cover",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onRemoveCoverImage() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Cover",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // "Add Cover Image" Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    coverPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = subtitleColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Add Cover Photo",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = subtitleColor
                                )
                            }
                        }
                    }

                    // ARTICLE HEADER (Title & Subtitle)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 12.dp)
                    ) {
                        // Title Input
                        BasicTextField(
                            value = state.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("story_title_input"),
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 38.sp,
                                fontFamily = editorFont
                            ),
                            cursorBrush = SolidColor(textColor),
                            decorationBox = { innerTextField ->
                                if (state.title.isEmpty()) {
                                    Text(
                                        text = "Title",
                                        style = TextStyle(
                                            color = textColor.copy(alpha = 0.35f),
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = editorFont
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Subtitle Input
                        BasicTextField(
                            value = state.subtitle,
                            onValueChange = onSubtitleChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("story_subtitle_input"),
                            textStyle = TextStyle(
                                color = subtitleColor,
                                fontSize = 17.sp,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 24.sp
                            ),
                            cursorBrush = SolidColor(textColor),
                            decorationBox = { innerTextField ->
                                if (state.subtitle.isEmpty()) {
                                    Text(
                                        text = "Add a subtitle...",
                                        style = TextStyle(
                                            color = subtitleColor.copy(alpha = 0.45f),
                                            fontSize = 17.sp,
                                            fontStyle = FontStyle.Italic
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Reading Stats Pill
                        val wordCount = state.content.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${state.readingTimeMinutes.coerceAtLeast(1)} min read",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = subtitleColor.copy(alpha = 0.75f)
                            )
                            Text(text = "•", color = subtitleColor.copy(alpha = 0.5f), fontSize = 12.sp)
                            Text(
                                text = "$wordCount words",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = subtitleColor.copy(alpha = 0.75f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = subtitleColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // ARTICLE BODY INPUT
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        if (blocks.size <= 1 && (blocks.isEmpty() || blocks[0] is NotesnookBlock.Text)) {
                            val richVisualTransformation = remember(richSpans, textColor, isDark) {
                                RichTextEngine.createVisualTransformation(
                                    spans = richSpans,
                                    textColor = textColor,
                                    accentColor = accentGreen,
                                    isDark = isDark,
                                    baseFontSizeSp = 17.5f
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
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 400.dp)
                                    .testTag("story_content_input"),
                                visualTransformation = richVisualTransformation,
                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = 17.5.sp,
                                    lineHeight = 28.sp,
                                    fontFamily = editorFont
                                ),
                                cursorBrush = SolidColor(textColor),
                                decorationBox = { innerTextField ->
                                    if (contentTfv.text.isEmpty()) {
                                        Text(
                                            text = "Tell your story...",
                                            style = TextStyle(
                                                color = textColor.copy(alpha = 0.35f),
                                                fontSize = 17.5.sp,
                                                lineHeight = 28.sp,
                                                fontFamily = editorFont
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        } else {
                            // Multi-block editor
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                blocks.forEachIndexed { index, block ->
                                    when (block) {
                                        is NotesnookBlock.Text -> {
                                            val blockId = block.id
                                            var textVal by remember(blockId) {
                                                val init = textBlockStates[blockId] ?: TextFieldValue(block.text, TextRange(block.text.length))
                                                textBlockStates[blockId] = init
                                                mutableStateOf(init)
                                            }
                                            if (textVal.text != block.text) {
                                                val safe = textVal.selection.start.coerceIn(0, block.text.length)
                                                textVal = textVal.copy(text = block.text, selection = TextRange(safe))
                                                textBlockStates[blockId] = textVal
                                            }
                                            val textVisual = remember(block.spans, textColor, isDark) {
                                                RichTextEngine.createVisualTransformation(
                                                    spans = block.spans,
                                                    textColor = textColor,
                                                    accentColor = accentGreen,
                                                    isDark = isDark,
                                                    baseFontSizeSp = 17.5f
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
                                                        val updatedB = block.copy(text = newT, spans = updatedS)
                                                        val newBlocks = blocks.toMutableList()
                                                        newBlocks[index] = updatedB
                                                        syncAndCommitBlocks(newBlocks)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .onFocusChanged { if (it.isFocused) activeBlockIndex = index },
                                                visualTransformation = textVisual,
                                                textStyle = TextStyle(
                                                    color = textColor,
                                                    fontSize = 17.5.sp,
                                                    lineHeight = 28.sp,
                                                    fontFamily = editorFont
                                                ),
                                                cursorBrush = SolidColor(textColor),
                                                decorationBox = { innerTextField ->
                                                    if (block.text.isEmpty() && index == 0) {
                                                        Text(
                                                            text = "Tell your story...",
                                                            style = TextStyle(
                                                                color = textColor.copy(alpha = 0.35f),
                                                                fontSize = 17.5.sp,
                                                                fontFamily = editorFont
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
                                                    val newB = blocks.toMutableList()
                                                    newB[index] = block.copy(
                                                        rows = block.rows,
                                                        cols = block.cols,
                                                        data = block.data.map { it.toMutableList() }.toMutableList(),
                                                        columnWidths = block.columnWidths.toMutableList()
                                                    )
                                                    syncAndCommitBlocks(newB)
                                                },
                                                onDelete = {
                                                    val newB = blocks.filterIndexed { i, _ -> i != index }
                                                    syncAndCommitBlocks(if (newB.isEmpty()) listOf(NotesnookBlock.Text()) else newB)
                                                },
                                                isDark = isDark,
                                                textColor = textColor
                                            )
                                        }
                                        is NotesnookBlock.Code -> {
                                            NotesnookCodeBlockWidget(
                                                block = block,
                                                onUpdate = {
                                                    val newB = blocks.toMutableList()
                                                    newB[index] = block
                                                    syncAndCommitBlocks(newB)
                                                },
                                                onDelete = {
                                                    val newB = blocks.filterIndexed { i, _ -> i != index }
                                                    syncAndCommitBlocks(if (newB.isEmpty()) listOf(NotesnookBlock.Text()) else newB)
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        is NotesnookBlock.Callout -> {
                                            NotesnookCalloutWidget(
                                                block = block,
                                                onUpdate = {
                                                    val newB = blocks.toMutableList()
                                                    newB[index] = block
                                                    syncAndCommitBlocks(newB)
                                                },
                                                onDelete = {
                                                    val newB = blocks.filterIndexed { i, _ -> i != index }
                                                    syncAndCommitBlocks(if (newB.isEmpty()) listOf(NotesnookBlock.Text()) else newB)
                                                },
                                                isDark = isDark,
                                                noteTextColor = textColor
                                            )
                                        }
                                        is NotesnookBlock.Quote -> {
                                            NotesnookQuoteWidget(
                                                block = block,
                                                onUpdate = {
                                                    val newB = blocks.toMutableList()
                                                    newB[index] = block
                                                    syncAndCommitBlocks(newB)
                                                },
                                                onDelete = {
                                                    val newB = blocks.filterIndexed { i, _ -> i != index }
                                                    syncAndCommitBlocks(if (newB.isEmpty()) listOf(NotesnookBlock.Text()) else newB)
                                                },
                                                isDark = isDark,
                                                noteTextColor = textColor
                                            )
                                        }
                                        is NotesnookBlock.HorizontalRule -> {
                                            NotesnookHorizontalRuleWidget(
                                                block = block,
                                                onDelete = {
                                                    val newB = blocks.filterIndexed { i, _ -> i != index }
                                                    syncAndCommitBlocks(if (newB.isEmpty()) listOf(NotesnookBlock.Text()) else newB)
                                                },
                                                isDark = isDark
                                            )
                                        }
                                        is NotesnookBlock.Image -> {
                                            NotesnookImageBlockWidget(
                                                block = block,
                                                onUpdate = {
                                                    val newB = blocks.toMutableList()
                                                    newB[index] = block
                                                    syncAndCommitBlocks(newB)
                                                },
                                                onDelete = {
                                                    val newB = blocks.filterIndexed { i, _ -> i != index }
                                                    syncAndCommitBlocks(if (newB.isEmpty()) listOf(NotesnookBlock.Text()) else newB)
                                                },
                                                onClick = {},
                                                isDark = isDark,
                                                noteTextColor = textColor
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CONTEXTUAL FORMATTING BAR (Pinned directly above the soft keyboard)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = if (isDark) Color(0xFF161A22) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF30363D) else Color(0x33000000))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Heading 1
                    EditorToolbarItem(
                        label = "H1",
                        onClick = {
                            val active = getActiveTextState() ?: return@EditorToolbarItem
                            val (idx, curTfv, curSpans) = active
                            val newSpans = RichTextEngine.toggleLineStyle(curSpans, RichSpanType.HEADING_1, curTfv.selection.start, curTfv.text)
                            updateActiveTextState(idx, curTfv, newSpans)
                        }
                    )

                    // Heading 2
                    EditorToolbarItem(
                        label = "H2",
                        onClick = {
                            val active = getActiveTextState() ?: return@EditorToolbarItem
                            val (idx, curTfv, curSpans) = active
                            val newSpans = RichTextEngine.toggleLineStyle(curSpans, RichSpanType.HEADING_2, curTfv.selection.start, curTfv.text)
                            updateActiveTextState(idx, curTfv, newSpans)
                        }
                    )

                    // Bold
                    EditorToolbarIconButton(
                        icon = Icons.Default.FormatBold,
                        desc = "Bold",
                        onClick = {
                            val active = getActiveTextState() ?: return@EditorToolbarIconButton
                            val (idx, curTfv, curSpans) = active
                            val sel = curTfv.selection
                            if (sel.start != sel.end) {
                                val newSpans = RichTextEngine.toggleSpan(curSpans, RichSpanType.BOLD, sel, curTfv.text.length)
                                updateActiveTextState(idx, curTfv, newSpans)
                            }
                        }
                    )

                    // Italic
                    EditorToolbarIconButton(
                        icon = Icons.Default.FormatItalic,
                        desc = "Italic",
                        onClick = {
                            val active = getActiveTextState() ?: return@EditorToolbarIconButton
                            val (idx, curTfv, curSpans) = active
                            val sel = curTfv.selection
                            if (sel.start != sel.end) {
                                val newSpans = RichTextEngine.toggleSpan(curSpans, RichSpanType.ITALIC, sel, curTfv.text.length)
                                updateActiveTextState(idx, curTfv, newSpans)
                            }
                        }
                    )

                    // Blockquote
                    EditorToolbarIconButton(
                        icon = Icons.Default.FormatQuote,
                        desc = "Quote",
                        onClick = {
                            insertBlockItem(NotesnookBlock.Quote(text = ""))
                        }
                    )

                    // Bullet List
                    EditorToolbarIconButton(
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        desc = "Bullet List",
                        onClick = {
                            val active = getActiveTextState() ?: return@EditorToolbarIconButton
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.applyLinePrefix(curTfv, "- ")
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                    )

                    // Numbered List
                    EditorToolbarIconButton(
                        icon = Icons.Default.FormatListNumbered,
                        desc = "Numbered List",
                        onClick = {
                            val active = getActiveTextState() ?: return@EditorToolbarIconButton
                            val (idx, curTfv, curSpans) = active
                            val newTfv = NotesnookFormattingHelper.applyLinePrefix(curTfv, "1. ")
                            val updatedSpans = RichTextEngine.updateSpansOnTextChange(curTfv.text, newTfv.text, curSpans)
                            updateActiveTextState(idx, newTfv, updatedSpans)
                        }
                    )

                    // Code Block
                    EditorToolbarIconButton(
                        icon = Icons.Default.Code,
                        desc = "Code Block",
                        onClick = {
                            insertBlockItem(NotesnookBlock.Code(language = "Kotlin", code = ""))
                        }
                    )

                    // Callout
                    EditorToolbarIconButton(
                        icon = Icons.Default.Lightbulb,
                        desc = "Callout",
                        onClick = {
                            insertBlockItem(NotesnookBlock.Callout(calloutType = "note", text = ""))
                        }
                    )

                    // Table
                    EditorToolbarIconButton(
                        icon = Icons.Default.TableChart,
                        desc = "Table",
                        onClick = {
                            insertBlockItem(NotesnookBlock.Table(rows = 3, cols = 3))
                        }
                    )

                    // Inline Image
                    EditorToolbarIconButton(
                        icon = Icons.Default.Image,
                        desc = "Add Inline Image",
                        onClick = {
                            inlineImagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    // Divider
                    EditorToolbarIconButton(
                        icon = Icons.Default.HorizontalRule,
                        desc = "Divider",
                        onClick = {
                            insertBlockItem(NotesnookBlock.HorizontalRule())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorToolbarIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun EditorToolbarItem(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}
