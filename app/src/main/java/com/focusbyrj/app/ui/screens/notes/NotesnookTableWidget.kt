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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.WidthWide
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt

private val NotesnookGreen = Color(0xFF22C55E)
private val NotesnookGreenLight = Color(0x2222C55E)
private val NotesnookGreenGlow = Color(0x6622C55E)

// ==========================================
// 1. NOTESNOOK AUTHENTIC 10x6 TABLE BUILDER DIALOG
// ==========================================
@Composable
fun NotesnookTableBuilderDialog(
    onDismiss: () -> Unit,
    onInsertTable: (rows: Int, cols: Int, initialData: List<List<String>>) -> Unit,
    isDark: Boolean
) {
    var selectedRows by remember { mutableIntStateOf(2) }
    var selectedCols by remember { mutableIntStateOf(2) }

    var rowsInputText by remember { mutableStateOf("2") }
    var colsInputText by remember { mutableStateOf("2") }

    val bg = Color(0xFF0F1115)
    val surfaceBoxBg = Color(0xFF161920)
    val textPrimary = Color(0xFFF9FAFB)
    val textSecondary = Color(0xFF9CA3AF)
    val borderCol = Color(0xFF262B35)
    val cellInactiveBg = Color(0xFF1A1D24)
    val cellInactiveBorder = Color(0xFF282D37)
    val cellSelectedBg = Color(0x3322C55E)
    val cellSelectedBorder = NotesnookGreen

    fun updateSelection(r: Int, c: Int) {
        val safeR = r.coerceAtLeast(1)
        val safeC = c.coerceAtLeast(1)
        selectedRows = safeR
        selectedCols = safeC
        rowsInputText = safeR.toString()
        colsInputText = safeC.toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(4.dp)
                .testTag("notesnook_table_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).testTag("table_dialog_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Table",
                            style = TextStyle(
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = textPrimary
                        )
                    }

                    // Green Checkmark Button
                    IconButton(
                        onClick = {
                            val r = selectedRows.coerceIn(1, 50)
                            val c = selectedCols.coerceIn(1, 20)
                            val initial = List(r) { List(c) { "" } }
                            onInsertTable(r, c, initial)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(NotesnookGreen, CircleShape)
                            .testTag("table_dialog_check_insert")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Insert table",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section Label: "SELECT SIZE"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT SIZE",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = textSecondary
                    )
                    Text(
                        text = "$selectedRows × $selectedCols",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NotesnookGreen
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 10x6 Grid Matrix
                var gridWidthPx by remember { mutableStateOf(1f) }
                var gridHeightPx by remember { mutableStateOf(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceBoxBg)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .onGloballyPositioned { coordinates ->
                            gridWidthPx = max(1f, coordinates.size.width.toFloat())
                            gridHeightPx = max(1f, coordinates.size.height.toFloat())
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val cellW = gridWidthPx / 10f
                                val cellH = gridHeightPx / 6f
                                val c = ((offset.x / cellW).toInt() + 1).coerceIn(1, 10)
                                val r = ((offset.y / cellH).toInt() + 1).coerceIn(1, 6)
                                updateSelection(r, c)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val cellW = gridWidthPx / 10f
                                val cellH = gridHeightPx / 6f
                                val c = ((change.position.x / cellW).toInt() + 1).coerceIn(1, 10)
                                val r = ((change.position.y / cellH).toInt() + 1).coerceIn(1, 6)
                                updateSelection(r, c)
                            }
                        }
                        .testTag("table_grid_matrix")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (rowIndex in 1..6) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (colIndex in 1..10) {
                                    val isSelected = rowIndex <= selectedRows && colIndex <= selectedCols
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) cellSelectedBg else cellInactiveBg)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) cellSelectedBorder else cellInactiveBorder,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Custom Inputs for Rows and Columns with Stepper Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Rows stepper
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rows",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(surfaceBoxBg)
                                .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val next = (selectedRows - 1).coerceAtLeast(1)
                                    updateSelection(next, selectedCols)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Minus row", tint = textPrimary, modifier = Modifier.size(16.dp))
                            }
                            BasicTextField(
                                value = rowsInputText,
                                onValueChange = { str ->
                                    val filtered = str.filter { it.isDigit() }
                                    rowsInputText = filtered
                                    filtered.toIntOrNull()?.let { num ->
                                        selectedRows = num.coerceIn(1, 50)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val next = (selectedRows + 1).coerceAtMost(50)
                                    updateSelection(next, selectedCols)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add row", tint = textPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Columns stepper
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Columns",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(surfaceBoxBg)
                                .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val next = (selectedCols - 1).coerceAtLeast(1)
                                    updateSelection(selectedRows, next)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Minus column", tint = textPrimary, modifier = Modifier.size(16.dp))
                            }
                            BasicTextField(
                                value = colsInputText,
                                onValueChange = { str ->
                                    val filtered = str.filter { it.isDigit() }
                                    colsInputText = filtered
                                    filtered.toIntOrNull()?.let { num ->
                                        selectedCols = num.coerceIn(1, 20)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val next = (selectedCols + 1).coerceAtMost(20)
                                    updateSelection(selectedRows, next)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add column", tint = textPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val r = selectedRows.coerceIn(1, 50)
                            val c = selectedCols.coerceIn(1, 20)
                            val initial = List(r) { List(c) { "" } }
                            onInsertTable(r, c, initial)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Insert Table", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. DEDICATED TABLE & CELL EDITOR DIALOG
// ==========================================
@Composable
fun NotesnookTableEditorDialog(
    table: NotesnookBlock.Table,
    initialRow: Int = 0,
    initialCol: Int = 0,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSaveAndCommit: () -> Unit
) {
    var curRow by remember { mutableIntStateOf(initialRow.coerceIn(0, (table.rows - 1).coerceAtLeast(0))) }
    var curCol by remember { mutableIntStateOf(initialCol.coerceIn(0, (table.cols - 1).coerceAtLeast(0))) }

    // Helper to ensure dimensions
    fun ensureGrid() {
        while (table.data.size < table.rows) {
            table.data.add(MutableList(table.cols) { "" })
        }
        for (r in table.data) {
            while (r.size < table.cols) {
                r.add("")
            }
        }
        while (table.columnWidths.size < table.cols) {
            table.columnWidths.add(110)
        }
    }
    ensureGrid()

    var activeText by remember(curRow, curCol, table.rows, table.cols) {
        val str = table.data.getOrNull(curRow)?.getOrNull(curCol) ?: ""
        mutableStateOf(TextFieldValue(str, TextRange(str.length)))
    }

    val bg = if (isDark) Color(0xFF13161C) else Color(0xFFFFFFFF)
    val cardSurface = if (isDark) Color(0xFF1E222B) else Color(0xFFF3F4F6)
    val textPrimary = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderCol = if (isDark) Color(0xFF2C323F) else Color(0xFFE5E7EB)

    fun commitCurrentCell() {
        ensureGrid()
        if (curRow in 0 until table.rows && curCol in 0 until table.cols) {
            table.data[curRow][curCol] = activeText.text
        }
    }

    fun moveToCell(r: Int, c: Int) {
        commitCurrentCell()
        curRow = r.coerceIn(0, (table.rows - 1).coerceAtLeast(0))
        curCol = c.coerceIn(0, (table.cols - 1).coerceAtLeast(0))
    }

    fun nextCell() {
        commitCurrentCell()
        if (curCol < table.cols - 1) {
            curCol += 1
        } else if (curRow < table.rows - 1) {
            curRow += 1
            curCol = 0
        }
    }

    fun prevCell() {
        commitCurrentCell()
        if (curCol > 0) {
            curCol -= 1
        } else if (curRow > 0) {
            curRow -= 1
            curCol = table.cols - 1
        }
    }

    val currentColWidth = table.columnWidths.getOrElse(curCol) { 110 }

    Dialog(
        onDismissRequest = {
            commitCurrentCell()
            onSaveAndCommit()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                .padding(4.dp)
                .testTag("table_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.TableChart,
                            contentDescription = null,
                            tint = NotesnookGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Table (${table.rows} × ${table.cols})",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            color = textPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            commitCurrentCell()
                            onSaveAndCommit()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Table Mini Grid Selector
                Text(
                    text = "SELECT CELL TO EDIT",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val hScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScrollState)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardSurface)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (r in 0 until table.rows) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (c in 0 until table.cols) {
                                    val isSelected = (r == curRow && c == curCol)
                                    val cellVal = table.data.getOrNull(r)?.getOrNull(c) ?: ""
                                    val isHeader = (r == 0)

                                    Box(
                                        modifier = Modifier
                                            .width(76.dp)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when {
                                                    isSelected -> Color(0x3322C55E)
                                                    isHeader -> if (isDark) Color(0xFF2A303C) else Color(0xFFE2E8F0)
                                                    else -> if (isDark) Color(0xFF161920) else Color.White
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) NotesnookGreen else borderCol,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                moveToCell(r, c)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = if (cellVal.isBlank()) (if (isHeader) "H${c+1}" else "R${r+1}C${c+1}") else cellVal,
                                            style = TextStyle(
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                color = if (cellVal.isBlank()) textSecondary.copy(alpha = 0.6f) else textPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Cell Editor Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Row ${curRow + 1} of ${table.rows}, Column ${curCol + 1} of ${table.cols}" + if (curRow == 0) " (Header)" else "",
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = NotesnookGreen
                    )

                    if (activeText.text.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                activeText = TextFieldValue("", TextRange.Zero)
                                commitCurrentCell()
                            }
                        ) {
                            Text("Clear", fontSize = 12.sp, color = Color(0xFFEF4444))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = activeText,
                    onValueChange = { newTfv ->
                        activeText = newTfv
                        if (curRow in 0 until table.rows && curCol in 0 until table.cols) {
                            table.data[curRow][curCol] = newTfv.text
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("table_editor_cell_input"),
                    placeholder = {
                        Text(
                            if (curRow == 0) "Column ${curCol + 1} Header" else "Cell content...",
                            color = textSecondary.copy(alpha = 0.6f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NotesnookGreen,
                        unfocusedBorderColor = borderCol,
                        focusedContainerColor = cardSurface,
                        unfocusedContainerColor = cardSurface,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Controls: Prev Cell & Next Cell
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { prevCell() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                        border = BorderStroke(1.dp, borderCol)
                    ) {
                        Icon(Icons.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prev Cell", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { nextCell() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen)
                    ) {
                        Text("Next Cell", fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = borderCol, thickness = 1.dp)

                Spacer(modifier = Modifier.height(10.dp))

                // Horizontal Cell Width Adjustment Section
                Text(
                    text = "COLUMN ${curCol + 1} HORIZONTAL WIDTH",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardSurface)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WidthWide,
                            contentDescription = null,
                            tint = NotesnookGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Width: ${currentColWidth} dp",
                            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                            color = textPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val nextW = (currentColWidth - 15).coerceIn(60, 400)
                                while (table.columnWidths.size <= curCol) table.columnWidths.add(110)
                                table.columnWidths[curCol] = nextW
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Narrower", tint = textPrimary, modifier = Modifier.size(14.dp))
                        }

                        TextButton(
                            onClick = {
                                while (table.columnWidths.size <= curCol) table.columnWidths.add(110)
                                table.columnWidths[curCol] = 110
                            }
                        ) {
                            Text("Reset", fontSize = 11.sp, color = textSecondary)
                        }

                        IconButton(
                            onClick = {
                                val nextW = (currentColWidth + 15).coerceIn(60, 400)
                                while (table.columnWidths.size <= curCol) table.columnWidths.add(110)
                                table.columnWidths[curCol] = nextW
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Wider", tint = textPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Structure modification buttons: +Row, -Row, +Col, -Col
                Text(
                    text = "STRUCTURE ACTIONS",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            commitCurrentCell()
                            val newRow = MutableList(table.cols) { "" }
                            table.data.add(newRow)
                            table.rows = table.data.size
                            curRow = table.rows - 1
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, borderCol)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = textPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Row", fontSize = 12.5.sp, color = textPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            commitCurrentCell()
                            table.cols += 1
                            for (r in table.data) {
                                r.add("")
                            }
                            table.columnWidths.add(110)
                            curCol = table.cols - 1
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, borderCol)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = textPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Col", fontSize = 12.5.sp, color = textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (table.rows > 1) {
                        OutlinedButton(
                            onClick = {
                                commitCurrentCell()
                                if (table.data.isNotEmpty()) {
                                    val removeIdx = curRow.coerceIn(0, table.data.size - 1)
                                    table.data.removeAt(removeIdx)
                                    table.rows = table.data.size
                                    curRow = curRow.coerceAtMost(table.rows - 1)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x44EF4444))
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Row", fontSize = 12.sp, color = Color(0xFFEF4444))
                        }
                    }

                    if (table.cols > 1) {
                        OutlinedButton(
                            onClick = {
                                commitCurrentCell()
                                val removeCol = curCol.coerceIn(0, table.cols - 1)
                                table.cols -= 1
                                for (r in table.data) {
                                    if (r.size > removeCol) {
                                        r.removeAt(removeCol)
                                    }
                                }
                                if (table.columnWidths.size > removeCol) {
                                    table.columnWidths.removeAt(removeCol)
                                }
                                curCol = curCol.coerceAtMost(table.cols - 1)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x44EF4444))
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Col", fontSize = 12.sp, color = Color(0xFFEF4444))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Done Button
                Button(
                    onClick = {
                        commitCurrentCell()
                        onSaveAndCommit()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NotesnookGreen)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done Editing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// 3. NOTESNOOK SEAMLESS IN-NOTE TABLE WIDGET WITH SUBTLE HORIZONTAL RESIZING
// ==========================================
@Composable
fun NotesnookTableWidget(
    table: NotesnookBlock.Table,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean,
    textColor: Color
) {
    // Internal table modification version to trigger Compose recomposition
    var tableVersion by remember { mutableIntStateOf(0) }
    var focusedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editDialogInitialCell by remember { mutableStateOf(Pair(0, 0)) }
    var resizingColIndex by remember { mutableStateOf<Int?>(null) }
    var resizingCurrentWidthDp by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current

    val gridBorderColor = if (isDark) Color(0xFF323842) else Color(0xFFDCE1E8)
    val headerBg = if (isDark) Color(0xFF222730) else Color(0xFFF3F4F6)
    val cellBg = if (isDark) Color(0xFF16191E) else Color(0xFFFFFFFF)
    val actionBg = if (isDark) Color(0xFF22262F) else Color(0xFFE9ECEF)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    val minCellHeight = 44.dp

    // Safe guarantee that data dimensions match rows and cols
    fun ensureGridData() {
        while (table.data.size < table.rows) {
            table.data.add(MutableList(table.cols) { "" })
        }
        for (r in table.data) {
            while (r.size < table.cols) {
                r.add("")
            }
        }
        while (table.columnWidths.size < table.cols) {
            table.columnWidths.add(110)
        }
        while (table.columnWidths.size > table.cols) {
            table.columnWidths.removeAt(table.columnWidths.size - 1)
        }
    }
    ensureGridData()

    // Cell Focus Requesters
    val focusRequesters = remember(table.rows, table.cols) {
        val map = mutableMapOf<Pair<Int, Int>, FocusRequester>()
        for (r in 0 until table.rows) {
            for (c in 0 until table.cols) {
                map[Pair(r, c)] = FocusRequester()
            }
        }
        map
    }

    if (showEditorDialog) {
        NotesnookTableEditorDialog(
            table = table,
            initialRow = editDialogInitialCell.first,
            initialCol = editDialogInitialCell.second,
            isDark = isDark,
            onDismiss = { showEditorDialog = false },
            onSaveAndCommit = {
                tableVersion++
                onUpdate()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("table_widget_${table.id}")
    ) {
        // Table Micro Actions Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Table size badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionBg
                ) {
                    Text(
                        text = "${table.rows} × ${table.cols}",
                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = subTextColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }

                // Add Row button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionBg,
                    modifier = Modifier.clickable {
                        val newRow = MutableList(table.cols) { "" }
                        table.data.add(newRow)
                        table.rows = table.data.size
                        tableVersion++
                        onUpdate()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = textColor)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Row", style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium), color = textColor)
                    }
                }

                // Add Column button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionBg,
                    modifier = Modifier.clickable {
                        table.cols += 1
                        for (r in table.data) {
                            r.add("")
                        }
                        table.columnWidths.add(110)
                        tableVersion++
                        onUpdate()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = textColor)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Col", style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium), color = textColor)
                    }
                }

                // Edit Table Modal button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionBg,
                    modifier = Modifier.clickable {
                        editDialogInitialCell = focusedCell ?: Pair(0, 0)
                        showEditorDialog = true
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Table", modifier = Modifier.size(12.dp), tint = NotesnookGreen)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Edit", style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold), color = NotesnookGreen)
                    }
                }

                // More Table Options Dropdown
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Table options", tint = subTextColor, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(if (isDark) Color(0xFF22262F) else Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Cell Editor Dialog", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                showMenu = false
                                editDialogInitialCell = focusedCell ?: Pair(0, 0)
                                showEditorDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Auto-fit column widths", fontSize = 13.sp) },
                            onClick = {
                                table.columnWidths.clear()
                                for (i in 0 until table.cols) {
                                    table.columnWidths.add(110)
                                }
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Make all columns wider (+20dp)", fontSize = 13.sp) },
                            onClick = {
                                for (i in 0 until table.cols) {
                                    val cur = table.columnWidths.getOrElse(i) { 110 }
                                    if (table.columnWidths.size <= i) table.columnWidths.add(cur + 20)
                                    else table.columnWidths[i] = (cur + 20).coerceIn(60, 400)
                                }
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Make all columns narrower (-20dp)", fontSize = 13.sp) },
                            onClick = {
                                for (i in 0 until table.cols) {
                                    val cur = table.columnWidths.getOrElse(i) { 110 }
                                    if (table.columnWidths.size <= i) table.columnWidths.add(cur - 20)
                                    else table.columnWidths[i] = (cur - 20).coerceIn(60, 400)
                                }
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        HorizontalDivider(color = gridBorderColor)
                        DropdownMenuItem(
                            text = { Text("Insert row above", fontSize = 13.sp) },
                            onClick = {
                                val newRow = MutableList(table.cols) { "" }
                                table.data.add(0, newRow)
                                table.rows = table.data.size
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Insert row below", fontSize = 13.sp) },
                            onClick = {
                                val newRow = MutableList(table.cols) { "" }
                                table.data.add(newRow)
                                table.rows = table.data.size
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Insert column left", fontSize = 13.sp) },
                            onClick = {
                                table.cols += 1
                                for (r in table.data) {
                                    r.add(0, "")
                                }
                                table.columnWidths.add(0, 110)
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Insert column right", fontSize = 13.sp) },
                            onClick = {
                                table.cols += 1
                                for (r in table.data) {
                                    r.add("")
                                }
                                table.columnWidths.add(110)
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                        if (table.rows > 1) {
                            DropdownMenuItem(
                                text = { Text("Delete last row", fontSize = 13.sp, color = Color(0xFFEF4444)) },
                                onClick = {
                                    if (table.data.isNotEmpty()) {
                                        table.data.removeAt(table.data.size - 1)
                                        table.rows = table.data.size
                                        tableVersion++
                                        onUpdate()
                                    }
                                    showMenu = false
                                }
                            )
                        }
                        if (table.cols > 1) {
                            DropdownMenuItem(
                                text = { Text("Delete last column", fontSize = 13.sp, color = Color(0xFFEF4444)) },
                                onClick = {
                                    table.cols -= 1
                                    for (r in table.data) {
                                        if (r.isNotEmpty()) r.removeAt(r.size - 1)
                                    }
                                    if (table.columnWidths.isNotEmpty()) {
                                        table.columnWidths.removeAt(table.columnWidths.size - 1)
                                    }
                                    tableVersion++
                                    onUpdate()
                                    showMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Clear all cells", fontSize = 13.sp) },
                            onClick = {
                                for (r in table.data) {
                                    for (i in r.indices) {
                                        r[i] = ""
                                    }
                                }
                                tableVersion++
                                onUpdate()
                                showMenu = false
                            }
                        )
                    }
                }
            }

            // Right side: Active resizing indicator or Delete table button
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = resizingColIndex != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NotesnookGreenLight,
                        border = BorderStroke(1.dp, NotesnookGreen)
                    ) {
                        Text(
                            text = "Col ${(resizingColIndex ?: 0) + 1}: ${resizingCurrentWidthDp}dp",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = NotesnookGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("delete_table_${table.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete table",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Table Grid Container with Subtle Horizontal Cell Sizing & Drag Handles
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidth = maxWidth
            val colCount = maxOf(1, table.cols)
            val totalDividersWidth = (colCount - 1).dp

            // Helper to get column width
            fun getColWidth(colIdx: Int): Dp {
                val stored = table.columnWidths.getOrNull(colIdx)
                return if (stored != null && stored > 0) {
                    stored.dp
                } else {
                    // Default fallback evenly divided
                    val available = (containerWidth - totalDividersWidth).coerceAtLeast(0.dp)
                    maxOf(100.dp, available / colCount)
                }
            }

            // Calculate total width of all columns + dividers
            var totalTableWidth = 0.dp
            for (c in 0 until table.cols) {
                totalTableWidth += getColWidth(c)
            }
            totalTableWidth += totalDividersWidth

            val isScrollNeeded = totalTableWidth > containerWidth
            val hScroll = rememberScrollState()
            val tableShape = RoundedCornerShape(8.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isScrollNeeded) Modifier.horizontalScroll(hScroll) else Modifier)
                    .clip(tableShape)
                    .border(1.dp, gridBorderColor, tableShape)
            ) {
                Column(
                    modifier = Modifier.width(if (isScrollNeeded) totalTableWidth else containerWidth.coerceAtLeast(totalTableWidth))
                ) {
                    for (r in 0 until table.rows) {
                        if (r > 0) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = gridBorderColor
                            )
                        }
                        val isHeaderRow = (r == 0)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = minCellHeight)
                                .background(if (isHeaderRow) headerBg else cellBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (c in 0 until table.cols) {
                                val currentWidthDp = getColWidth(c)
                                val isColResizing = (resizingColIndex == c)

                                // Left divider if not first column
                                if (c > 0) {
                                    val isPrevColResizing = (resizingColIndex == c - 1)
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .heightIn(min = minCellHeight)
                                            .background(if (isPrevColResizing) NotesnookGreen else gridBorderColor)
                                    )
                                }

                                // Ensure row and cell exist
                                while (table.data.size <= r) {
                                    table.data.add(MutableList(table.cols) { "" })
                                }
                                val rowList = table.data[r]
                                while (rowList.size <= c) {
                                    rowList.add("")
                                }

                                val isCellFocused = (focusedCell?.first == r && focusedCell?.second == c)
                                val cellKey = "cell_${table.id}_${r}_${c}_$tableVersion"
                                val cellRequester = focusRequesters[Pair(r, c)] ?: remember { FocusRequester() }

                                var cellTfv by remember(cellKey) {
                                    val currentVal = rowList.getOrElse(c) { "" }
                                    mutableStateOf(TextFieldValue(currentVal, TextRange(currentVal.length)))
                                }

                                // Sync if data changed externally
                                LaunchedEffect(tableVersion) {
                                    val freshVal = table.data.getOrNull(r)?.getOrNull(c) ?: ""
                                    if (cellTfv.text != freshVal) {
                                        cellTfv = TextFieldValue(freshVal, TextRange(freshVal.length))
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(currentWidthDp)
                                        .heightIn(min = minCellHeight)
                                        .background(if (isCellFocused) NotesnookGreenLight else Color.Transparent)
                                        .border(
                                            width = if (isCellFocused) 1.5.dp else 0.dp,
                                            color = if (isCellFocused) NotesnookGreen else Color.Transparent
                                        )
                                        .clickable {
                                            focusedCell = Pair(r, c)
                                            try {
                                                cellRequester.requestFocus()
                                            } catch (_: Exception) {}
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Cell Text Field
                                        BasicTextField(
                                            value = cellTfv,
                                            onValueChange = { newVal ->
                                                cellTfv = newVal
                                                if (r < table.data.size && c < table.data[r].size) {
                                                    table.data[r][c] = newVal.text
                                                    onUpdate()
                                                }
                                            },
                                            textStyle = TextStyle(
                                                color = textColor,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isHeaderRow) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            cursorBrush = SolidColor(NotesnookGreen),
                                            keyboardOptions = KeyboardOptions(
                                                imeAction = if (r == table.rows - 1 && c == table.cols - 1) ImeAction.Done else ImeAction.Next
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onNext = {
                                                    val nextPair = if (c < table.cols - 1) Pair(r, c + 1) else if (r < table.rows - 1) Pair(r + 1, 0) else null
                                                    if (nextPair != null) {
                                                        focusedCell = nextPair
                                                        try {
                                                            focusRequesters[nextPair]?.requestFocus()
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                            ),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (cellTfv.text.isEmpty()) {
                                                        Text(
                                                            text = if (isHeaderRow) "Col ${c + 1}" else "",
                                                            style = TextStyle(
                                                                color = textColor.copy(alpha = 0.35f),
                                                                fontSize = 13.5.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(cellRequester)
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        focusedCell = Pair(r, c)
                                                    } else if (focusedCell == Pair(r, c)) {
                                                        focusedCell = null
                                                    }
                                                }
                                                .testTag("table_cell_${r}_${c}")
                                        )

                                        // Subtle drag handle grip on Header cells for intuitive column width resizing
                                        if (isHeaderRow) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 16.dp, height = 28.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(if (isColResizing) NotesnookGreenGlow else Color.Transparent)
                                                    .pointerInput(c) {
                                                        detectHorizontalDragGestures(
                                                            onDragStart = {
                                                                resizingColIndex = c
                                                                resizingCurrentWidthDp = currentWidthDp.value.roundToInt()
                                                            },
                                                            onDragEnd = {
                                                                resizingColIndex = null
                                                                tableVersion++
                                                                onUpdate()
                                                            },
                                                            onDragCancel = {
                                                                resizingColIndex = null
                                                            },
                                                            onHorizontalDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val deltaDp = (dragAmount / density.density).roundToInt()
                                                                if (deltaDp != 0) {
                                                                    val curW = table.columnWidths.getOrElse(c) { currentWidthDp.value.roundToInt() }
                                                                    val newW = (curW + deltaDp).coerceIn(60, 400)
                                                                    while (table.columnWidths.size <= c) {
                                                                        table.columnWidths.add(110)
                                                                    }
                                                                    table.columnWidths[c] = newW
                                                                    resizingCurrentWidthDp = newW
                                                                    tableVersion++
                                                                }
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                // Subtle vertical 2-line indicator grip
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(1.5.dp)
                                                            .height(10.dp)
                                                            .background(if (isColResizing) NotesnookGreen else subTextColor.copy(alpha = 0.45f))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .width(1.5.dp)
                                                            .height(10.dp)
                                                            .background(if (isColResizing) NotesnookGreen else subTextColor.copy(alpha = 0.45f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
