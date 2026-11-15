/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.HabitDaySummary
import com.focusbyrj.app.util.*
import java.text.SimpleDateFormat
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun EnclaveMatrixSection(
    colors: EnclaveColors,
    profile: UserProfile,
    activeStreak: Int,
    longestStreak: Int,
    effectiveLevel: Int,
    dailyUsage: Map<Int, Long>
) {
    val tierTitle = ProfileAvatarManager.getAvatarTitle(profile.selectedAvatar, profile.avatarTier).uppercase()
    val multiplier = FocusEconomyManager.getGoldMultiplier(effectiveLevel)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Matrix",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "MATRIX // 365D",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            // TIER Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.subCardBg,
                border = BorderStroke(1.dp, colors.subCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.accentCyan)
                    )
                    Text(
                        text = "TIER: $tierTitle",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Matrix Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Streak Metrics Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Col 1: ACTIVE STREAK
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE STREAK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$activeStreak",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "DAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 1.5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Adjust,
                                contentDescription = null,
                                tint = colors.accentCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (activeStreak > 0) "+100%" else "+0%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                ),
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Col 2: ALL-TIME RECORD
                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = "ALL-TIME RECORD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$longestStreak",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "DAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 1.5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "CONSISTENT DISCIPLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp
                            ),
                            color = colors.textMuted
                        )
                    }

                    // Col 3: FREEZE SHIELD
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FREEZE SHIELD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${profile.streakFreezes}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = colors.textMuted,
                                modifier = Modifier.padding(bottom = 1.5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "STREAK SAFEGUARD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp
                            ),
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Heatmap Grid: M, W, F rows x 18 week columns
                EnclaveHeatmapGrid(colors = colors, dailyUsage = dailyUsage)

                Spacer(modifier = Modifier.height(16.dp))

                // Heatmap Footer: Balanced 2-sided layout (Multiplier / Boost on left, Legend on right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "MULTIPLIER:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                ),
                                color = colors.textMuted
                            )
                            Text(
                                text = "${multiplier}x",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = "GOLD BOOST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.accentGold
                        )
                    }

                    // LESS [ ][ ][ ][ ] MORE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "LESS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.subCardBg))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.accentPrimary.copy(alpha = 0.25f)))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.accentPrimary.copy(alpha = 0.55f)))
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.accentPrimary))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "MORE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnclaveHeatmapGrid(colors: EnclaveColors, dailyUsage: Map<Int, Long>) {
    val context = LocalContext.current
    val rowLabels = listOf("M", "", "W", "", "F", "", "")
    val totalCols = 18
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Labels column (M, W, F)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 1.dp)
        ) {
            for (r in 0..6) {
                Box(
                    modifier = Modifier.size(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rowLabels[r].isNotEmpty()) {
                        Text(
                            text = rowLabels[r],
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // Columns of squares
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (col in 0 until totalCols) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0..6) {
                        val daysAgo = (totalCols - 1 - col) * 7 + (6 - row)
                        val targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                        val dayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)
                        val compositeKey = targetCal.get(Calendar.YEAR) * 1000 + dayOfYear
                        // Support composite year*1000+dayOfYear key with fallback to bare dayOfYear
                        val realMinutes = ((dailyUsage[compositeKey] ?: dailyUsage[dayOfYear]) ?: 0L) / (60 * 1000L)

                        val cellColor = when {
                            realMinutes > 45 -> colors.accentPrimary
                            realMinutes > 25 -> colors.accentPrimary.copy(alpha = 0.7f)
                            realMinutes > 10 -> colors.accentPrimary.copy(alpha = 0.45f)
                            realMinutes > 0 -> colors.accentPrimary.copy(alpha = 0.25f)
                            else -> colors.subCardBg
                        }

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cellColor)
                                .clickable {
                                    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(targetCal.time)
                                    val statusStr = if (realMinutes > 0) "Focus: ${realMinutes}m ($dateStr)" else "Rest day ($dateStr)"
                                    Toast.makeText(context, statusStr, Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }
            }
        }
    }
}

// B0. HABIT 7-DAY VECTOR GRAPH (AREA & SPLINE GRAPH)

