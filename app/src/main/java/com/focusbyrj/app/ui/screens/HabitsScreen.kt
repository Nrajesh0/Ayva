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

package com.focusbyrj.app.ui.screens

import androidx.activity.compose.BackHandler
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitDaySummary
import com.focusbyrj.app.data.HabitRepository
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.HabitWithProgress
import com.focusbyrj.app.ui.viewmodels.HabitViewModel
import com.focusbyrj.app.util.FocusEconomyManager
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    habitViewModel: HabitViewModel,
    onBack: (() -> Unit)? = null,
    initialOpenCreate: Boolean = false
) {
    val habitsWithProgress by habitViewModel.habitsWithProgress.collectAsStateWithLifecycle()
    val economyProfile by FocusEconomyManager.profileFlow.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var showEditorSheet by remember { mutableStateOf(initialOpenCreate) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var habitToDelete by remember { mutableStateOf<Habit?>(null) }
    var showFreezeDialog by remember { mutableStateOf(false) }

    val completedCount = habitsWithProgress.count { it.isCompletedToday }
    val totalCount = habitsWithProgress.size
    val overallRatio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Habits & Rhythms",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (totalCount == 0) "Build lasting consistency" else "$completedCount of $totalCount completed today",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Streak Freeze Shield Pill
                    Surface(
                        onClick = { showFreezeDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${economyProfile.streakFreezes}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0284C7)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Minimal Apple-style Circular Plus Button
                    Surface(
                        onClick = {
                            editingHabit = null
                            showEditorSheet = true
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "New Habit",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Overall Daily Summary Card
            item {
                HabitProgressHeroCard(
                    completedCount = completedCount,
                    totalCount = totalCount,
                    progressRatio = overallRatio,
                    longestActiveStreak = habitsWithProgress.maxOfOrNull { it.currentStreak } ?: 0,
                    totalCompletionsAllTime = habitsWithProgress.sumOf { it.totalCompletionsAllTime }
                )
            }

            // 2. Preset Quick Picks (if user has few habits or wants inspiration)
            if (totalCount < 3) {
                item {
                    HabitPresetsRow(
                        onSelectPreset = { preset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            habitViewModel.addHabit(preset)
                        }
                    )
                }
            }

            // 3. List of Active Habits
            if (habitsWithProgress.isEmpty()) {
                item {
                    EmptyHabitsCard(
                        onAddClick = {
                            editingHabit = null
                            showEditorSheet = true
                        }
                    )
                }
            } else {
                items(habitsWithProgress, key = { it.habit.id }) { item ->
                    HabitCard(
                        item = item,
                        onIncrement = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            habitViewModel.incrementProgress(item.habit)
                        },
                        onDecrement = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            habitViewModel.decrementProgress(item.habit)
                        },
                        onEdit = {
                            editingHabit = item.habit
                            showEditorSheet = true
                        },
                        onDelete = {
                            habitToDelete = item.habit
                        }
                    )
                }
            }
        }
    }

    // Edit / Create Habit Bottom Sheet
    if (showEditorSheet) {
        HabitEditorBottomSheet(
            habit = editingHabit,
            onDismiss = {
                showEditorSheet = false
                editingHabit = null
            },
            onSave = { habitToSave ->
                if (editingHabit == null) {
                    habitViewModel.addHabit(habitToSave)
                } else {
                    habitViewModel.updateHabit(habitToSave)
                }
                showEditorSheet = false
                editingHabit = null
            }
        )
    }

    // Delete Confirmation Dialog
    habitToDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Delete Habit?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    "Are you sure you want to remove \"${habit.title}\"? All historical streak logs for this habit will be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        habitViewModel.deleteHabit(habit)
                        habitToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Streak Freeze Vault Dialog
    if (showFreezeDialog) {
        AlertDialog(
            onDismissRequest = { showFreezeDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = { Text("🛡️", fontSize = 38.sp) },
            title = {
                Text(
                    text = "Streak Freeze Vault",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "A Streak Freeze automatically protects your streak from breaking if you miss your habits tomorrow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Active Shields",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${economyProfile.streakFreezes} Available",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0284C7)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Current Gold",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${economyProfile.gold} 🪙",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Cost: 150 Gold per shield",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = FocusEconomyManager.buyStreakFreeze(150)
                        if (success) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    enabled = economyProfile.gold >= 150,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Buy (+1 Shield)", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreezeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun HabitProgressHeroCard(
    completedCount: Int,
    totalCount: Int,
    progressRatio: Float,
    longestActiveStreak: Int,
    totalCompletionsAllTime: Int = 0
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio,
        animationSpec = tween(600),
        label = "heroProgress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Header Category / Capsule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TODAY'S RHYTHM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )

                    if (longestActiveStreak > 0) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF97316).copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, Color(0xFFF97316).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔥", fontSize = 9.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$longestActiveStreak d",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFFEA580C)
                                )
                            }
                        }
                    }

                    if (totalCompletionsAllTime > 0) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✨", fontSize = 9.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$totalCompletionsAllTime",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val desc = when {
                    totalCount == 0 -> "Tap + to build daily rituals & micro-habits"
                    progressRatio >= 1f -> "All daily rituals completed! Outstanding momentum."
                    completedCount == 0 -> "$totalCount habits scheduled for today."
                    else -> "$completedCount of $totalCount completed today."
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = (-0.1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Minimal Apple Watch-inspired Activity Ring
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    val stroke = 5.5.dp.toPx()
                    // Soft Track Ring
                    drawCircle(
                        color = Color(0xFF0284C7).copy(alpha = 0.12f),
                        style = Stroke(width = stroke)
                    )
                    // Active Gradient Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF38BDF8),
                                Color(0xFF0284C7),
                                Color(0xFF38BDF8)
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = (animatedProgress * 360f).coerceAtLeast(1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun HabitPresetsRow(onSelectPreset: (Habit) -> Unit) {
    val presets = remember { HabitRepository.getPresetHabits() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "QUICK START PRESETS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(presets) { preset ->
                Surface(
                    onClick = { onSelectPreset(preset) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(preset.iconEmoji, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.1).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HabitCard(
    item: HabitWithProgress,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val habit = item.habit
    val context = LocalContext.current
    val isDone = item.isCompletedToday
    val habitColor = remember(habit.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(habit.colorHex))
        } catch (_: Exception) {
            Color(0xFF0284C7)
        }
    }
    var showMenu by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = item.progressFraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "itemProgress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDone) 0.28f else 0.18f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    if (isDone) habitColor.copy(alpha = 0.55f) else habitColor.copy(alpha = 0.25f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Main Interactive Row: [Icon + Info] ... [Tactile Smart Ring / Pill]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Zone: Glowing Squircle Orb + Habit Title & Pill Badges
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 2026 Chromatic Orb
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        habitColor.copy(alpha = 0.25f),
                                        habitColor.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .border(1.dp, habitColor.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(habit.iconEmoji, fontSize = 19.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = habit.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (item.currentStreak > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (item.streakFrozenToday) Color(0xFF0284C7).copy(alpha = 0.14f) else Color(0xFFF97316).copy(alpha = 0.14f),
                                    border = BorderStroke(0.7.dp, if (item.streakFrozenToday) Color(0xFF38BDF8).copy(alpha = 0.35f) else Color(0xFFF97316).copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (item.streakFrozenToday) "🛡️" else "🔥", fontSize = 9.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${item.currentStreak}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = if (item.streakFrozenToday) Color(0xFF0284C7) else Color(0xFFEA580C)
                                        )
                                    }
                                }
                            }
                        }

                        val scheduleInfo = when (habit.type) {
                            HabitType.ONCE_DAILY -> {
                                val hr = habit.fixedReminderHour
                                val min = String.format(Locale.US, "%02d", habit.fixedReminderMinute)
                                val amPm = if (hr >= 12) "PM" else "AM"
                                val displayHr = if (hr % 12 == 0) 12 else hr % 12
                                "Daily • $displayHr:$min $amPm"
                            }
                            HabitType.INTERVAL_WINDOW -> {
                                "Every ${habit.formattedInterval} • ${habit.formattedWindow}"
                            }
                        }

                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = scheduleInfo,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = (-0.1).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Zone: 2026 Interactive Radial Tap Target + Quick Overflow Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Tactile Radial Progress Button
                    Surface(
                        onClick = onIncrement,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDone) habitColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDone) habitColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Mini Radial Meter inside the button
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val strokeWidth = 2.6.dp.toPx()
                                    // Track
                                    drawCircle(
                                        color = habitColor.copy(alpha = 0.18f),
                                        style = Stroke(width = strokeWidth)
                                    )
                                    // Arc
                                    drawArc(
                                        color = habitColor,
                                        startAngle = -90f,
                                        sweepAngle = (animatedProgress * 360f).coerceAtLeast(1f),
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }

                                if (isDone) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = habitColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (isDone) "Done" else "${item.completedToday}/${item.targetToday}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = if (isDone) habitColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Options Dropdown Menu Anchor
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (item.completedToday > 0) {
                                DropdownMenuItem(
                                    text = { Text("Undo 1 Log (-1)") },
                                    onClick = {
                                        showMenu = false
                                        onDecrement()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit Habit") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Preview Floating Nudge") },
                                onClick = {
                                    showMenu = false
                                    com.focusbyrj.app.service.HabitFloatingOverlayManager.showHabitOverlay(
                                        context = context,
                                        habit = habit,
                                        completedCount = item.completedToday,
                                        targetCount = habit.targetPerDay,
                                        currentStreak = item.currentStreak
                                    )
                                },
                                leadingIcon = {
                                    Text("✨", fontSize = 15.sp)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Delete Habit", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Bottom 2026 Micro-Heat Sparkline (Slim, glowing 7-day pill strip)
            if (item.weeklyHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(9.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 7 Compact Laser Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.weeklyHistory.forEach { daySummary ->
                            val isCompleted = daySummary.isCompleted
                            val isPartial = daySummary.completedCount > 0 && !isCompleted
                            val isToday = daySummary.isToday

                            val pillColor = when {
                                isCompleted -> habitColor
                                isPartial -> habitColor.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 15.dp, height = 5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(pillColor)
                                        .then(
                                            if (isToday) Modifier.border(0.9.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                                            else Modifier
                                        )
                                )

                                Text(
                                    text = daySummary.dayOfWeekLetter,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // Trailing Analytics Summary
                    Text(
                        text = "${item.totalCompletionsAllTime} total logs",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHabitsCard(onAddClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌱", fontSize = 32.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Habits Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Track hydration, reading, stretching, or daily focus with clean gentle reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create First Habit", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorBottomSheet(
    habit: Habit?,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible

    BackHandler(enabled = isImeVisible) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    var title by remember(habit) { mutableStateOf(habit?.title ?: "") }
    var description by remember(habit) { mutableStateOf(habit?.description ?: "") }
    var iconEmoji by remember(habit) { mutableStateOf(habit?.iconEmoji ?: "🥤") }
    var colorHex by remember(habit) { mutableStateOf(habit?.colorHex ?: "#0284C7") }
    var type by remember(habit) { mutableStateOf(habit?.type ?: HabitType.INTERVAL_WINDOW) }
    var targetPerDay by remember(habit) { mutableIntStateOf(habit?.targetPerDay ?: 6) }

    var windowStartHour by remember(habit) { mutableIntStateOf(habit?.windowStartHour ?: 8) }
    var windowStartMinute by remember(habit) { mutableIntStateOf(habit?.windowStartMinute ?: 0) }
    var windowEndHour by remember(habit) { mutableIntStateOf(habit?.windowEndHour ?: 20) }
    var windowEndMinute by remember(habit) { mutableIntStateOf(habit?.windowEndMinute ?: 0) }

    val activeWindowMinutes by remember {
        derivedStateOf {
            val startMins = windowStartHour * 60 + windowStartMinute
            val endMins = windowEndHour * 60 + windowEndMinute
            if (endMins > startMins) {
                (endMins - startMins).coerceAtLeast(30)
            } else {
                (24 * 60 - startMins + endMins).coerceAtLeast(30)
            }
        }
    }

    val initialIntervalMins = habit?.let { it.intervalHours * 60 + it.intervalMinutes }
        ?: ((if (20 > 8) (20 - 8) * 60 else 12 * 60) / 6) // default 120m = 2h

    var intervalHours by remember(habit) { mutableIntStateOf(initialIntervalMins / 60) }
    var intervalMinutes by remember(habit) { mutableIntStateOf(initialIntervalMins % 60) }

    var fixedHour by remember(habit) { mutableIntStateOf(habit?.fixedReminderHour ?: 9) }
    var fixedMinute by remember(habit) { mutableIntStateOf(habit?.fixedReminderMinute ?: 0) }

    fun autoSplitInterval(target: Int) {
        val totalWin = activeWindowMinutes
        val step = (totalWin / target.coerceAtLeast(1)).coerceAtLeast(5)
        intervalHours = step / 60
        intervalMinutes = step % 60
    }

    fun onTargetChanged(newTarget: Int) {
        val clamped = newTarget.coerceIn(1, 24)
        targetPerDay = clamped
        autoSplitInterval(clamped)
    }

    fun onIntervalStep(direction: Int) {
        val currentTotal = (intervalHours * 60 + intervalMinutes)
        val step = if (currentTotal <= 90) 15 else 30
        val targetStep = if (direction > 0) {
            ((currentTotal / step) + 1) * step
        } else {
            val prev = ((currentTotal - 1) / step) * step
            prev.coerceAtLeast(15)
        }
        val safeWindow = activeWindowMinutes.coerceAtLeast(30)
        val clamped = targetStep.coerceIn(15, safeWindow)
        intervalHours = clamped / 60
        intervalMinutes = clamped % 60
        targetPerDay = (safeWindow / clamped).coerceIn(1, 24)
    }

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val activeColor = remember(colorHex) {
        try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            Color(0xFF0284C7)
        }
    }

    val emojiCategories = remember {
        listOf(
            "Popular" to listOf("🥤", "🧋", "🧊", "🌊", "💊", "🧘", "🏃", "🏋️", "⚡", "🧠", "🎯", "📖", "🌙", "😴", "☀️", "✨"),
            "🥤 Drinks" to listOf("🥤", "🧋", "🧊", "🌊", "🫖", "☕", "🍵", "🍶", "🥥", "🥑", "🫐", "🍓", "🥗", "🍎", "🥦", "🍳"),
            "🧘 Health" to listOf("🧘", "🏃", "🏋️", "🚴", "🚶", "🧗", "🏊", "🤸", "💪", "🛹", "💊", "🧪", "🩺", "🩹", "🫀"),
            "⚡ Focus" to listOf("⚡", "🧠", "🎯", "📖", "✍️", "💻", "🎧", "💡", "🚀", "♟️", "🎨", "🎸", "📚", "📝", "🔬"),
            "🌙 Daily" to listOf("🌙", "😴", "🛌", "🚿", "🪥", "🧴", "🪴", "🐕", "🐈", "☀️", "🌅", "🕯️", "🧹", "⏰", "✨")
        )
    }

    val displayedEmojis = remember(selectedCategoryIndex) {
        emojiCategories[selectedCategoryIndex].second
    }

    val colors = listOf(
        "#0284C7", "#3B82F6", "#6366F1", "#8B5CF6", "#A855F7",
        "#EC4899", "#F43F5E", "#F97316", "#EAB308", "#10B981", "#14B8A6", "#06B6D4"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title on Left, Action on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (habit == null) "New Habit" else "Edit Habit",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val newHabit = (habit ?: Habit(title = "")).copy(
                                title = title.trim(),
                                description = description.trim(),
                                iconEmoji = iconEmoji,
                                colorHex = colorHex,
                                type = type,
                                targetPerDay = targetPerDay,
                                intervalHours = intervalHours,
                                intervalMinutes = intervalMinutes,
                                fixedReminderHour = fixedHour,
                                fixedReminderMinute = fixedMinute,
                                windowStartHour = windowStartHour,
                                windowStartMinute = windowStartMinute,
                                windowEndHour = windowEndHour,
                                windowEndMinute = windowEndMinute
                            )
                            onSave(newHabit)
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeColor,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (habit == null) "Create" else "Save",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title Input Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g. Hydrate & Glow") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Details / Motivation Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Details (Optional)") },
                placeholder = { Text("Motivation or notes") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Icon & Category Section
            Text(
                text = "Icon & Category",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = activeColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                emojiCategories.forEachIndexed { idx, (catName, _) ->
                    FilterChip(
                        selected = selectedCategoryIndex == idx,
                        onClick = { selectedCategoryIndex = idx },
                        label = { Text(catName, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selected Category Emoji Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(displayedEmojis) { emoji ->
                    val isSelected = iconEmoji == emoji
                    Surface(
                        onClick = { iconEmoji = emoji },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) activeColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.8.dp,
                            color = if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Schedule Rhythm Category
            Text(
                text = "Schedule Rhythm",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = activeColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = type == HabitType.ONCE_DAILY,
                    onClick = {
                        type = HabitType.ONCE_DAILY
                        targetPerDay = 1
                    },
                    label = { Text("Once Daily") },
                    shape = RoundedCornerShape(12.dp)
                )

                FilterChip(
                    selected = type == HabitType.INTERVAL_WINDOW,
                    onClick = {
                        type = HabitType.INTERVAL_WINDOW
                        if (targetPerDay <= 1) targetPerDay = 6
                    },
                    label = { Text("Interval Window") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rhythm Settings Row (Matching Due Date & Time / Settings Row pattern)
            if (type == HabitType.ONCE_DAILY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reminder Time",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    fixedHour = h
                                    fixedMinute = m
                                },
                                fixedHour,
                                fixedMinute,
                                false
                            ).show()
                        }
                    ) {
                        val hrStr = if (fixedHour % 12 == 0) "12" else "${fixedHour % 12}"
                        val amPm = if (fixedHour >= 12) "PM" else "AM"
                        Text(
                            text = "$hrStr:${String.format(Locale.US, "%02d", fixedMinute)} $amPm",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = activeColor
                            )
                        )
                    }
                }
            } else {
                val currentTotalMins = intervalHours * 60 + intervalMinutes
                val intervalDisplay = when {
                    intervalHours > 0 && intervalMinutes > 0 -> "Every ${intervalHours}h ${intervalMinutes}m"
                    intervalHours > 0 -> "Every ${intervalHours}h"
                    else -> "Every ${intervalMinutes}m"
                }

                val windowHours = activeWindowMinutes / 60
                val windowMinsRem = activeWindowMinutes % 60
                val windowDurationText = if (windowMinsRem > 0) "${windowHours}h ${windowMinsRem}m" else "${windowHours}h"

                val autoPartMins = (activeWindowMinutes / targetPerDay.coerceAtLeast(1)).coerceAtLeast(5)
                val autoH = autoPartMins / 60
                val autoM = autoPartMins % 60
                val autoPartText = when {
                    autoH > 0 && autoM > 0 -> "${autoH}h ${autoM}m"
                    autoH > 0 -> "${autoH}h"
                    else -> "${autoM}m"
                }
                val isAutoSynced = (currentTotalMins == autoPartMins)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Repeat Frequency",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { onIntervalStep(-1) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            intervalDisplay,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = activeColor
                            )
                        )
                        Surface(
                            onClick = { onIntervalStep(1) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Daily Target",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { onTargetChanged(targetPerDay - 1) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            "$targetPerDay times",
                            modifier = Modifier.padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = activeColor
                            )
                        )
                        Surface(
                            onClick = { onTargetChanged(targetPerDay + 1) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-cut calculation banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = activeColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "⚡",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "$windowDurationText window cut into $targetPerDay parts (~$autoPartText each)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isAutoSynced) {
                            Surface(
                                onClick = { autoSplitInterval(targetPerDay) },
                                shape = RoundedCornerShape(6.dp),
                                color = activeColor,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "Auto-Split",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Active Window",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        windowStartHour = h
                                        windowStartMinute = m
                                        autoSplitInterval(targetPerDay)
                                    },
                                    windowStartHour,
                                    windowStartMinute,
                                    false
                                ).show()
                            }
                        ) {
                            val startHr = if (windowStartHour % 12 == 0) 12 else windowStartHour % 12
                            val startAmPm = if (windowStartHour >= 12) "PM" else "AM"
                            val startMin = String.format(Locale.US, "%02d", windowStartMinute)
                            Text(
                                text = "$startHr:$startMin $startAmPm",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeColor
                                )
                            )
                        }

                        Text(
                            "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        windowEndHour = h
                                        windowEndMinute = m
                                        autoSplitInterval(targetPerDay)
                                    },
                                    windowEndHour,
                                    windowEndMinute,
                                    false
                                ).show()
                            }
                        ) {
                            val endHr = if (windowEndHour % 12 == 0) 12 else windowEndHour % 12
                            val endAmPm = if (windowEndHour >= 12) "PM" else "AM"
                            val endMin = String.format(Locale.US, "%02d", windowEndMinute)
                            Text(
                                text = "$endHr:$endMin $endAmPm",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeColor
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Color Theme Palette
            Text(
                text = "Color Theme",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = activeColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(colors) { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = colorHex.equals(hex, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val newHabit = (habit ?: Habit(title = "")).copy(
                            title = title.trim(),
                            description = description.trim(),
                            iconEmoji = iconEmoji,
                            colorHex = colorHex,
                            type = type,
                            targetPerDay = targetPerDay,
                            intervalHours = intervalHours,
                            intervalMinutes = intervalMinutes,
                            fixedReminderHour = fixedHour,
                            fixedReminderMinute = fixedMinute,
                            windowStartHour = windowStartHour,
                            windowStartMinute = windowStartMinute,
                            windowEndHour = windowEndHour,
                            windowEndMinute = windowEndMinute
                        )
                        onSave(newHabit)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = if (habit == null) Icons.Filled.Add else Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (habit == null) "Create Habit" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
