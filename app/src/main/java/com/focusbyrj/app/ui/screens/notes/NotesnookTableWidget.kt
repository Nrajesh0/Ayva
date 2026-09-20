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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TableChart
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min

private val NotesnookGreen = Color(0xFF22C55E)
private val NotesnookGreenLight = Color(0x2222C55E)

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

    // Notesnook Authentic Obsidian Black Table Selector Theme
    val bg = Color(0xFF0F1115) // Deep luxury dark canvas
    val surfaceBoxBg = Color(0xFF161920) // Elevated container surface
    val textPrimary = Color(0xFFF9FAFB) // High contrast white
    val textSecondary = Color(0xFF9CA3AF) // Muted label gray
    val borderCol = Color(0xFF262B35) // Elegant subtle border
    val cellInactiveBg = Color(0xFF1A1D24) // Empty unselected cell background
    val cellInactiveBorder = Color(0xFF282D37) // Inactive cell border
    val cellSelectedBg = Color(0x3322C55E) // Vibrant green tint
    val cellSelectedBorder = NotesnookGreen // Vivid accent green

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
                // Top Action Bar: Back Arrow, Title "Table", and Green Checkmark
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

                // Section Label: "SELECT SIZE: 2 x 2"
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

                // The 10x6 Grid Canvas (Polished Notesnook Dark Matrix)
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
// 2. NOTESNOOK SEAMLESS IN-NOTE TABLE WIDGET
// ==========================================
@Composable
fun NotesnookTableWidget(
    table: NotesnookBlock.Table,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean,
    textColor: Color
) {
    var isTableFocused by remember { mutableStateOf(false) }

    val gridBorderColor = if (isDark) Color(0xFF323842) else Color(0xFFDCE1E8)
    val headerBg = if (isDark) Color(0xFF222730) else Color(0xFFF3F4F6)
    val cellBg = if (isDark) Color(0xFF16191E) else Color(0xFFFFFFFF)
    val actionBg = if (isDark) Color(0xFF22262F) else Color(0xFFE9ECEF)
    val subTextColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    val minCellHeight = 38.dp

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
                .padding(bottom = 6.dp),
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
                            text = { Text("Insert row above", fontSize = 13.sp) },
                            onClick = {
                                val newRow = MutableList(table.cols) { "" }
                                table.data.add(0, newRow)
                                table.rows = table.data.size
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
                                onUpdate()
                                showMenu = false
                            }
                        )
                    }
                }
            }

            // Delete Entire Table Button
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

        // Seamless, Perfectly-Even Table Grid
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val containerWidth = maxWidth
            val colCount = maxOf(1, table.cols)
            val totalDividersWidth = (colCount - 1).dp
            val minColWidth = 72.dp
            val availableForCols = (containerWidth - totalDividersWidth).coerceAtLeast(0.dp)
            val autoColWidth = availableForCols / colCount
            val isScrollNeeded = autoColWidth < minColWidth
            val colWidth = if (isScrollNeeded) 85.dp else autoColWidth
            val totalTableWidth = if (isScrollNeeded) (colWidth * colCount + totalDividersWidth) else containerWidth

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
                    modifier = if (isScrollNeeded) Modifier.width(totalTableWidth) else Modifier.fillMaxWidth()
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
                                .height(IntrinsicSize.Min)
                                .background(if (isHeaderRow) headerBg else cellBg)
                        ) {
                            for (c in 0 until table.cols) {
                                if (c > 0) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(gridBorderColor)
                                    )
                                }

                                // Ensure data grid has this cell
                                while (table.data.size <= r) {
                                    table.data.add(MutableList(table.cols) { "" })
                                }
                                val rowList = table.data[r]
                                while (rowList.size <= c) {
                                    rowList.add("")
                                }

                                val cellContent = rowList[c]

                                Box(
                                    modifier = Modifier
                                        .width(colWidth)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = minCellHeight)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = cellContent,
                                        onValueChange = { newVal ->
                                            table.data[r][c] = newVal
                                            onUpdate()
                                        },
                                        textStyle = TextStyle(
                                            color = textColor,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isHeaderRow) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(NotesnookGreen),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                                if (cellContent.isEmpty() && isHeaderRow) {
                                                    Text(
                                                        text = "Col ${c + 1}",
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
                                            .fillMaxWidth()
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    isTableFocused = true
                                                }
                                            }
                                            .testTag("table_cell_${r}_${c}")
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