@Composable
internal fun Habit7DayVectorGraph(
    weeklyHistory: List<HabitDaySummary>,
    habitColor: Color,
    isDark: Boolean,
    colors: EnclaveColors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val history = if (weeklyHistory.size == 7) {
        weeklyHistory
    } else {
        val defaultLetters = listOf("M", "T", "W", "T", "F", "S", "S")
        (0 until 7).map { i ->
            weeklyHistory.getOrNull(i) ?: HabitDaySummary(
                date = "Day $i",
                dayOfWeekLetter = defaultLetters[i],
                completedCount = 0,
                targetCount = 1,
                isCompleted = false,
                isToday = (i == 6)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF06070B) else colors.subCardBg)
            .border(1.dp, if (isDark) Color(0xFF141720) else colors.subCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        // Trailing status & legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "7-DAY CADENCE GRAPH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                ),
                color = colors.textMuted
            )

            val hits7Days = history.count { it.isCompleted }
            val consistencyPct = ((hits7Days * 100) / 7).coerceIn(0, 100)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (consistencyPct >= 70) habitColor else colors.textMuted)
                )
                Text(
                    text = "$consistencyPct% CONSISTENCY ($hits7Days/7 DAYS)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (consistencyPct >= 70) habitColor else colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas: Vector Area & Spline Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizontalPadding = 14.dp.toPx()
                val topPadding = 8.dp.toPx()
                val bottomPadding = 8.dp.toPx()
                val availableWidth = size.width - horizontalPadding * 2f
                val availableHeight = size.height - topPadding - bottomPadding
                val stepX = availableWidth / 6f
                val baseY = size.height - bottomPadding

                // Horizontal Guide Lines: 100% target dashed line
                drawLine(
                    color = habitColor.copy(alpha = 0.18f),
                    start = Offset(horizontalPadding, topPadding),
                    end = Offset(size.width - horizontalPadding, topPadding),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )

                // 50% target dotted line
                val midY = topPadding + availableHeight * 0.5f
                drawLine(
                    color = if (isDark) Color(0xFF1E222A).copy(alpha = 0.5f) else Color(0xFFE2E8F0),
                    start = Offset(horizontalPadding, midY),
                    end = Offset(size.width - horizontalPadding, midY),
                    strokeWidth = 0.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                )

                // Baseline 0%
                drawLine(
                    color = if (isDark) Color(0xFF1A1E26) else Color(0xFFE2E8F0),
                    start = Offset(horizontalPadding, baseY),
                    end = Offset(size.width - horizontalPadding, baseY),
                    strokeWidth = 1.dp.toPx()
                )

                // Calculate (x, y) for each of the 7 days
                val points = history.mapIndexed { index, day ->
                    val ratio = if (day.targetCount > 0) (day.completedCount.toFloat() / day.targetCount.toFloat()).coerceIn(0f, 1f) else 0f
                    val x = horizontalPadding + index * stepX
                    val y = topPadding + (1f - ratio) * availableHeight
                    Offset(x, y)
                }

                if (points.isNotEmpty()) {
                    // Build smooth cubic bezier curve
                    val strokePath = Path()
                    val fillPath = Path()

                    strokePath.moveTo(points.first().x, points.first().y)
                    fillPath.moveTo(points.first().x, baseY)
                    fillPath.lineTo(points.first().x, points.first().y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx1 = p0.x + (p1.x - p0.x) / 2f
                        val cy1 = p0.y
                        val cx2 = p0.x + (p1.x - p0.x) / 2f
                        val cy2 = p1.y

                        strokePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    }

                    fillPath.lineTo(points.last().x, baseY)
                    fillPath.close()

                    // Draw area gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                habitColor.copy(alpha = 0.38f),
                                habitColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startY = topPadding,
                            endY = baseY
                        )
                    )

                    // Draw curve line
                    drawPath(
                        path = strokePath,
                        color = habitColor,
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw interactive data nodes
                    points.forEachIndexed { i, pt ->
                        val day = history[i]
                        val ratio = if (day.targetCount > 0) (day.completedCount.toFloat() / day.targetCount.toFloat()).coerceIn(0f, 1f) else 0f
                        val isToday = day.isToday

                        when {
                            ratio >= 1f -> {
                                // Full hit: glowing outer halo + filled circle + core dot
                                drawCircle(
                                    color = habitColor.copy(alpha = 0.35f),
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = habitColor,
                                    radius = 4.2.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 1.8.dp.toPx(),
                                    center = pt
                                )
                            }
                            ratio > 0f -> {
                                // Partial hit: stroked ring + core
                                drawCircle(
                                    color = habitColor,
                                    radius = 4.dp.toPx(),
                                    center = pt,
                                    style = Stroke(width = 1.8.dp.toPx())
                                )
                                drawCircle(
                                    color = habitColor.copy(alpha = 0.45f),
                                    radius = 2.2.dp.toPx(),
                                    center = pt
                                )
                            }
                            else -> {
                                // 0: muted dot
                                drawCircle(
                                    color = if (isDark) Color(0xFF262C38) else Color(0xFFCBD5E1),
                                    radius = 2.6.dp.toPx(),
                                    center = pt
                                )
                            }
                        }

                        // Today pulse ring
                        if (isToday) {
                            drawCircle(
                                color = if (isDark) Color.White.copy(alpha = 0.85f) else colors.textPrimary,
                                radius = 7.5.dp.toPx(),
                                center = pt,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Aligned Day Labels Axis (M, T, W, T, F, S, S)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            history.forEachIndexed { index, day ->
                val ratio = if (day.targetCount > 0) (day.completedCount.toFloat() / day.targetCount.toFloat()).coerceIn(0f, 1f) else 0f
                val isToday = day.isToday

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val msg = "${day.dayOfWeekLetter} (${day.date}): ${day.completedCount}/${day.targetCount} completed"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = day.dayOfWeekLetter,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isToday) (if (isDark) Color.White else colors.textPrimary) else colors.textMuted
                    )

                    Spacer(modifier = Modifier.height(1.dp))

                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(width = 10.dp, height = 2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(habitColor)
                        )
                    } else {
                        Text(
                            text = if (ratio >= 1f) "✓" else "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (ratio >= 1f) habitColor else colors.textMuted.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// B. DISCIPLINE & SHIELDS: APP RESISTANCE & 7-DAY HABIT CONSISTENCY
