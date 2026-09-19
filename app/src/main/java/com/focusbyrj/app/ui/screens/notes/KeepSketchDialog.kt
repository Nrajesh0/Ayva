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

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Supported drawing tool types for a realistic studio drawing feel.
 */
enum class DrawingTool(val label: String, val icon: ImageVector) {
    PEN("Pen", Icons.Filled.Edit),
    PENCIL("Pencil", Icons.Filled.Brush),
    HIGHLIGHTER("Highlighter", Icons.Filled.Highlight),
    MARKER("Marker", Icons.Filled.AutoFixHigh),
    ERASER("Eraser", Icons.Filled.DeleteOutline),
    SHAPE("Shapes", Icons.Filled.Layers)
}

/**
 * Preset geometric shapes for vector drawing.
 */
enum class DrawingShapeType(val label: String, val icon: ImageVector) {
    LINE("Line", Icons.Filled.ShowChart),
    ARROW("Arrow", Icons.Filled.ShowChart),
    RECTANGLE("Rectangle", Icons.Filled.Rectangle),
    ROUNDED_RECT("Round Rect", Icons.Filled.Rectangle),
    OVAL("Oval / Circle", Icons.Filled.Circle)
}

/**
 * Paper background styles for true note and sketch drawing.
 */
enum class PaperStyle(val label: String, val icon: ImageVector) {
    BLANK("Blank Paper", Icons.Filled.Layers),
    DOTS("Dot Grid", Icons.Filled.GridOn),
    LINES("Ruled Lines", Icons.Filled.ShowChart),
    GRID("Grid Graph", Icons.Filled.GridOn),
    BLACKBOARD("Blackboard", Icons.Filled.Layers)
}

/**
 * Represents a single drawn stroke or shape.
 */
