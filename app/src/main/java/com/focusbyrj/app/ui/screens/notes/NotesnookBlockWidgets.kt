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

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ==========================================
// 1. NOTESNOOK CODE BLOCK WIDGET
// ==========================================
@Composable
fun NotesnookCodeBlockWidget(
    block: NotesnookBlock.Code,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var showLangMenu by remember { mutableStateOf(false) }

    var codeValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.code, TextRange(block.code.length)))
    }

    LaunchedEffect(block.code) {
        if (block.code != codeValue.text) {
            codeValue = TextFieldValue(block.code, TextRange(block.code.length))
        }
    }

    val languages = listOf(
        "Kotlin", "Java", "Python", "JavaScript", "TypeScript",
        "HTML", "CSS", "SQL", "JSON", "C++", "Go", "Rust",
        "Bash", "Markdown", "Plain Text"
    )

    val blockBg = if (isDark) Color(0xFF14161A) else Color(0xFF1E2126)
    val headerBg = if (isDark) Color(0xFF1C1F24) else Color(0xFF282C34)
    val textColor = Color(0xFFABB2BF)
    val codeTextColor = if (isDark) Color(0xFFE5E7EB) else Color(0xFFE5E7EB)
    val borderColor = if (isDark) Color(0xFF2B313A) else Color(0xFF3B4048)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(blockBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .testTag("code_block_${block.id}")
    ) {
        // Code Block Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Language selector pill
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showLangMenu = true }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = block.language,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF61AFEF)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Select language",
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showLangMenu,
                    onDismissRequest = { showLangMenu = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, style = TextStyle(fontSize = 13.sp)) },
                            onClick = {
                                block.language = lang
                                showLangMenu = false
                                onUpdate()
                            }
                        )
                    }
                }
            }

            // Right actions: Copy only (deletion handled cleanly via Backspace when empty, like Notesnook)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(codeValue.text))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp).testTag("copy_code_${block.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        tint = textColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Code Editor Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                }
                .padding(14.dp)
        ) {
            BasicTextField(
                value = codeValue,
                onValueChange = { newTfv ->
                    codeValue = newTfv
                    if (block.code != newTfv.text) {
                        block.code = newTfv.text
                        onUpdate()
                    }
                },
                textStyle = TextStyle(
                    color = codeTextColor,
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color(0xFF528BFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace && codeValue.text.isEmpty()) {
                            onDelete()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("code_input_${block.id}"),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (codeValue.text.isEmpty()) {
                            Text(
                                text = "// Type or paste code here...",
                                style = TextStyle(
                                    color = textColor.copy(alpha = 0.45f),
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

// ==========================================
// 2. NOTESNOOK CALLOUT WIDGET
// ==========================================
@Composable
fun NotesnookCalloutWidget(
    block: NotesnookBlock.Callout,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    var showTypePicker by remember { mutableStateOf(false) }

    data class CalloutStyle(
        val type: String,
        val label: String,
        val emoji: String,
        val accentColor: Color,
        val darkBg: Color,
        val lightBg: Color
    )

    val styles = listOf(
        CalloutStyle("note", "Note", "💡", Color(0xFF0284C7), Color(0x1F0284C7), Color(0x140284C7)),
        CalloutStyle("warning", "Warning", "⚠️", Color(0xFFD97706), Color(0x1FD97706), Color(0x14D97706)),
        CalloutStyle("important", "Important", "📌", Color(0xFFDC2626), Color(0x1FDC2626), Color(0x14DC2626)),
        CalloutStyle("tip", "Tip", "🚀", Color(0xFF16A34A), Color(0x1F16A34A), Color(0x1416A34A)),
        CalloutStyle("info", "Info", "ℹ️", Color(0xFF9333EA), Color(0x1F9333EA), Color(0x149333EA)),
        CalloutStyle("success", "Success", "✅", Color(0xFF059669), Color(0x1F059669), Color(0x14059669))
    )

    val currentStyle = styles.firstOrNull { it.type.equals(block.calloutType, ignoreCase = true) }
        ?: styles[0]

    val containerBg = if (isDark) currentStyle.darkBg else currentStyle.lightBg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(
                width = 1.dp,
                color = currentStyle.accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .testTag("callout_block_${block.id}"),
        verticalAlignment = Alignment.Top
    ) {
        // Left accent indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 52.dp)
                .background(currentStyle.accentColor)
        )

        // Emoji & Type Switcher
        Box(
            modifier = Modifier.padding(top = 10.dp, start = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { showTypePicker = true }
                    .background(currentStyle.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = currentStyle.emoji, fontSize = 16.sp)
            }

            DropdownMenu(
                expanded = showTypePicker,
                onDismissRequest = { showTypePicker = false }
            ) {
                styles.forEach { style ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(style.emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(style.label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            }
                        },
                        onClick = {
                            block.calloutType = style.type
                            showTypePicker = false
                            onUpdate()
                        }
                    )
                }
            }
        }

        // Callout text editor
        var calloutValue by remember(block.id) {
            mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length)))
        }

        LaunchedEffect(block.text) {
            if (block.text != calloutValue.text) {
                calloutValue = TextFieldValue(block.text, TextRange(block.text.length))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = calloutValue,
                onValueChange = { newTfv ->
                    calloutValue = newTfv
                    if (block.text != newTfv.text) {
                        block.text = newTfv.text
                        onUpdate()
                    }
                },
                textStyle = TextStyle(
                    color = noteTextColor,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(currentStyle.accentColor),
                modifier = Modifier.fillMaxWidth().testTag("callout_input_${block.id}"),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (calloutValue.text.isEmpty()) {
                            Text(
                                text = "${currentStyle.label} message...",
                                style = TextStyle(
                                    color = noteTextColor.copy(alpha = 0.45f),
                                    fontSize = 14.5.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.padding(top = 6.dp, end = 4.dp).size(28.dp).testTag("delete_callout_${block.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete callout",
                tint = noteTextColor.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// 3. NOTESNOOK HORIZONTAL RULE WIDGET
// ==========================================
@Composable
fun NotesnookHorizontalRuleWidget(
    block: NotesnookBlock.HorizontalRule,
    onDelete: () -> Unit,
    isDark: Boolean
) {
    var isHovered by remember { mutableStateOf(false) }
    val dividerColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { isHovered = !isHovered }
            .testTag("hr_block_${block.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = dividerColor
        )

        // Subtle quick delete icon on tap
        if (isHovered) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 6.dp)
                    .testTag("delete_hr_${block.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove horizontal line",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ==========================================
// 4. NOTESNOOK MATH & FORMULA WIDGET
// ==========================================
@Composable
fun NotesnookMathWidget(
    block: NotesnookBlock.MathFormula,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    var isEditing by remember { mutableStateOf(false) }

    var formulaValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.formula, TextRange(block.formula.length)))
    }

    LaunchedEffect(block.formula) {
        if (block.formula != formulaValue.text) {
            formulaValue = TextFieldValue(block.formula, TextRange(block.formula.length))
        }
    }

    // Notesnook Authentic Math Formula styling (dark obsidian container or crisp light container)
    val bg = if (isDark) Color(0xFF13161C) else Color(0xFFF8FAFC)
    val containerBorder = if (isDark) Color(0xFF262B35) else Color(0xFFE2E8F0)
    val accentColor = Color(0xFF22C55E)
    val mathSymbols = listOf("±", "²", "³", "√", "∫", "∑", "π", "∞", "θ", "α", "β", "÷", "≠", "≤", "≥", "≈", "∈", "∀", "∃")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                1.dp,
                if (isEditing) accentColor.copy(alpha = 0.8f) else containerBorder,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("math_block_${block.id}")
    ) {
        // Header badge row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isDark) Color(0x1F22C55E) else Color(0x1422C55E))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Functions,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "KaTeX Math",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                    color = accentColor
                )
            }

            // Edit toggle action (no delete icon — backspace deletes when empty, just like Notesnook)
            IconButton(
                onClick = { isEditing = !isEditing },
                modifier = Modifier.size(28.dp).testTag("edit_math_${block.id}")
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isEditing) "Done editing" else "Edit formula",
                    tint = if (isEditing) accentColor else noteTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isEditing) {
            // KaTeX expression input field
            BasicTextField(
                value = formulaValue,
                onValueChange = { newTfv ->
                    formulaValue = newTfv
                    if (block.formula != newTfv.text) {
                        block.formula = newTfv.text
                        onUpdate()
                    }
                },
                textStyle = TextStyle(
                    color = if (isDark) Color(0xFFF3F4F6) else Color(0xFF111827),
                    fontSize = 14.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Backspace && formulaValue.text.isEmpty()) {
                            onDelete()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("math_input_${block.id}"),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (formulaValue.text.isEmpty()) {
                            Text(
                                text = "Type math expression (e.g. E = mc² or \\frac{a}{b})...",
                                style = TextStyle(
                                    color = noteTextColor.copy(alpha = 0.45f),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Quick symbol chips bar
            val hScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mathSymbols.forEach { sym ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E222A) else Color(0xFFE2E8F0))
                            .clickable {
                                val updated = formulaValue.text + sym
                                formulaValue = TextFieldValue(updated, TextRange(updated.length))
                                block.formula = updated
                                onUpdate()
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = sym,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = noteTextColor
                        )
                    }
                }
            }
        } else {
            // Display beautifully formatted KaTeX / Mathematical formula display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isEditing = true }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formulaValue.text.ifBlank { "Tap to enter formula" },
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (formulaValue.text.isBlank()) noteTextColor.copy(alpha = 0.4f) else (if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827))
                )
            }
        }
    }
}

// ==========================================
// 5. NOTESNOOK QUOTE WIDGET
// ==========================================
@Composable
fun NotesnookQuoteWidget(
    block: NotesnookBlock.Quote,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    val accentColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("quote_block_${block.id}"),
        verticalAlignment = Alignment.Top
    ) {
        // Left thick accent bar
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        var quoteValue by remember(block.id) {
            mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length)))
        }

        LaunchedEffect(block.text) {
            if (block.text != quoteValue.text) {
                quoteValue = TextFieldValue(block.text, TextRange(block.text.length))
            }
        }

        BasicTextField(
            value = quoteValue,
            onValueChange = { newTfv ->
                quoteValue = newTfv
                if (block.text != newTfv.text) {
                    block.text = newTfv.text
                    onUpdate()
                }
            },
            textStyle = TextStyle(
                color = noteTextColor.copy(alpha = 0.9f),
                fontSize = 15.5.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 23.sp
            ),
            cursorBrush = SolidColor(accentColor),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .testTag("quote_input_${block.id}"),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (quoteValue.text.isEmpty()) {
                        Text(
                            text = "Quote...",
                            style = TextStyle(
                                color = noteTextColor.copy(alpha = 0.4f),
                                fontSize = 15.5.sp,
                                fontStyle = FontStyle.Italic
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp).testTag("delete_quote_${block.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete quote",
                tint = noteTextColor.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

// ==========================================
// 6. NOTESNOOK OUTLINE LIST ITEM WIDGET
// ==========================================
@Composable
fun NotesnookOutlineWidget(
    block: NotesnookBlock.OutlineItem,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    val indentPadding = (block.level.coerceIn(0, 4) * 20).dp
    val bulletColor = if (isDark) Color(0xFF22C55E) else Color(0xFF16A34A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding, top = 3.dp, bottom = 3.dp)
            .testTag("outline_block_${block.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Outline Tree / Bullet Icon
        Box(
            modifier = Modifier
                .size(18.dp)
                .clickable {
                    block.isCollapsed = !block.isCollapsed
                    onUpdate()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(bulletColor)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Outline Item Text
        var outlineValue by remember(block.id) {
            mutableStateOf(TextFieldValue(block.text, TextRange(block.text.length)))
        }

        LaunchedEffect(block.text) {
            if (block.text != outlineValue.text) {
                outlineValue = TextFieldValue(block.text, TextRange(block.text.length))
            }
        }

        BasicTextField(
            value = outlineValue,
            onValueChange = { newTfv ->
                outlineValue = newTfv
                if (block.text != newTfv.text) {
                    block.text = newTfv.text
                    onUpdate()
                }
            },
            textStyle = TextStyle(
                color = noteTextColor,
                fontSize = 14.5.sp,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(bulletColor),
            modifier = Modifier.weight(1f).testTag("outline_input_${block.id}"),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (outlineValue.text.isEmpty()) {
                        Text(
                            text = "List item...",
                            style = TextStyle(color = noteTextColor.copy(alpha = 0.4f), fontSize = 14.5.sp)
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Indent controls (< and >)
        IconButton(
            onClick = {
                if (block.level > 0) {
                    block.level -= 1
                    onUpdate()
                }
            },
            enabled = block.level > 0,
            modifier = Modifier.size(24.dp).testTag("outdent_${block.id}")
        ) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Outdent",
                tint = if (block.level > 0) noteTextColor.copy(alpha = 0.7f) else noteTextColor.copy(alpha = 0.2f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = {
                if (block.level < 4) {
                    block.level += 1
                    onUpdate()
                }
            },
            enabled = block.level < 4,
            modifier = Modifier.size(24.dp).testTag("indent_${block.id}")
        ) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Indent",
                tint = if (block.level < 4) noteTextColor.copy(alpha = 0.7f) else noteTextColor.copy(alpha = 0.2f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp).testTag("delete_outline_${block.id}")
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete item",
                tint = noteTextColor.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ==========================================
// 7. NOTESNOOK EMBED WIDGET
// ==========================================
@Composable
fun NotesnookEmbedWidget(
    block: NotesnookBlock.Embed,
    onDelete: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    val context = LocalContext.current
    val bg = if (isDark) Color(0xFF1E2128) else Color(0xFFF3F4F6)
    val borderCol = if (isDark) Color(0xFF2E3440) else Color(0xFFE5E7EB)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(block.url))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .padding(12.dp)
            .testTag("embed_block_${block.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.SmartDisplay,
            contentDescription = null,
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.title.ifBlank { block.url },
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = noteTextColor,
                maxLines = 1
            )
            Text(
                text = block.url,
                style = TextStyle(fontSize = 12.sp),
                color = noteTextColor.copy(alpha = 0.6f),
                maxLines = 1
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp).testTag("delete_embed_${block.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete embed",
                tint = noteTextColor.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// 8. NOTESNOOK ATTACHMENT WIDGET
// ==========================================
@Composable
fun NotesnookAttachmentWidget(
    block: NotesnookBlock.Attachment,
    onDelete: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    val context = LocalContext.current
    val bg = if (isDark) Color(0xFF1E2128) else Color(0xFFF3F4F6)
    val borderCol = if (isDark) Color(0xFF2E3440) else Color(0xFFE5E7EB)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(block.uri))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .padding(12.dp)
            .testTag("attachment_block_${block.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AttachFile,
            contentDescription = null,
            tint = Color(0xFF3B82F6),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.fileName,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = noteTextColor,
                maxLines = 1
            )
            Text(
                text = block.fileSize,
                style = TextStyle(fontSize = 11.5.sp),
                color = noteTextColor.copy(alpha = 0.55f)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp).testTag("delete_attachment_${block.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete attachment",
                tint = noteTextColor.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// 9. NOTESNOOK IMAGE BLOCK WIDGET
// ==========================================
@Composable
fun NotesnookImageBlockWidget(
    block: NotesnookBlock.Image,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    isDark: Boolean,
    noteTextColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("image_block_${block.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model = block.uri,
                contentDescription = block.caption.ifBlank { "Image" },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                contentScale = ContentScale.FillWidth
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .testTag("delete_image_${block.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete image",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Caption
        BasicTextField(
            value = block.caption,
            onValueChange = {
                block.caption = it
                onUpdate()
            },
            textStyle = TextStyle(
                color = noteTextColor.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                fontStyle = FontStyle.Italic,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            decorationBox = { innerTextField ->
                if (block.caption.isEmpty()) {
                    Text(
                        text = "Add caption...",
                        style = TextStyle(
                            color = noteTextColor.copy(alpha = 0.35f),
                            fontSize = 12.5.sp,
                            fontStyle = FontStyle.Italic,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                innerTextField()
            }
        )
    }
}
