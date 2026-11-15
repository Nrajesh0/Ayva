/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.util.*
import kotlinx.coroutines.launch
import java.util.*

import com.focusbyrj.app.ui.screens.*

@Composable
internal fun EnclaveAppBlockingSection(
    colors: EnclaveColors,
    restrictions: List<AppRestriction>,
    dailyResisted: Int,
    lifetimeResists: Int,
    onManageClick: () -> Unit,
    onHabitsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusApplication
    val habitsProgress by app.habitRepository.activeHabitsWithProgress.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val hardApps = restrictions.filter { it.isRestricted && it.mode.equals("HARD", ignoreCase = true) }
    val softApps = restrictions.filter { it.isRestricted && !it.mode.equals("HARD", ignoreCase = true) }
    val totalRestricted = restrictions.count { it.isRestricted }
    val resistedDisplay = if (dailyResisted > 0) dailyResisted else lifetimeResists

    // 7-day calculations from real Room habit logs
    val totalHabitsCount = habitsProgress.size
    val totalPossible7Days = totalHabitsCount * 7
    val totalHit7Days = habitsProgress.sumOf { h -> h.weeklyHistory.count { it.isCompleted } }
    val consistencyPct = if (totalPossible7Days > 0) ((totalHit7Days * 100) / totalPossible7Days).coerceIn(0, 100) else 0

    val todayCompletedCount = habitsProgress.count { it.isCompletedToday }
    val longestActiveStreak = habitsProgress.maxOfOrNull { it.currentStreak } ?: 0
    val streakDisplay = longestActiveStreak
    val disciplineRank = when {
        consistencyPct >= 85 -> "Top 1% discipline"
        consistencyPct >= 65 -> "Top 5% cadence"
        consistencyPct >= 40 -> "Consistent momentum"
        else -> "Protocol initializing"
    }
    val todayRatio = if (totalHabitsCount > 0) (todayCompletedCount.toFloat() / totalHabitsCount.toFloat()).coerceIn(0f, 1f) else 0f
    val remainingProtocols = (totalHabitsCount - todayCompletedCount).coerceAtLeast(0)

    // Theme-adaptive styling: darker inset background in dark themes, clean soft inset in light themes
    val isDark = colors.isDark
    val panelBg = if (isDark) Color(0xFF090A0E) else colors.subCardBg.copy(alpha = 0.65f)
    val panelBorder = if (isDark) Color(0xFF1B1E26) else colors.subCardBorder
    val ringTrackColor = if (isDark) Color(0xFF1E222A) else Color(0xFFE2E8F0)
    val ringArcColor = if (isDark) Color.White else colors.accentPrimary
    val watermarkColor = if (isDark) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.04f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header: [🛡 DISCIPLINE & SHIELDS]   [● X GUARDED]
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
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Discipline & Shields",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "DISCIPLINE & SHIELDS",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary
                )
            }

            // ● Status Pill (tap to view habits or shields)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.subCardBg,
                border = BorderStroke(1.dp, colors.subCardBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onHabitsClick?.invoke() ?: onManageClick() }
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
                            .background(if (totalRestricted > 0 || totalHabitsCount > 0) colors.accentCyan else colors.textMuted)
                    )
                    Text(
                        text = if (totalRestricted > 0) "$totalRestricted APPS • $totalHabitsCount HABITS" else "$totalHabitsCount ACTIVE HABITS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Surface Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.cardBg,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // ── DUAL PANELS: TOP METRIC 1 (DEFENSE) & TOP METRIC 2 (HABITS) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // LEFT PANEL: TOP METRIC 1 (DEFENSE) - APP LAUNCHES RESISTED
                    Surface(
                        modifier = Modifier.weight(1.15f),
                        shape = RoundedCornerShape(16.dp),
                        color = panelBg,
                        border = BorderStroke(1.dp, panelBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp)
                        ) {
                            // Dot + Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFFE2E8F0) else colors.textPrimary)
                                )
                                Text(
                                    text = "APP LAUNCHES RESISTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Barriers backed out of",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Large Number with Watermark #01
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                // Background Watermark
                                Text(
                                    text = "#01",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = watermarkColor,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 4.dp, bottom = 2.dp)
                                )

                                // Foreground Value: 14 TIMES DEFENSE
                                Row(
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = "$resistedDisplay",
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                        Text(
                                            text = "TIMES",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "DEFENSE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.sp
                                            ),
                                            color = colors.textMuted
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sub-telemetry: Guarded status
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = if (totalRestricted > 0) "$totalRestricted APPS GUARDED" else "NO APPS GUARDED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.accentCyan
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = colors.textMuted
                                    )
                                )
                                Text(
                                    text = "ZERO-ACCESS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    // RIGHT PANEL: TOP METRIC 2 (HABITS) - WEEKLY CONSISTENCY SCORE
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = panelBg,
                        border = BorderStroke(1.dp, panelBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header Row: CONSISTENCY + Pill Badge (Today's progress)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CONSISTENCY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = colors.textPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDark) Color(0xFF1E232B) else Color(0xFFE2E8F0)
                                ) {
                                    Text(
                                        text = "$todayCompletedCount/$totalHabitsCount",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Circular Progress Ring: Weekly Consistency Score (86%)
                            val consistencyRatio = (consistencyPct / 100f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier.size(66.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(66.dp)) {
                                    val strokeWidth = 5.dp.toPx()
                                    // Background ring track
                                    drawCircle(
                                        color = ringTrackColor,
                                        style = Stroke(width = strokeWidth)
                                    )
                                    // Active progress arc for weekly consistency
                                    if (consistencyRatio > 0f) {
                                        drawArc(
                                            color = ringArcColor,
                                            startAngle = -90f,
                                            sweepAngle = 360f * consistencyRatio,
                                            useCenter = false,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$consistencyPct%",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "7-DAY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = colors.textMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom Caption: Percentage of habit targets hit over the last 7 days
                            Text(
                                text = "Targets hit (7 days)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.3.sp
                                ),
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── HABIT CADENCE & CUSTOMIZED 7-DAY GRAPHS (REAL DATA) ──────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HABIT CADENCE & 7-DAY TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        ),
                        color = colors.textMuted
                    )

                    if (totalHabitsCount > 0) {
                        Text(
                            text = "$todayCompletedCount/$totalHabitsCount DONE TODAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (todayCompletedCount == totalHabitsCount && totalHabitsCount > 0) colors.accentGreen else colors.accentCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (habitsProgress.isEmpty()) {
                    // Empty state: invite user to create their first habit protocol
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = panelBg,
                        border = BorderStroke(1.dp, panelBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHabitsClick?.invoke() ?: onManageClick() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.accentCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚡", fontSize = 16.sp)
                                }
                                Column {
                                    Text(
                                        text = "NO HABITS YET",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Tap to add your first habit & track real data",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp
                                        ),
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Text(
                                text = "+ ADD HABIT >",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = colors.accentCyan
                            )
                        }
                    }
                } else {
                    // Per-habit list with individual customized 7-day graphs
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        habitsProgress.take(3).forEach { habitItem ->
                            val habit = habitItem.habit
                            val habitColor = remember(habit.colorHex) {
                                try {
                                    Color(android.graphics.Color.parseColor(habit.colorHex))
                                } catch (_: Exception) {
                                    colors.accentCyan
                                }
                            }
                            val isDone = habitItem.isCompletedToday

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = panelBg,
                                border = BorderStroke(1.dp, panelBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(13.dp)
                                ) {
                                    // Row 1: Icon Orb, Title, Streak & Interactive Log Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Glowing Squircle Orb for Habit Icon
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                habitColor.copy(alpha = 0.32f),
                                                                habitColor.copy(alpha = 0.08f)
                                                            )
                                                        )
                                                    )
                                                    .border(1.dp, habitColor.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(habit.iconEmoji, fontSize = 18.sp)
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = habit.title,
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.5.sp
                                                        ),
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (habitItem.currentStreak > 0) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = if (habitItem.streakFrozenToday) colors.accentCyan.copy(alpha = 0.15f) else Color(0xFFF97316).copy(alpha = 0.15f),
                                                            border = BorderStroke(0.6.dp, if (habitItem.streakFrozenToday) colors.accentCyan.copy(alpha = 0.4f) else Color(0xFFF97316).copy(alpha = 0.4f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(if (habitItem.streakFrozenToday) "🛡️" else "🔥", fontSize = 8.5.sp)
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text(
                                                                    text = "${habitItem.currentStreak}",
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 9.5.sp,
                                                                        fontFamily = FontFamily.Monospace
                                                                    ),
                                                                    color = if (habitItem.streakFrozenToday) colors.accentCyan else Color(0xFFF97316)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(1.5.dp))

                                                Text(
                                                    text = "Target: ${habitItem.targetToday}/day • ${habitItem.totalCompletionsAllTime} total logs",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp
                                                    ),
                                                    color = colors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Today's Status & Interactive +1 Log Button
                                        Surface(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        app.habitRepository.incrementHabitProgress(habit.id)
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (isDone) habitColor.copy(alpha = 0.18f) else (if (isDark) Color(0xFF161A22) else Color(0xFFE2E8F0)),
                                            border = BorderStroke(
                                                0.9.dp,
                                                if (isDone) habitColor.copy(alpha = 0.55f) else (if (isDark) Color(0xFF282F3D) else Color(0xFFCBD5E1))
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (isDone) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = habitColor,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "DONE",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.5.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = habitColor
                                                    )
                                                } else {
                                                    Text(
                                                        text = "${habitItem.completedToday}/${habitItem.targetToday}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.5.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = colors.textPrimary
                                                    )
                                                    Text(
                                                        text = "+1",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.5.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = colors.accentCyan
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // THE 7-DAY VECTOR GRAPH FOR THIS HABIT
                                    Habit7DayVectorGraph(
                                        weeklyHistory = habitItem.weeklyHistory,
                                        habitColor = habitColor,
                                        isDark = isDark,
                                        colors = colors
                                    )
                                }
                            }
                        }

                        if (totalHabitsCount > 3) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = panelBg,
                                border = BorderStroke(1.dp, panelBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onHabitsClick?.invoke() ?: onManageClick() }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "+${totalHabitsCount - 3} MORE HABITS CONFIGURED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.textMuted
                                    )
                                    Text(
                                        text = "VIEW ALL >",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = colors.accentCyan
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── FOOTER: STATUS & ACTIONS ──────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accentGreen)
                        )
                        Text(
                            text = "DISCIPLINE ENGINE ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "HABITS >",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.accentCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onHabitsClick?.invoke() ?: onManageClick() }
                                .padding(vertical = 2.dp)
                        )
                        Text(
                            text = "SHIELDS >",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onManageClick() }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// C. PINNED NOTES COMPONENT WITH REAL NOTES & NOTE SELECTION