data class DrawingPath(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val opacity: Float = 1.0f,
    val tool: DrawingTool = DrawingTool.PEN,
    val shapeType: DrawingShapeType? = null,
    val startPoint: Offset? = null,
    val endPoint: Offset? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeepSketchDialog(
    onDismiss: () -> Unit,
    onSaveDrawing: (Bitmap) -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    // History Stacks for Undo / Redo
    val paths = remember { mutableStateListOf<DrawingPath>() }
    val redoStack = remember { mutableStateListOf<DrawingPath>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }

    // Active tool settings
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var currentShapeType by remember { mutableStateOf(DrawingShapeType.LINE) }
    var paperStyle by remember { mutableStateOf(if (isSystemDark) PaperStyle.BLACKBOARD else PaperStyle.BLANK) }

    // Colors & Sizes
    val defaultPenColor = if (paperStyle == PaperStyle.BLACKBOARD) Color(0xFFF1F3F4) else Color(0xFF202124)
    var selectedColor by remember { mutableStateOf(defaultPenColor) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(6f) }
    var selectedOpacity by remember { mutableFloatStateOf(1f) }

    // Recent Colors Memory
    val recentColors = remember {
        mutableStateListOf(
            defaultPenColor,
            Color(0xFF1A73E8), // Royal Blue
            Color(0xFFEA4335), // Crimson Red
            Color(0xFF34A853), // Emerald Green
            Color(0xFFFBBC04), // Amber Yellow
            Color(0xFF9334E6)  // Purple
        )
    }

    // Modal Sheet & Dialog States
    var showColorSheet by remember { mutableStateOf(false) }
    var showStrokeSettingsSheet by remember { mutableStateOf(false) }
    var showShapePickerSheet by remember { mutableStateOf(false) }
    var showPaperStyleSheet by remember { mutableStateOf(false) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmationDialog by remember { mutableStateOf(false) }

    // Interactive shape dragging state
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragCurrent by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1440)) }

    // Comprehensive Organized Color Palette (Never cut off)
    val colorCategories = remember {
        listOf(
            "Essentials" to listOf(
                Color(0xFF000000), Color(0xFF202124), Color(0xFF5F6368),
                Color(0xFF9AA0A6), Color(0xFFDADCE0), Color(0xFFFFFFFF),
                Color(0xFF1A73E8), Color(0xFFD93025), Color(0xFF1E8E3E), Color(0xFFF9AB00)
            ),
            "Vibrant & Neons" to listOf(
                Color(0xFFFF1744), Color(0xFFFF5252), Color(0xFFFF4081),
                Color(0xFFE040FB), Color(0xFF7C4DFF), Color(0xFF536DFE),
                Color(0xFF00B0FF), Color(0xFF00E5FF), Color(0xFF1DE9B6),
                Color(0xFF00E676), Color(0xFF76FF03), Color(0xFFFFEA00),
                Color(0xFFFF9100), Color(0xFFFF3D00)
            ),
            "Pastels & Soft" to listOf(
                Color(0xFFFFCDD2), Color(0xFFF8BBD0), Color(0xFFE1BEE7),
                Color(0xFFD1C4E9), Color(0xFFC5CAE9), Color(0xFFBBDEFB),
                Color(0xFFB3E5FC), Color(0xFFB2EBF2), Color(0xFFB2DFDB),
                Color(0xFFC8E6C9), Color(0xFFDCEDC8), Color(0xFFF0F4C3),
                Color(0xFFFFF9C4), Color(0xFFFFECB3), Color(0xFFFFE0B2)
            ),
            "Earth & Vintage" to listOf(
                Color(0xFF3E2723), Color(0xFF4E342E), Color(0xFF5D4037),
                Color(0xFF6D4C41), Color(0xFF795548), Color(0xFF8D6E63),
                Color(0xFFA1887F), Color(0xFF263238), Color(0xFF37474F),
                Color(0xFF455A64), Color(0xFF1B5E20), Color(0xFF33691E)
            )
        )
    }

    // Fast quick-swatch bar colors for immediate tapping
    val quickColors = remember(defaultPenColor) {
        listOf(
            defaultPenColor,
            Color(0xFFFFFFFF), // White
            Color(0xFF1A73E8), // Google Blue
            Color(0xFFEA4335), // Red
            Color(0xFF34A853), // Green
            Color(0xFFFBBC04), // Amber
            Color(0xFF9334E6), // Purple
            Color(0xFFFF6D00), // Orange
            Color(0xFF00ACC1), // Cyan
            Color(0xFFE91E63)  // Pink
        )
    }

    // Helper to add or select a color
    fun onSelectColor(color: Color) {
        selectedColor = color
        if (!recentColors.contains(color)) {
            recentColors.add(0, color)
            if (recentColors.size > 8) recentColors.removeAt(recentColors.size - 1)
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Dialog(
        onDismissRequest = {
            if (paths.isNotEmpty()) {
                showDiscardConfirmationDialog = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isSystemDark) Color(0xFF121316) else Color(0xFFF4F5F8)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ==========================================
                // 1. TOP APP BAR: Ultra-compact, guaranteed no overflow
                // ==========================================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSystemDark) Color(0xFF1E2024) else Color(0xFFFFFFFF),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Back button
                        IconButton(
                            onClick = {
                                if (paths.isNotEmpty()) {
                                    showDiscardConfirmationDialog = true
                                } else {
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("sketch_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back or Discard",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Center: Undo & Redo Group
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (paths.isNotEmpty()) {
                                        val removed = paths.removeAt(paths.size - 1)
                                        redoStack.add(removed)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                enabled = paths.isNotEmpty(),
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("sketch_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (paths.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (redoStack.isNotEmpty()) {
                                        val restored = redoStack.removeAt(redoStack.size - 1)
                                        paths.add(restored)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                enabled = redoStack.isNotEmpty(),
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("sketch_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Right: Paper Style, Clear, & Done Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Paper Grid Selector
                            IconButton(
                                onClick = { showPaperStyleSheet = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("sketch_paper_style_button")
                            ) {
                                Icon(
                                    imageVector = paperStyle.icon,
                                    contentDescription = "Paper style",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Clear Canvas
                            IconButton(
                                onClick = {
                                    if (paths.isNotEmpty()) showClearConfirmationDialog = true
                                },
                                enabled = paths.isNotEmpty(),
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("sketch_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Clear all",
                                    tint = if (paths.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Done / Save Button
                            Button(
                                onClick = {
                                    val w = if (canvasSize.width > 0) canvasSize.width else 1080
                                    val h = if (canvasSize.height > 0) canvasSize.height else 1440
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)

                                    // 1. Draw Paper Background onto Bitmap
                                    val bgPaint = Paint().apply { isAntiAlias = true }
                                    when (paperStyle) {
                                        PaperStyle.BLANK -> {
                                            canvas.drawColor(if (isSystemDark) android.graphics.Color.rgb(30, 31, 34) else android.graphics.Color.WHITE)
                                        }
                                        PaperStyle.BLACKBOARD -> {
                                            canvas.drawColor(android.graphics.Color.rgb(20, 22, 26))
                                        }
                                        PaperStyle.DOTS, PaperStyle.LINES, PaperStyle.GRID -> {
                                            canvas.drawColor(if (isSystemDark) android.graphics.Color.rgb(30, 31, 34) else android.graphics.Color.WHITE)
                                            val linePaint = Paint().apply {
                                                isAntiAlias = true
                                                color = if (isSystemDark) android.graphics.Color.argb(40, 255, 255, 255) else android.graphics.Color.argb(40, 0, 0, 0)
                                                strokeWidth = 2f
                                            }
                                            if (paperStyle == PaperStyle.LINES) {
                                                var y = 60f
                                                while (y < h) {
                                                    canvas.drawLine(0f, y, w.toFloat(), y, linePaint)
                                                    y += 60f
                                                }
                                            } else if (paperStyle == PaperStyle.GRID) {
                                                var x = 50f
                                                while (x < w) {
                                                    canvas.drawLine(x, 0f, x, h.toFloat(), linePaint)
                                                    x += 50f
                                                }
                                                var y = 50f
                                                while (y < h) {
                                                    canvas.drawLine(0f, y, w.toFloat(), y, linePaint)
                                                    y += 50f
                                                }
                                            } else if (paperStyle == PaperStyle.DOTS) {
                                                var x = 40f
                                                while (x < w) {
                                                    var y = 40f
                                                    while (y < h) {
                                                        canvas.drawCircle(x, y, 3f, linePaint)
                                                        y += 40f
                                                    }
                                                    x += 40f
                                                }
                                            }
                                        }
                                    }

                                    // 2. Draw Vector Strokes & Shapes onto Bitmap
                                    val strokePaint = Paint().apply {
                                        isAntiAlias = true
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                    }

                                    for (item in paths) {
                                        val alphaInt = (item.color.alpha * item.opacity * 255).toInt().coerceIn(0, 255)
                                        val argb = android.graphics.Color.argb(
                                            alphaInt,
                                            (item.color.red * 255).toInt(),
                                            (item.color.green * 255).toInt(),
                                            (item.color.blue * 255).toInt()
                                        )
                                        strokePaint.color = argb
                                        strokePaint.strokeWidth = item.strokeWidth

                                        if (item.tool == DrawingTool.HIGHLIGHTER) {
                                            strokePaint.strokeCap = Paint.Cap.SQUARE
                                        } else {
                                            strokePaint.strokeCap = Paint.Cap.ROUND
                                        }

                                        if (item.shapeType != null && item.startPoint != null && item.endPoint != null) {
                                            drawShapeOnAndroidCanvas(canvas, strokePaint, item.shapeType, item.startPoint, item.endPoint)
                                        } else if (item.points.size > 1) {
                                            val aPath = AndroidPath()
                                            aPath.moveTo(item.points[0].x, item.points[0].y)
                                            for (i in 1 until item.points.size) {
                                                val p0 = item.points[i - 1]
                                                val p1 = item.points[i]
                                                val midX = (p0.x + p1.x) / 2f
                                                val midY = (p0.y + p1.y) / 2f
                                                aPath.quadTo(p0.x, p0.y, midX, midY)
                                            }
                                            val last = item.points.last()
                                            aPath.lineTo(last.x, last.y)
                                            canvas.drawPath(aPath, strokePaint)
                                        }
                                    }

                                    onSaveDrawing(bitmap)
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("sketch_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Done", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ==========================================
                // 2. DRAWING CANVAS SHEET
                // ==========================================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
                        .background(
                            when (paperStyle) {
                                PaperStyle.BLACKBOARD -> Color(0xFF14161A)
                                else -> if (isSystemDark) Color(0xFF1E2024) else Color(0xFFFFFFFF)
                            }
                        )
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(currentTool, currentShapeType, selectedColor, selectedStrokeWidth, selectedOpacity) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (currentTool == DrawingTool.ERASER) {
                                        val erased = erasePathsNear(paths, offset, selectedStrokeWidth * 2.5f)
                                        if (erased) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else if (currentTool == DrawingTool.SHAPE) {
                                        shapeDragStart = offset
                                        shapeDragCurrent = offset
                                    } else {
                                        redoStack.clear()
                                        currentPoints.clear()
                                        currentPoints.add(offset)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (currentTool == DrawingTool.ERASER) {
                                        val erased = erasePathsNear(paths, change.position, selectedStrokeWidth * 2.5f)
                                        if (erased) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else if (currentTool == DrawingTool.SHAPE) {
                                        shapeDragCurrent = change.position
                                    } else {
                                        currentPoints.add(change.position)
                                    }
                                },
                                onDragEnd = {
                                    if (currentTool == DrawingTool.SHAPE && shapeDragStart != null && shapeDragCurrent != null) {
                                        paths.add(
                                            DrawingPath(
                                                points = emptyList(),
                                                color = selectedColor,
                                                strokeWidth = selectedStrokeWidth,
                                                opacity = selectedOpacity,
                                                tool = DrawingTool.SHAPE,
                                                shapeType = currentShapeType,
                                                startPoint = shapeDragStart,
                                                endPoint = shapeDragCurrent
                                            )
                                        )
                                        shapeDragStart = null
                                        shapeDragCurrent = null
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (currentPoints.isNotEmpty()) {
                                        val effectiveOpacity = when (currentTool) {
                                            DrawingTool.HIGHLIGHTER -> 0.35f * selectedOpacity
                                            DrawingTool.PENCIL -> 0.85f * selectedOpacity
                                            DrawingTool.MARKER -> 0.75f * selectedOpacity
                                            else -> selectedOpacity
                                        }
                                        paths.add(
                                            DrawingPath(
                                                points = currentPoints.toList(),
                                                color = selectedColor,
                                                strokeWidth = selectedStrokeWidth,
                                                opacity = effectiveOpacity,
                                                tool = currentTool
                                            )
                                        )
                                        currentPoints.clear()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // 1. Draw Paper Grids / Dots / Lines
                        drawPaperPattern(paperStyle, isSystemDark, size)

                        // 2. Draw Committed Paths & Shapes with Smooth Quadratic Beziers
                        for (pathData in paths) {
                            if (pathData.shapeType != null && pathData.startPoint != null && pathData.endPoint != null) {
                                drawVectorShape(
                                    drawScope = this,
                                    shapeType = pathData.shapeType,
                                    start = pathData.startPoint,
                                    end = pathData.endPoint,
                                    color = pathData.color.copy(alpha = pathData.color.alpha * pathData.opacity),
                                    strokeWidth = pathData.strokeWidth
                                )
                            } else if (pathData.points.size > 1) {
                                val smoothPath = createSmoothPath(pathData.points)
                                val cap = if (pathData.tool == DrawingTool.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round
                                drawPath(
                                    path = smoothPath,
                                    color = pathData.color.copy(alpha = pathData.color.alpha * pathData.opacity),
                                    style = Stroke(
                                        width = pathData.strokeWidth,
                                        cap = cap,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // 3. Draw Active Freehand Stroke with Smooth Interpolation
                        if (currentPoints.size > 1) {
                            val activeSmoothPath = createSmoothPath(currentPoints)
                            val effectiveOpacity = when (currentTool) {
                                DrawingTool.HIGHLIGHTER -> 0.35f * selectedOpacity
                                DrawingTool.PENCIL -> 0.85f * selectedOpacity
                                DrawingTool.MARKER -> 0.75f * selectedOpacity
                                else -> selectedOpacity
                            }
                            val cap = if (currentTool == DrawingTool.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round
                            drawPath(
                                path = activeSmoothPath,
                                color = selectedColor.copy(alpha = selectedColor.alpha * effectiveOpacity),
                                style = Stroke(
                                    width = selectedStrokeWidth,
                                    cap = cap,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // 4. Draw Live Interactive Shape Dragging Preview
                        if (currentTool == DrawingTool.SHAPE && shapeDragStart != null && shapeDragCurrent != null) {
                            drawVectorShape(
                                drawScope = this,
                                shapeType = currentShapeType,
                                start = shapeDragStart!!,
                                end = shapeDragCurrent!!,
                                color = selectedColor.copy(alpha = selectedColor.alpha * selectedOpacity),
                                strokeWidth = selectedStrokeWidth
                            )
                        }
                    }

                    // Empty canvas placeholder guide
                    if (paths.isEmpty() && currentPoints.isEmpty() && shapeDragStart == null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = currentTool.icon,
                                contentDescription = null,
                                tint = (if (isSystemDark) Color.White else Color.Black).copy(alpha = 0.18f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Draw, sketch or write with stylus or finger",
                                style = MaterialTheme.typography.bodyMedium,
                                color = (if (isSystemDark) Color.White else Color.Black).copy(alpha = 0.28f)
                            )
                        }
                    }
                }

                // ==========================================
                // 3. BOTTOM DOCK TOOLBAR (Elevated above system gesture navigation bar)
                // ==========================================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = if (isSystemDark) Color(0xFF1E2024) else Color(0xFFFFFFFF),
                    shadowElevation = 10.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp)
                    ) {
                        // Row 1: Swatches Bar + Full Palette Button + Brush Size Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Expand Full Palette Button
                            FilledTonalIconButton(
                                onClick = { showColorSheet = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("sketch_full_palette_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = "Full color palette",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Swatches LazyRow with smooth start/end padding
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(quickColors) { color ->
                                    val isSelected = selectedColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .clickable { onSelectColor(color) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected color",
                                                tint = if (color == Color.White || color == Color(0xFFFBBC04) || color == Color(0xFFF1F3F4)) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Stroke & Brush Quick Preview Pill
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showStrokeSettingsSheet = true }
                                    .testTag("sketch_stroke_settings_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((selectedStrokeWidth / 2f).coerceIn(4f, 10f).dp)
                                            .clip(CircleShape)
                                            .background(selectedColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${selectedStrokeWidth.toInt()}px",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = "Brush settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(6.dp))

                        // Row 2: Tool Switcher Dock (Pen, Pencil, Highlighter, Marker, Eraser, Shapes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DrawingTool.entries.forEach { tool ->
                                val isSelected = currentTool == tool
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isSelected && tool == DrawingTool.SHAPE) {
                                                showShapePickerSheet = true
                                            } else if (isSelected && tool != DrawingTool.ERASER) {
                                                showStrokeSettingsSheet = true
                                            }
                                            currentTool = tool
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = tool.label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = tool.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 10.sp
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
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

    // ==========================================
    // 4. MODAL SHEETS & DIALOGS
    // ==========================================

    // A. Full Color Palette & Category Sheet (Categorized & Non-cut off)
    if (showColorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showColorSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Color Palette",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showColorSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                // Recent colors
                if (recentColors.isNotEmpty()) {
                    Text(
                        text = "Recently Used",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentColors.forEach { color ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onSelectColor(color)
                                        showColorSheet = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = if (color == Color.White || color == Color(0xFFFBBC04) || color == Color(0xFFF1F3F4)) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Categorized Swatches with FlowRow (Guaranteed never cut off!)
                colorCategories.forEach { (categoryName, colors) ->
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        colors.forEach { color ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onSelectColor(color)
                                        showColorSheet = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = if (color == Color.White || color == Color(0xFFFBBC04) || color == Color(0xFFF1F3F4)) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // B. Brush Stroke & Opacity Settings Sheet
    if (showStrokeSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStrokeSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentTool.label} Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showStrokeSettingsSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Live Stroke Preview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cap = if (currentTool == DrawingTool.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round
                            val effectiveOpacity = when (currentTool) {
                                DrawingTool.HIGHLIGHTER -> 0.35f * selectedOpacity
                                DrawingTool.PENCIL -> 0.85f * selectedOpacity
                                DrawingTool.MARKER -> 0.75f * selectedOpacity
                                else -> selectedOpacity
                            }
                            drawLine(
                                color = selectedColor.copy(alpha = selectedColor.alpha * effectiveOpacity),
                                start = Offset(size.width * 0.15f, size.height / 2f),
                                end = Offset(size.width * 0.85f, size.height / 2f),
                                strokeWidth = selectedStrokeWidth,
                                cap = cap
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stroke Size Slider & Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Brush Size",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "${selectedStrokeWidth.toInt()} px",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = selectedStrokeWidth,
                    onValueChange = { selectedStrokeWidth = it },
                    valueRange = 1f..64f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Quick Size Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Fine" to 3f,
                        "Medium" to 7f,
                        "Bold" to 14f,
                        "Marker" to 24f,
                        "Heavy" to 42f
                    ).forEach { (label, size) ->
                        val isSelected = selectedStrokeWidth == size
                        FilledTonalButton(
                            onClick = { selectedStrokeWidth = size },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Opacity",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "${(selectedOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = selectedOpacity,
                    onValueChange = { selectedOpacity = it },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // C. Shapes Picker Bottom Sheet
    if (showShapePickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShapePickerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Shape",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showShapePickerSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                DrawingShapeType.entries.forEach { shape ->
                    val isSelected = currentShapeType == shape
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                currentShapeType = shape
                                currentTool = DrawingTool.SHAPE
                                showShapePickerSheet = false
                            },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = shape.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = shape.label,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // D. Paper Style Picker Sheet
    if (showPaperStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPaperStyleSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paper Template",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showPaperStyleSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                PaperStyle.entries.forEach { style ->
                    val isSelected = paperStyle == style
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                paperStyle = style
                                if (style == PaperStyle.BLACKBOARD && selectedColor == Color(0xFF202124)) {
                                    selectedColor = Color(0xFFF1F3F4)
                                } else if (style != PaperStyle.BLACKBOARD && selectedColor == Color(0xFFF1F3F4)) {
                                    selectedColor = Color(0xFF202124)
                                }
                                showPaperStyleSheet = false
                            },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = style.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // E. Clear Canvas Confirmation Dialog
    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = { Text("Clear Canvas?") },
            text = { Text("This will erase all current drawing strokes and shapes on the canvas.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        paths.clear()
                        redoStack.clear()
                        currentPoints.clear()
                        showClearConfirmationDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // F. Discard Confirmation Dialog
    if (showDiscardConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmationDialog = false },
            title = { Text("Discard Drawing?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard this drawing?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmationDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmationDialog = false }) {
                    Text("Keep Drawing")
                }
            }
        )
    }
}

/**
 * Creates a smooth Bezier spline path from discrete touch points.
 */
private fun createSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return path
    }
    for (i in 1 until points.size) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val midX = (p0.x + p1.x) / 2f
        val midY = (p0.y + p1.y) / 2f
        path.quadraticTo(p0.x, p0.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

/**
 * Helper to erase paths near touch target.
 */
private fun erasePathsNear(paths: MutableList<DrawingPath>, target: Offset, radius: Float): Boolean {
    val initialSize = paths.size
    val iterator = paths.iterator()
    while (iterator.hasNext()) {
        val p = iterator.next()
        var hit = false
        if (p.shapeType != null && p.startPoint != null && p.endPoint != null) {
            val minX = minOf(p.startPoint.x, p.endPoint.x) - radius
            val maxX = maxOf(p.startPoint.x, p.endPoint.x) + radius
            val minY = minOf(p.startPoint.y, p.endPoint.y) - radius
            val maxY = maxOf(p.startPoint.y, p.endPoint.y) + radius
            if (target.x in minX..maxX && target.y in minY..maxY) {
                hit = true
            }
        } else {
            for (pt in p.points) {
                val dist = hypot(pt.x - target.x, pt.y - target.y)
                if (dist <= radius + (p.strokeWidth / 2f)) {
                    hit = true
                    break
                }
            }
        }
        if (hit) {
            iterator.remove()
        }
    }
    return paths.size != initialSize
}

/**
 * Draws paper grids, ruled lines, and dots in Compose DrawScope.
 */
private fun DrawScope.drawPaperPattern(paperStyle: PaperStyle, isDark: Boolean, size: Size) {
    val patternColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    when (paperStyle) {
        PaperStyle.BLANK, PaperStyle.BLACKBOARD -> {
            // Clean blank background
        }
        PaperStyle.DOTS -> {
            val step = 32f
            var x = step
            while (x < size.width) {
                var y = step
                while (y < size.height) {
                    drawCircle(color = patternColor, radius = 2f, center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        PaperStyle.LINES -> {
            val step = 48f
            var y = step
            while (y < size.height) {
                drawLine(color = patternColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.2f)
                y += step
            }
        }
        PaperStyle.GRID -> {
            val step = 40f
            var x = step
            while (x < size.width) {
                drawLine(color = patternColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(color = patternColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

/**
 * Draws vector geometric shapes onto Compose DrawScope.
 */
private fun drawVectorShape(
    drawScope: DrawScope,
    shapeType: DrawingShapeType,
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (shapeType) {
        DrawingShapeType.LINE -> {
            drawScope.drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        DrawingShapeType.ARROW -> {
            drawScope.drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val arrowLength = (strokeWidth * 3.5f).coerceIn(16f, 40f)
            val arrowAngle = Math.PI / 6.0
            val x1 = end.x - (arrowLength * cos(angle - arrowAngle)).toFloat()
            val y1 = end.y - (arrowLength * sin(angle - arrowAngle)).toFloat()
            val x2 = end.x - (arrowLength * cos(angle + arrowAngle)).toFloat()
            val y2 = end.y - (arrowLength * sin(angle + arrowAngle)).toFloat()
            drawScope.drawLine(color = color, start = end, end = Offset(x1, y1), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawScope.drawLine(color = color, start = end, end = Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        DrawingShapeType.RECTANGLE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val width = kotlin.math.abs(end.x - start.x)
            val height = kotlin.math.abs(end.y - start.y)
            drawScope.drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = stroke
            )
        }
        DrawingShapeType.ROUNDED_RECT -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val width = kotlin.math.abs(end.x - start.x)
            val height = kotlin.math.abs(end.y - start.y)
            drawScope.drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(16f, 16f),
                style = stroke
            )
        }
        DrawingShapeType.OVAL -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val width = kotlin.math.abs(end.x - start.x)
            val height = kotlin.math.abs(end.y - start.y)
            drawScope.drawOval(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = stroke
            )
        }
    }
}

/**
 * Draws vector geometric shapes onto Android Canvas for final export bitmap.
 */
private fun drawShapeOnAndroidCanvas(
    canvas: android.graphics.Canvas,
    paint: Paint,
    shapeType: DrawingShapeType,
    start: Offset,
    end: Offset
) {
    when (shapeType) {
        DrawingShapeType.LINE -> {
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
        DrawingShapeType.ARROW -> {
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
            val arrowLength = (paint.strokeWidth * 3.5f).coerceIn(16f, 40f)
            val arrowAngle = Math.PI / 6.0
            val x1 = end.x - (arrowLength * cos(angle - arrowAngle)).toFloat()
            val y1 = end.y - (arrowLength * sin(angle - arrowAngle)).toFloat()
            val x2 = end.x - (arrowLength * cos(angle + arrowAngle)).toFloat()
            val y2 = end.y - (arrowLength * sin(angle + arrowAngle)).toFloat()
            canvas.drawLine(end.x, end.y, x1, y1, paint)
            canvas.drawLine(end.x, end.y, x2, y2, paint)
        }
        DrawingShapeType.RECTANGLE -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)
            canvas.drawRect(left, top, right, bottom, paint)
        }
        DrawingShapeType.ROUNDED_RECT -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)
            canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, paint)
        }
        DrawingShapeType.OVAL -> {
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)
            canvas.drawOval(RectF(left, top, right, bottom), paint)
        }
    }
}
