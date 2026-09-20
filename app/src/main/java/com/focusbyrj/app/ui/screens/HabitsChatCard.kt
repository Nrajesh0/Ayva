/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.data.HabitType
import com.focusbyrj.app.data.HabitWithProgress
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.GamificationHaptics
import com.focusbyrj.app.util.HabitAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HabitsChatCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    onHabitLog: ((Long) -> Unit)? = null,
    onHabitCreated: ((Habit) -> Unit)? = null,
    onOpenFullTracker: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val app = remember { context.applicationContext as FocusApplication }
    val habitRepo = remember { app.habitRepository }

    // Live observing habits from repository
    val activeHabitsWithProgress by habitRepo.activeHabitsWithProgress.collectAsState(initial = emptyList())

    // Card View Mode: false = List of habits, true = Enter a habit form
    var isCreatingHabit by remember { mutableStateOf(false) }

    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (320 + (fontSizeSp - 15f) * 12f).coerceIn(320f, 400f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            // Ayva Avatar
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.focusbyrj.app.R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Main Card Container
            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 20.dp
                ),
                color = if (isDark) Color(0xFF121824) else Color(0xFFF8FAFC),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.15f else 0.25f)
                ),
                shadowElevation = 4.dp
            ) {
                AnimatedContent(
                    targetState = isCreatingHabit,
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { height -> -height / 2 } + fadeOut())
                    },
                    label = "HabitCardModeTransition"
                ) { creating ->
                    if (!creating) {
                        // -------------------------------------------------------------
                        // 1. HABITS LIST VIEW
                        // -------------------------------------------------------------
                        HabitsListView(
                            habits = activeHabitsWithProgress,
                            fontSizeSp = fontSizeSp,
                            isDark = isDark,
                            onPlusClicked = { isCreatingHabit = true },
                            onLogHabit = { habitId ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val updatedLog = habitRepo.incrementHabitProgress(habitId)
                                        val habit = habitRepo.getHabitById(habitId)
                                        if (habit != null) {
                                            val isGoalMet = updatedLog.completedCount >= habit.targetPerDay
                                            HabitAlarmScheduler.scheduleHabitReminder(
                                                context,
                                                habit,
                                                updatedLog.lastCompletedTimestamp,
                                                isGoalCompletedToday = isGoalMet
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            GamificationHaptics.playSuccess(context)
                                            FocusEconomyManager.addRewards(baseXp = 15, baseGold = 5)
                                            onHabitLog?.invoke(habitId)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Error logging habit: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onOpenFullTracker = {
                                if (onOpenFullTracker != null) {
                                    onOpenFullTracker()
                                } else {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra("navigate_to", "habits")
                                        putExtra("NAV_DESTINATION", "habits")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                    } else {
                        // -------------------------------------------------------------
                        // 2. ENTER HABIT (OPTIONS & CREATION) VIEW
                        // -------------------------------------------------------------
                        EnterHabitView(
                            isDark = isDark,
                            fontSizeSp = fontSizeSp,
                            onBack = { isCreatingHabit = false },
                            onOpenFullTracker = {
                                if (onOpenFullTracker != null) {
                                    onOpenFullTracker()
                                } else {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra("navigate_to", "habits")
                                        putExtra("NAV_DESTINATION", "habits")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            onCreateHabit = { newHabit ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val createdId = habitRepo.insertHabit(newHabit)
                                        val scheduledHabit = newHabit.copy(id = createdId)
                                        HabitAlarmScheduler.scheduleHabitReminder(context, scheduledHabit)
                                        withContext(Dispatchers.Main) {
                                            GamificationHaptics.playCelebration(context)
                                            FocusEconomyManager.addRewards(baseXp = 25, baseGold = 10)
                                            Toast.makeText(context, "🎯 Habit '${newHabit.title}' added! (+25 XP)", Toast.LENGTH_SHORT).show()
                                            onHabitCreated?.invoke(scheduledHabit)
                                            isCreatingHabit = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to create habit: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-VIEW 1: HABITS LIST VIEW
// =============================================================================
@Composable
private fun HabitsListView(
    habits: List<HabitWithProgress>,
    fontSizeSp: Float,
    isDark: Boolean,
    onPlusClicked: () -> Unit,
    onLogHabit: (Long) -> Unit,
    onOpenFullTracker: () -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.TrackChanges,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Habit Radar",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizeSp * 1.0f).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                if (habits.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        val completedCount = habits.count { it.isCompletedToday }
                        Text(
                            text = "$completedCount/${habits.size} done",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // The PLUS Button to flip into Enter Habit View
            Surface(
                onClick = onPlusClicked,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Habit",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Empty state
        if (habits.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1A2232) else Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎯", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No habits tracked yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start small! Hydration, posture, or reading daily routines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onPlusClicked,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Habit", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        } else {
            // Habit Cards List
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                habits.forEach { hp ->
                    val habit = hp.habit
                    val habitColor = parseHexColor(habit.colorHex)
                    val isDone = hp.isCompletedToday
                    val progressFraction = hp.progressFraction

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF161E2E) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isDone) habitColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.12f else 0.18f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Emoji Icon Box
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(habitColor.copy(alpha = if (isDark) 0.2f else 0.12f))
                                        .border(1.dp, habitColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = habit.iconEmoji, fontSize = 18.sp)
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Title, Schedule, and Streak
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = habit.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (fontSizeSp * 0.95f).sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (hp.currentStreak > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF97316).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "🔥 ${hp.currentStreak}d",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = Color(0xFFF97316),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Schedule subtext
                                    val scheduleDesc = if (habit.type == HabitType.INTERVAL_WINDOW) {
                                        "Every ${habit.formattedInterval} • ${habit.formattedWindow}"
                                    } else {
                                        "Daily at ${formatTime(habit.fixedReminderHour, habit.fixedReminderMinute)}"
                                    }

                                    Text(
                                        text = scheduleDesc,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Log Action Button (+1 or Done)
                                Surface(
                                    onClick = { onLogHabit(habit.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDone) habitColor else habitColor.copy(alpha = if (isDark) 0.2f else 0.12f),
                                    contentColor = if (isDone) Color.White else habitColor,
                                    border = BorderStroke(1.dp, habitColor.copy(alpha = if (isDone) 1f else 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isDone) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Completed",
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Done",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Log progress",
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "1",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Progress Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progressFraction)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(habitColor)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${hp.completedToday}/${hp.targetToday}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    ),
                                    color = if (isDone) habitColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Footer Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPlusClicked,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Habit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
            }

            TextButton(
                onClick = onOpenFullTracker,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Open Full Tracker", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// =============================================================================
// SUB-VIEW 2: ENTER HABIT (OPTIONS & FORM) VIEW - COMPACT
// =============================================================================
@Composable
private fun EnterHabitView(
    isDark: Boolean,
    fontSizeSp: Float,
    onBack: () -> Unit,
    onOpenFullTracker: (() -> Unit)? = null,
    onCreateHabit: (Habit) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var iconEmoji by remember { mutableStateOf("🥤") }
    var colorHex by remember { mutableStateOf("#0284C7") }
    var habitType by remember { mutableStateOf(HabitType.INTERVAL_WINDOW) }
    var targetPerDay by remember { mutableIntStateOf(6) }

    var windowStartHour by remember { mutableIntStateOf(8) }
    var windowStartMinute by remember { mutableIntStateOf(0) }
    var windowEndHour by remember { mutableIntStateOf(20) }
    var windowEndMinute by remember { mutableIntStateOf(0) }

    val activeWindowMinutes by remember {
        derivedStateOf {
            val startMins = windowStartHour * 60 + windowStartMinute
            val endMins = windowEndHour * 60 + windowEndMinute
            if (endMins > startMins) {
                endMins - startMins
            } else {
                (24 * 60 - startMins + endMins).coerceAtLeast(15)
            }
        }
    }

    var intervalHours by remember { mutableIntStateOf(2) }
    var intervalMinutes by remember { mutableIntStateOf(0) }
    var fixedHour by remember { mutableIntStateOf(9) }
    var fixedMinute by remember { mutableIntStateOf(0) }

    // Toggle for more customization (emojis, colors, note, window times)
    var showMoreOptions by remember { mutableStateOf(false) }

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
        val clamped = targetStep.coerceIn(15, activeWindowMinutes)
        intervalHours = clamped / 60
        intervalMinutes = clamped % 60
        targetPerDay = (activeWindowMinutes / clamped).coerceIn(1, 24)
    }

    val activeColor = remember(colorHex) {
        parseHexColor(colorHex)
    }

    // Curated quick emojis + categories when expanded
    val quickEmojis = listOf("🥤", "💊", "📖", "🧘", "🏃", "💻", "✨", "💧")
    val emojiCategories = remember {
        listOf(
            "Popular" to listOf("🥤", "🧋", "🧊", "🌊", "💊", "🧘", "🏃", "🏋️", "⚡", "🧠", "🎯", "📖", "🌙", "😴", "☀️", "✨"),
            "🥤 Drinks" to listOf("🥤", "🧋", "🧊", "🌊", "🫖", "☕", "🍵", "🍶", "🥥", "🥑", "🫐", "🍓", "🥗", "🍎"),
            "🧘 Health" to listOf("🧘", "🏃", "🏋️", "🚴", "🚶", "🧗", "🏊", "🤸", "💪", "🛹", "💊", "🩺", "🩹"),
            "⚡ Focus" to listOf("⚡", "🧠", "🎯", "📖", "✍️", "💻", "🎧", "💡", "🚀", "🎨", "🎸", "📚", "📝"),
            "🌙 Daily" to listOf("🌙", "😴", "🛌", "🚿", "🪥", "🧴", "🪴", "🐕", "🐈", "☀️", "🌅", "🕯️", "⏰", "✨")
        )
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val displayedEmojis = remember(selectedCategoryIndex) {
        emojiCategories[selectedCategoryIndex].second
    }

    // Quick colors
    val quickColors = listOf("#0284C7", "#3B82F6", "#8B5CF6", "#EC4899", "#10B981", "#F59E0B")
    val allColors = listOf(
        "#0284C7", "#3B82F6", "#6366F1", "#8B5CF6", "#A855F7",
        "#EC4899", "#F43F5E", "#F97316", "#EAB308", "#10B981", "#14B8A6", "#06B6D4"
    )

    // Preset Habit Templates
    val presets = remember {
        listOf(
            PresetChoice("Drink Water", "🥤", "#0284C7", HabitType.INTERVAL_WINDOW, 8, 1, 30, 8, 0, 20, 0, 9, 0),
            PresetChoice("Medicine", "💊", "#F43F5E", HabitType.ONCE_DAILY, 1, 2, 0, 8, 0, 20, 0, 9, 0),
            PresetChoice("Read 20m", "📖", "#A855F7", HabitType.ONCE_DAILY, 1, 2, 0, 8, 0, 20, 0, 21, 0),
            PresetChoice("Posture", "🧍", "#10B981", HabitType.INTERVAL_WINDOW, 4, 2, 15, 10, 0, 19, 0, 9, 0),
            PresetChoice("Meditate", "🧘", "#F59E0B", HabitType.ONCE_DAILY, 1, 2, 0, 8, 0, 20, 0, 7, 30),
            PresetChoice("Workout", "🏃", "#3B82F6", HabitType.ONCE_DAILY, 1, 2, 0, 8, 0, 20, 0, 7, 0),
            PresetChoice("Deep Work", "💻", "#6366F1", HabitType.INTERVAL_WINDOW, 3, 2, 30, 9, 0, 18, 0, 10, 0)
        )
    }

    val cardBg = if (isDark) Color(0xFF161E2E) else Color(0xFFFFFFFF)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.16f else 0.12f))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // -------------------------------------------------------------
        // COMPACT HEADER: Back, Title, Full Editor, Close
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Habit",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizeSp * 1.0f).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onOpenFullTracker != null) {
                    TextButton(
                        onClick = onOpenFullTracker,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = activeColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Full Screen",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = activeColor,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 1. QUICK PRESETS PILL ROW (Compact 1-Tap)
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = title == preset.title
                Surface(
                    onClick = {
                        title = preset.title
                        iconEmoji = preset.emoji
                        colorHex = preset.colorHex
                        habitType = preset.type
                        targetPerDay = preset.target
                        intervalHours = preset.intervalH
                        intervalMinutes = preset.intervalM
                        windowStartHour = preset.wStartH
                        windowStartMinute = preset.wStartM
                        windowEndHour = preset.wEndH
                        windowEndMinute = preset.wEndM
                        fixedHour = preset.fixedH
                        fixedMinute = preset.fixedM
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) activeColor else (if (isDark) Color(0xFF1E2838) else Color(0xFFF1F5F9)),
                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(preset.emoji, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            preset.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. HABIT TITLE INPUT (Slim & Clean)
        // -------------------------------------------------------------
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = {
                Text("Habit title (e.g. Drink water, Read)", fontSize = 12.sp)
            },
            leadingIcon = {
                Text(iconEmoji, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg
            )
        )

        // -------------------------------------------------------------
        // 3. SCHEDULE FREQUENCY: [Once Daily] vs [Periodic Window]
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Once Daily Pill
            Surface(
                onClick = {
                    habitType = HabitType.ONCE_DAILY
                    targetPerDay = 1
                },
                shape = RoundedCornerShape(8.dp),
                color = if (habitType == HabitType.ONCE_DAILY) activeColor.copy(alpha = 0.18f) else (if (isDark) Color(0xFF161E2E) else Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, if (habitType == HabitType.ONCE_DAILY) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (habitType == HabitType.ONCE_DAILY) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Once Daily",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (habitType == HabitType.ONCE_DAILY) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.5.sp
                        ),
                        color = if (habitType == HabitType.ONCE_DAILY) activeColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Periodic Window Pill
            Surface(
                onClick = {
                    habitType = HabitType.INTERVAL_WINDOW
                    if (targetPerDay <= 1) targetPerDay = 6
                },
                shape = RoundedCornerShape(8.dp),
                color = if (habitType == HabitType.INTERVAL_WINDOW) activeColor.copy(alpha = 0.18f) else (if (isDark) Color(0xFF161E2E) else Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, if (habitType == HabitType.INTERVAL_WINDOW) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (habitType == HabitType.INTERVAL_WINDOW) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Periodic Window",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (habitType == HabitType.INTERVAL_WINDOW) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.5.sp
                        ),
                        color = if (habitType == HabitType.INTERVAL_WINDOW) activeColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 4. TIMING & TARGET CONTROLS (Dense & Streamlined)
        // -------------------------------------------------------------
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = cardBg,
            border = cardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (habitType == HabitType.ONCE_DAILY) {
                    // Time selector row + quick chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "⏰ ${formatTime(fixedHour, fixedMinute)} (change)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = activeColor,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                    }

                    // Quick Pick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple("8 AM", 8, 0),
                            Triple("12 PM", 12, 0),
                            Triple("6 PM", 18, 0),
                            Triple("9 PM", 21, 0)
                        ).forEach { (label, h, m) ->
                            val selected = fixedHour == h && fixedMinute == m
                            Surface(
                                onClick = {
                                    fixedHour = h
                                    fixedMinute = m
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, if (selected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Periodic Window: Target row with stepping + quick chips
                    val intervalDisplay = when {
                        intervalHours > 0 && intervalMinutes > 0 -> "Every ${intervalHours}h ${intervalMinutes}m"
                        intervalHours > 0 -> "Every ${intervalHours}h"
                        else -> "Every ${intervalMinutes}m"
                    }
                    val winHours = activeWindowMinutes / 60
                    val winMinsRem = activeWindowMinutes % 60
                    val winDurationText = if (winMinsRem > 0) "${winHours}h ${winMinsRem}m" else "${winHours}h"

                    // Target & Steppers Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Target: ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                text = "${targetPerDay}x/day",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = activeColor, fontSize = 11.sp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                onClick = { onTargetChanged(targetPerDay - 1) },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }
                            // Quick chips (2x, 4x, 6x, 8x)
                            listOf(2, 4, 6, 8).forEach { count ->
                                val selected = targetPerDay == count
                                Surface(
                                    onClick = { onTargetChanged(count) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${count}x",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                            }
                            Surface(
                                onClick = { onTargetChanged(targetPerDay + 1) },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    // Interval & Window summary in 1 compact pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = activeColor.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, activeColor.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ $intervalDisplay · $winDurationText window",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Surface(
                                    onClick = { onIntervalStep(-1) },
                                    shape = RoundedCornerShape(4.dp),
                                    color = activeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(10.dp), tint = activeColor)
                                    }
                                }
                                Surface(
                                    onClick = { onIntervalStep(1) },
                                    shape = RoundedCornerShape(4.dp),
                                    color = activeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(10.dp), tint = activeColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 5. QUICK EMOJI & COLOR ROW (Slim 1-line picker)
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Emojis (Compact strip)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickEmojis.forEach { emoji ->
                    val isSelected = iconEmoji == emoji
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) activeColor.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .clickable { iconEmoji = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Colors (Compact dots)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                quickColors.forEach { hex ->
                    val color = parseHexColor(hex)
                    val isSelected = colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex }
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 6. COLLAPSIBLE "MORE CUSTOMIZATION" SECTION (Hidden by default)
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMoreOptions = !showMoreOptions }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showMoreOptions) "▲ Less options" else "▼ More options (Window, Notes, 80+ Emojis)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = activeColor,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        AnimatedVisibility(visible = showMoreOptions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Optional Note / Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Details / Motivation (optional)", fontSize = 11.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = cardBg
                    )
                )

                // Window Start & End time pickers (only relevant if INTERVAL_WINDOW)
                if (habitType == HabitType.INTERVAL_WINDOW) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Window: ${formatTime(windowStartHour, windowStartMinute)} → ${formatTime(windowEndHour, windowEndMinute)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Start", fontSize = 10.sp, color = activeColor)
                            }
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
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("End", fontSize = 10.sp, color = activeColor)
                            }
                        }
                    }
                }

                // Full Category Emoji Picker (LazyRow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    emojiCategories.forEachIndexed { idx, (catName, _) ->
                        Surface(
                            onClick = { selectedCategoryIndex = idx },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedCategoryIndex == idx) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(0.8.dp, if (selectedCategoryIndex == idx) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Text(
                                catName,
                                fontSize = 9.5.sp,
                                color = if (selectedCategoryIndex == idx) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 1.dp)
                ) {
                    items(displayedEmojis) { emoji ->
                        val isSelected = iconEmoji == emoji
                        Surface(
                            onClick = { iconEmoji = emoji },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) activeColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // All 12 Colors
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 1.dp)
                ) {
                    items(allColors) { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 7. COMPACT ACTION BUTTONS: Cancel & Add Habit
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
            }

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val newHabit = Habit(
                            title = title.trim(),
                            description = description.trim(),
                            iconEmoji = iconEmoji,
                            colorHex = colorHex,
                            type = habitType,
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
                        onCreateHabit(newHabit)
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier.weight(1.5f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add Habit (+25 XP)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                )
            }
        }
    }
}

// Data class helper for quick preset selection
private data class PresetChoice(
    val title: String,
    val emoji: String,
    val colorHex: String,
    val type: HabitType,
    val target: Int,
    val intervalH: Int,
    val intervalM: Int = 0,
    val wStartH: Int = 8,
    val wStartM: Int = 0,
    val wEndH: Int = 20,
    val wEndM: Int = 0,
    val fixedH: Int = 9,
    val fixedM: Int = 0
)

private fun parseHexColor(hex: String, defaultColor: Color = Color(0xFF38BDF8)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        defaultColor
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0 || hour == 12) 12 else hour % 12
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format(Locale.getDefault(), "%d:%02d %s", h, minute, amPm)
}
