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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val NotesnookGreen = Color(0xFF22C55E)

/**
 * Authentic Notesnook Insert Sheet matching Screenshot 2.
 * Offers: Outline list, Horizontal rule, Code block, Math & formulas, Callout,
 * Quote, Image, Attachment, Embed, and Table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesnookInsertBottomSheet(
    onDismissRequest: () -> Unit,
    onInsertOutlineList: () -> Unit,
    onInsertHorizontalRule: () -> Unit,
    onOpenCodeBlockDialog: () -> Unit,
    onOpenMathDialog: () -> Unit,
    onOpenCalloutDialog: () -> Unit,
    onInsertQuote: () -> Unit,
    onOpenImageDialog: () -> Unit,
    onOpenAttachmentDialog: () -> Unit,
    onOpenEmbedDialog: () -> Unit,
    onOpenTableDialog: () -> Unit,
    isDark: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (isDark) Color(0xFF16181B) else Color(0xFFFFFFFF)
    val itemTextColor = if (isDark) Color(0xFFE3E7ED) else Color(0xFF212529)
    val iconColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val arrowColor = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    val dividerColor = if (isDark) Color(0xFF23272E) else Color(0xFFE5E7EB)

    fun safeDismissAnd(action: () -> Unit) {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } catch (_: Exception) {}
            onDismissRequest()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .testTag("notesnook_insert_bottom_sheet")
        ) {
            // 1. Outline list
            NotesnookInsertRow(
                icon = Icons.Outlined.AccountTree,
                title = "Outline list",
                hasChevron = false,
                textColor = itemTextColor,
                iconColor = iconColor,
                onClick = {
                    safeDismissAnd { onInsertOutlineList() }
                },
                testTag = "insert_outline_list"
            )

            // 2. Horizontal rule
            NotesnookInsertRow(
                icon = Icons.Outlined.HorizontalRule,
                title = "Horizontal rule",
                hasChevron = false,
                textColor = itemTextColor,
                iconColor = iconColor,
                onClick = {
                    safeDismissAnd { onInsertHorizontalRule() }
                },
                testTag = "insert_horizontal_rule"
            )

            // 3. Code block
            NotesnookInsertRow(
                icon = Icons.Outlined.Code,
                title = "Code block",
                hasChevron = false,
                textColor = itemTextColor,
                iconColor = iconColor,
                onClick = {
                    safeDismissAnd { onOpenCodeBlockDialog() }
                },
                testTag = "insert_code_block"
            )

            // 4. Math & formulas
            NotesnookInsertRow(
                icon = Icons.Outlined.Functions,
                title = "Math & formulas",
                hasChevron = false,
                textColor = itemTextColor,
                iconColor = iconColor,
                onClick = {
                    safeDismissAnd { onOpenMathDialog() }
                },
                testTag = "insert_math_formulas"
            )

            // 5. Callout >
            NotesnookInsertRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Callout",
                hasChevron = true,
                textColor = itemTextColor,
                iconColor = iconColor,
                arrowColor = arrowColor,
                onClick = {
                    safeDismissAnd { onOpenCalloutDialog() }
                },
                testTag = "insert_callout"
            )

            // 6. Quote
            NotesnookInsertRow(
                icon = Icons.Outlined.FormatQuote,
                title = "Quote",
                hasChevron = false,
                textColor = itemTextColor,
                iconColor = iconColor,
                onClick = {
                    safeDismissAnd { onInsertQuote() }
                },
                testTag = "insert_quote"
            )

            // 7. Image >
            NotesnookInsertRow(
                icon = Icons.Outlined.Image,
                title = "Image",
                hasChevron = true,
                textColor = itemTextColor,
                iconColor = iconColor,
                arrowColor = arrowColor,
                onClick = {
                    safeDismissAnd { onOpenImageDialog() }
                },
                testTag = "insert_image"
            )

            // 8. Attachment
            NotesnookInsertRow(
                icon = Icons.Outlined.AttachFile,
                title = "Attachment",
                hasChevron = false,
                textColor = itemTextColor,
                iconColor = iconColor,
                onClick = {
                    safeDismissAnd { onOpenAttachmentDialog() }
                },
                testTag = "insert_attachment"
            )

            // 9. Embed >
            NotesnookInsertRow(
                icon = Icons.Outlined.SmartDisplay,
                title = "Embed",
                hasChevron = true,
                textColor = itemTextColor,
                iconColor = iconColor,
                arrowColor = arrowColor,
                onClick = {
                    safeDismissAnd { onOpenEmbedDialog() }
                },
                testTag = "insert_embed"
            )

            // 10. Table >
            NotesnookInsertRow(
                icon = Icons.Outlined.TableChart,
                title = "Table",
                hasChevron = true,
                textColor = itemTextColor,
                iconColor = iconColor,
                arrowColor = arrowColor,
                onClick = {
                    safeDismissAnd { onOpenTableDialog() }
                },
                testTag = "insert_table"
            )
        }
    }
}

@Composable
private fun NotesnookInsertRow(
    icon: ImageVector,
    title: String,
    hasChevron: Boolean,
    textColor: Color,
    iconColor: Color,
    arrowColor: Color = iconColor,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = textColor
            )
        }

        if (hasChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = arrowColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// NotesnookTableBuilderDialog is now defined in NotesnookTableWidget.kt


// ==========================================
// 2. MATH & FORMULAS DIALOG
// ==========================================
@Composable
fun NotesnookMathDialog(
    onDismiss: () -> Unit,
    onInsertFormula: (formula: String, isInline: Boolean) -> Unit,
    isDark: Boolean
) {
    var customFormula by remember { mutableStateOf("E = mc²") }
    var isInline by remember { mutableStateOf(false) }

    // Notesnook Deep Luxury Obsidian Theme
    val bg = Color(0xFF0F1115)
    val textPrimary = Color(0xFFF9FAFB)
    val textSecondary = Color(0xFF9CA3AF)
    val borderCol = Color(0xFF262B35)
    val previewBg = Color(0xFF161920)

    val popularFormulas = listOf(
        "E = mc²" to "Mass-Energy",
        "\\frac{a}{b}" to "Fraction",
        "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}" to "Quadratic",
        "\\int_{a}^{b} f(x) dx" to "Integral",
        "\\sum_{i=1}^{n} i^2" to "Summation",
        "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1" to "Limit",
        "\\sqrt{x^2 + y^2}" to "Euclidean Norm",
        "e^{i\\pi} + 1 = 0" to "Euler Identity",
        "\\begin{matrix} a & b \\\\ c & d \\end{matrix}" to "2x2 Matrix",
        "\\alpha + \\beta = \\theta" to "Greek"
    )

    val quickSymbols = listOf("±", "²", "³", "√", "∫", "∑", "π", "∞", "θ", "α", "β", "÷", "≠", "≤", "≥")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(4.dp)
                .testTag("math_formula_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Functions,
                        contentDescription = null,
                        tint = NotesnookGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Math & Formulas",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Formula input
                OutlinedTextField(
                    value = customFormula,
                    onValueChange = { customFormula = it },
                    label = { Text("LaTeX / KaTeX Formula") },
                    placeholder = { Text("e.g. E = mc² or \\frac{a}{b}") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NotesnookGreen,
                        unfocusedBorderColor = borderCol,
                        focusedLabelColor = NotesnookGreen,
                        unfocusedLabelColor = textSecondary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick symbols bar
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickSymbols) { sym ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E222A))
                                .border(1.dp, Color(0xFF282D37), RoundedCornerShape(6.dp))
                                .clickable { customFormula += sym }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(sym, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Preview Card
                Text(
                    text = "LIVE RENDER PREVIEW",
                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewBg)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customFormula.ifBlank { "(empty expression)" },
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (customFormula.isBlank()) textSecondary else textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "POPULAR PRESETS",
                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(popularFormulas) { (formula, label) ->
                        OutlinedButton(
                            onClick = { customFormula = formula },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (customFormula == formula) NotesnookGreen else borderCol),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (customFormula == formula) Color(0x2222C55E) else Color.Transparent
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, style = TextStyle(fontSize = 12.sp), color = textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inline Math", style = TextStyle(fontSize = 14.sp), color = textPrimary)
                    Switch(
                        checked = isInline,
                        onCheckedChange = { isInline = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NotesnookGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customFormula.isNotBlank()) {
                                onInsertFormula(customFormula, isInline)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen)
                    ) {
                        Text("Insert Formula", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. CODE BLOCK DIALOG
// ==========================================
@Composable
fun NotesnookCodeBlockDialog(
    onDismiss: () -> Unit,
    onInsertCodeBlock: (language: String) -> Unit,
    isDark: Boolean
) {
    val languages = listOf("Kotlin", "Java", "Python", "JavaScript", "TypeScript", "HTML", "CSS", "SQL", "JSON", "C++", "Rust", "Go", "Bash", "Text")
    var selectedLang by remember { mutableStateOf("Kotlin") }

    val bg = if (isDark) Color(0xFF1E2126) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderCol = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        tint = NotesnookGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Insert Code Block",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "SELECT LANGUAGE",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(languages) { lang ->
                        val isSelected = selectedLang.equals(lang, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NotesnookGreen else if (isDark) Color(0xFF2C3038) else Color(0xFFE5E7EB))
                                .clickable { selectedLang = lang }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = lang,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else textPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = textSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onInsertCodeBlock(selectedLang)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen)
                    ) {
                        Text("Insert Code", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. CALLOUT PICKER DIALOG
// ==========================================
@Composable
fun NotesnookCalloutDialog(
    onDismiss: () -> Unit,
    onSelectCallout: (type: String) -> Unit,
    isDark: Boolean
) {
    val calloutTypes = listOf(
        Triple("💡 Note", "note", Color(0xFF0284C7)),
        Triple("⚠️ Warning", "warning", Color(0xFFD97706)),
        Triple("📌 Important", "important", Color(0xFFDC2626)),
        Triple("🚀 Tip", "tip", Color(0xFF16A34A)),
        Triple("ℹ️ Info", "info", Color(0xFF9333EA)),
        Triple("✅ Success", "success", Color(0xFF059669))
    )

    val bg = if (isDark) Color(0xFF1E2126) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1F2937)
    val borderCol = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = "Select Callout Type",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                calloutTypes.forEach { (title, typeKey, tint) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectCallout(typeKey)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(tint)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            color = textPrimary
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. EMBED DIALOG
// ==========================================
@Composable
fun NotesnookEmbedDialog(
    onDismiss: () -> Unit,
    onInsertEmbed: (type: String, url: String, title: String) -> Unit,
    isDark: Boolean
) {
    var embedType by remember { mutableStateOf("YouTube") }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    val bg = if (isDark) Color(0xFF1E2126) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderCol = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    val embedTypes = listOf("YouTube", "Web Link", "Audio", "Figma", "CodePen")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.SmartDisplay,
                        contentDescription = null,
                        tint = NotesnookGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Embed Media / Web",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(embedTypes) { type ->
                        val isSelected = embedType.equals(type, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NotesnookGreen else if (isDark) Color(0xFF2C3038) else Color(0xFFE5E7EB))
                                .clickable { embedType = type }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = type,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else textPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (https://...)") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NotesnookGreen,
                        unfocusedBorderColor = borderCol
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NotesnookGreen,
                        unfocusedBorderColor = borderCol
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = textSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                onInsertEmbed(embedType, url, title)
                            }
                            onDismiss()
                        },
                        enabled = url.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen)
                    ) {
                        Text("Embed", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. IMAGE OPTIONS BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesnookImageOptionsSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onInsertUrl: (url: String, alt: String) -> Unit,
    isDark: Boolean
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf("") }
    var altCaption by remember { mutableStateOf("") }

    val sheetBg = if (isDark) Color(0xFF16181B) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    if (showUrlDialog) {
        Dialog(onDismissRequest = { showUrlDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = sheetBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.92f).padding(4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Insert Image from URL", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = textPrimary)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Image URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = altCaption,
                        onValueChange = { altCaption = it },
                        label = { Text("Caption (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showUrlDialog = false }) { Text("Cancel", color = textSecondary) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (imageUrl.isNotBlank()) {
                                    onInsertUrl(imageUrl, altCaption.ifBlank { "Image" })
                                    showUrlDialog = false
                                    onDismiss()
                                }
                            },
                            enabled = imageUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen)
                        ) {
                            Text("Insert", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = sheetBg,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 6.dp)) {
                NotesnookInsertRow(
                    icon = Icons.Outlined.PhotoCamera,
                    title = "Take photo",
                    hasChevron = false,
                    textColor = textPrimary,
                    iconColor = textSecondary,
                    onClick = {
                        onDismiss()
                        onTakePhoto()
                    },
                    testTag = "image_take_photo"
                )
                NotesnookInsertRow(
                    icon = Icons.Outlined.Image,
                    title = "Choose from gallery",
                    hasChevron = false,
                    textColor = textPrimary,
                    iconColor = textSecondary,
                    onClick = {
                        onDismiss()
                        onPickGallery()
                    },
                    testTag = "image_pick_gallery"
                )
                NotesnookInsertRow(
                    icon = Icons.Outlined.WebAsset,
                    title = "Insert image from URL",
                    hasChevron = false,
                    textColor = textPrimary,
                    iconColor = textSecondary,
                    onClick = {
                        showUrlDialog = true
                    },
                    testTag = "image_url_option"
                )
            }
        }
    }
}

// ==========================================
// 7. ATTACHMENT OPTIONS SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesnookAttachmentOptionsSheet(
    onDismiss: () -> Unit,
    onPickDocument: () -> Unit,
    onRecordAudio: () -> Unit,
    onOpenSketch: () -> Unit,
    isDark: Boolean
) {
    val sheetBg = if (isDark) Color(0xFF16181B) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 6.dp)) {
            NotesnookInsertRow(
                icon = Icons.Outlined.AttachFile,
                title = "Upload file / document",
                hasChevron = false,
                textColor = textPrimary,
                iconColor = textSecondary,
                onClick = {
                    onDismiss()
                    onPickDocument()
                },
                testTag = "attachment_upload_file"
            )
            NotesnookInsertRow(
                icon = Icons.Outlined.Mic,
                title = "Voice recording memo",
                hasChevron = false,
                textColor = textPrimary,
                iconColor = textSecondary,
                onClick = {
                    onDismiss()
                    onRecordAudio()
                },
                testTag = "attachment_voice_record"
            )
            NotesnookInsertRow(
                icon = Icons.Outlined.Brush,
                title = "Hand drawing / sketch",
                hasChevron = false,
                textColor = textPrimary,
                iconColor = textSecondary,
                onClick = {
                    onDismiss()
                    onOpenSketch()
                },
                testTag = "attachment_sketch"
            )
        }
    }
}
