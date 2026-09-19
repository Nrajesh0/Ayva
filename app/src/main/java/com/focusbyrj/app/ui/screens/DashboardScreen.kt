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

import com.focusbyrj.app.ui.components.CustomRestrictionSection
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.focusbyrj.app.ui.theme.MidnightBlack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.scale
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.ui.theme.*
import com.focusbyrj.app.util.DeviceStatsHelper
import com.focusbyrj.app.util.DndHelper
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    restrictions: List<AppRestriction> = emptyList(),
    schedules: List<com.focusbyrj.app.data.FocusSchedule> = emptyList(),
    habits: List<com.focusbyrj.app.data.HabitWithProgress> = emptyList(),
    onToggle: (AppRestriction) -> Unit = {},
    onDelete: (AppRestriction) -> Unit = {},
    onUpdate: (AppRestriction) -> Unit = {},
    isSessionActive: Boolean = false,
    timeRemaining: Long = 25 * 60L,
    initialTime: Long = 25 * 60L,
    onToggleSession: () -> Unit = {},
    onSetTime: (Int) -> Unit = {},
    onOpenRoutines: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onHabitIncrement: (com.focusbyrj.app.data.Habit) -> Unit = {}
) {
    if (isSessionActive) {
        ActiveSessionScreen(timeRemaining = timeRemaining, initialTime = initialTime, onToggleSession = onToggleSession)
    } else {
        NormalDashboard(
            restrictions = restrictions,
            schedules = schedules,
            habits = habits,
            onToggle = onToggle,
            onDelete = onDelete,
            onUpdate = onUpdate,
            timeRemaining = timeRemaining,
            onToggleSession = onToggleSession,
            onSetTime = onSetTime,
            onOpenRoutines = onOpenRoutines,
            onOpenHabits = onOpenHabits,
            onHabitIncrement = onHabitIncrement
        )
    }
}

@Composable
fun NormalDashboard(
    restrictions: List<AppRestriction>,
    schedules: List<com.focusbyrj.app.data.FocusSchedule> = emptyList(),
    habits: List<com.focusbyrj.app.data.HabitWithProgress> = emptyList(),
    onToggle: (AppRestriction) -> Unit,
    onDelete: (AppRestriction) -> Unit = {},
    onUpdate: (AppRestriction) -> Unit = {},
    timeRemaining: Long,
    onToggleSession: () -> Unit,
    onSetTime: (Int) -> Unit,
    onOpenRoutines: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onHabitIncrement: (com.focusbyrj.app.data.Habit) -> Unit = {}
) {
    var editingApp by remember { mutableStateOf<AppRestriction?>(null) }

    if (editingApp != null) {
        EditRestrictionBottomSheet(
            app = editingApp!!,
            onDismiss = { editingApp = null },
            onSave = { updated ->
                onUpdate(updated)
                editingApp = null
            },
            onDelete = { toDelete ->
                onDelete(toDelete)
                editingApp = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            var hasUsageStats by remember { mutableStateOf(com.focusbyrj.app.util.PermissionUtils.hasUsageStatsPermission(context)) }
            var hasOverlay by remember { mutableStateOf(com.focusbyrj.app.util.PermissionUtils.hasOverlayPermission(context)) }
            var isBatteryUnrestricted by remember { mutableStateOf(com.focusbyrj.app.util.PermissionUtils.isIgnoringBatteryOptimizations(context)) }
            var hasNotifications by remember { mutableStateOf(com.focusbyrj.app.util.PermissionUtils.hasNotificationPermission(context)) }
            
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        hasUsageStats = com.focusbyrj.app.util.PermissionUtils.hasUsageStatsPermission(context)
                        hasOverlay = com.focusbyrj.app.util.PermissionUtils.hasOverlayPermission(context)
                        isBatteryUnrestricted = com.focusbyrj.app.util.PermissionUtils.isIgnoringBatteryOptimizations(context)
                        hasNotifications = com.focusbyrj.app.util.PermissionUtils.hasNotificationPermission(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            if (!hasUsageStats || !hasOverlay || !isBatteryUnrestricted || !hasNotifications) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            if (!hasUsageStats) {
                                com.focusbyrj.app.util.PermissionUtils.requestUsageStatsPermission(context)
                            } else if (!hasOverlay) {
                                com.focusbyrj.app.util.PermissionUtils.requestOverlayPermission(context)
                            } else if (!isBatteryUnrestricted) {
                                com.focusbyrj.app.util.PermissionUtils.requestIgnoreBatteryOptimizations(context)
                            } else if (!hasNotifications) {
                                com.focusbyrj.app.util.PermissionUtils.requestNotificationPermission(context)
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val title = when {
                                !hasUsageStats -> "Usage Access Required"
                                !hasOverlay -> "Display Over Apps Required"
                                !isBatteryUnrestricted -> "Battery: Set to 'No Restrictions'"
                                else -> "Notification Permission Required"
                            }
                            val subtitle = when {
                                !hasUsageStats -> "Tap to grant Usage Access to detect running apps."
                                !hasOverlay -> "Tap to allow displaying block overlay over apps."
                                !isBatteryUnrestricted -> "Tap to set Unrestricted battery so Android doesn't kill focus locks."
                                else -> "Tap to allow notifications for block events."
                            }
                            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Top Row: Deep Work Card (Left) & Habits Glance Card (Right) Side-by-Side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DeepWorkCard(
                    timeRemaining = timeRemaining,
                    onToggleSession = onToggleSession,
                    onSetTime = onSetTime,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                DashboardHabitsGlanceCard(
                    habits = habits,
                    onOpenHabits = onOpenHabits,
                    onHabitIncrement = onHabitIncrement,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            RoutinesAndShieldedSection(
                restrictions = restrictions,
                schedules = schedules,
                onOpenRoutines = onOpenRoutines
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your boundaries",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Swipe to edit or delete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (restrictions.isEmpty()) {
            item { EmptyStateView() }
        } else {
            val manualApps = restrictions.filter { !it.isFromRoutine }
            val routineApps = restrictions.filter { it.isFromRoutine }

            if (manualApps.isNotEmpty()) {
                item {
                    Text(
                        text = "MANUALLY ADDED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                items(manualApps, key = { it.packageName }) { app ->
                    SwipeableAppRestrictionCard(
                        app = app,
                        onToggle = { onToggle(app) },
                        onEdit = { editingApp = app },
                        onDelete = { onDelete(app) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (routineApps.isNotEmpty()) {
                if (manualApps.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
                item {
                    Text(
                        text = "ACTIVE FROM ROUTINES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                items(routineApps, key = { it.packageName }) { app ->
                    SwipeableAppRestrictionCard(
                        app = app,
                        onToggle = { onToggle(app) },
                        onEdit = { editingApp = app },
                        onDelete = { onDelete(app) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun DeepWorkCard(
    timeRemaining: Long,
    onToggleSession: () -> Unit,
    onSetTime: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showDialog by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf((timeRemaining / 60).toFloat()) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set Focus Duration") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (sliderValue > 5f) sliderValue -= 5f },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${sliderValue.toInt()}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "min",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        IconButton(
                            onClick = { if (sliderValue < 120f) sliderValue += 5f },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTime(sliderValue.toInt())
                    showDialog = false
                }) { Text("Save", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(22.dp)
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Lock icon badge + Title + Edit Duration icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = "DEEP FOCUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    onClick = { showDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit Duration",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // Duration: Big number + min
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clickable { showDialog = true }
            ) {
                Text(
                    text = "${timeRemaining / 60}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "minutes",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            // Start Button Pill
            Surface(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (!DndHelper.hasDndPermission(context)) {
                        DndHelper.requestDndPermission(context)
                    } else {
                        DndHelper.setDndMode(context, true)
                        onToggleSession()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Start",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ActiveSessionScreen(timeRemaining: Long, initialTime: Long, onToggleSession: () -> Unit) {
    val progress = if (initialTime > 0) (timeRemaining.toFloat() / initialTime.toFloat()) else 0f
    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)
    val context = LocalContext.current
    
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glowOpacity by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOpacity"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        
        Box(
            modifier = Modifier
                .size(400.dp)
                .scale(scale)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = glowOpacity), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DEEP WORK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(48.dp))
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .scale(scale)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = 12.dp.toPx())
                    )
                    
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 68.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (scale > 1f) "Breathe In..." else "Breathe Out...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
            
            Button(
                onClick = {
                    DndHelper.setDndMode(context, false)
                    onToggleSession()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("End Early", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}


@Composable
fun EmptyStateView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No apps locked",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap + to add a distraction boundary",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DashboardHabitsGlanceCard(
    habits: List<com.focusbyrj.app.data.HabitWithProgress>,
    onOpenHabits: () -> Unit,
    onHabitIncrement: (com.focusbyrj.app.data.Habit) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val completedCount = habits.count { it.isCompletedToday }
    val totalCount = habits.size
    val maxStreak = habits.maxOfOrNull { it.currentStreak } ?: 0
    val overallRatio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    val percentText = if (totalCount > 0) "${(overallRatio * 100).toInt()}%" else "0%"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(22.dp)
            )
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onOpenHabits()
            }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: RHYTHMS label + Streak pill + Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "RHYTHMS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (maxStreak > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF97316).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "🔥 ${maxStreak}d",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = Color(0xFFF97316)
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "View all",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Body Row: Left Status Metrics + Right Activity Rings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Metrics
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = percentText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (totalCount == 0) "Tap to start" else if (completedCount == totalCount) "All done ✓" else "$completedCount of $totalCount done",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = if (completedCount == totalCount && totalCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Right: Concentric Activity Rings
                HabitActivityRings(
                    habits = habits,
                    modifier = Modifier.size(52.dp)
                )
            }

            // Bottom Micro Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (totalCount == 0) {
                    Text(
                        "Set daily goals",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        if (completedCount == totalCount) "Daily goal reached!" else "${totalCount - completedCount} remaining today",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (completedCount == totalCount) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HabitActivityRings(
    habits: List<com.focusbyrj.app.data.HabitWithProgress>,
    modifier: Modifier = Modifier
) {
    val topHabits = remember(habits) { habits.take(3) }
    val ringCount = topHabits.size

    val progress0 by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (topHabits.isNotEmpty()) (topHabits[0].completedToday.toFloat() / topHabits[0].targetToday.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "ring0"
    )
    val progress1 by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (topHabits.size > 1) (topHabits[1].completedToday.toFloat() / topHabits[1].targetToday.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "ring1"
    )
    val progress2 by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (topHabits.size > 2) (topHabits[2].completedToday.toFloat() / topHabits[2].targetToday.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "ring2"
    )

    val centerEmoji = remember(habits) {
        habits.firstOrNull { !it.isCompletedToday }?.habit?.iconEmoji
            ?: habits.firstOrNull()?.habit?.iconEmoji
            ?: "🥤"
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = if (ringCount <= 1) 4.5.dp.toPx() else if (ringCount == 2) 3.5.dp.toPx() else 2.8.dp.toPx()
            val spacing = if (ringCount == 2) 4.5.dp.toPx() else 3.8.dp.toPx()
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

            if (ringCount == 0) {
                // Empty subtle track
                val radius = (size.minDimension / 2f) - (strokeWidth / 2f)
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.2f),
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                val progressList = listOf(progress0, progress1, progress2)
                for (i in 0 until ringCount) {
                    val item = topHabits[i]
                    val ringColor = try {
                        Color(android.graphics.Color.parseColor(item.habit.colorHex))
                    } catch (_: Exception) {
                        when (i) {
                            0 -> Color(0xFF38BDF8)
                            1 -> Color(0xFFA855F7)
                            else -> Color(0xFF10B981)
                        }
                    }
                    val currentProgress = progressList[i]
                    val radius = (size.minDimension / 2f) - (strokeWidth / 2f) - (i * spacing)

                    if (radius > strokeWidth / 2f) {
                        // Background track
                        drawCircle(
                            color = ringColor.copy(alpha = 0.18f),
                            radius = radius,
                            style = Stroke(width = strokeWidth)
                        )

                        // Active progress arc
                        if (currentProgress > 0f) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = (currentProgress * 360f).coerceIn(4f, 360f),
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }

        // Center emoji or status symbol
        Text(
            text = centerEmoji,
            fontSize = if (ringCount <= 1) 14.sp else 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RoutinesAndShieldedSection(
    restrictions: List<AppRestriction>,
    schedules: List<com.focusbyrj.app.data.FocusSchedule> = emptyList(),
    onOpenRoutines: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val activeCount = schedules.count { it.isEnabled }
    val totalCount = schedules.size

    Row(
        modifier = Modifier.fillMaxWidth().height(124.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Routines Card (Replaces Streaks Card)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    RoundedCornerShape(22.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onOpenRoutines()
                }
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            "ROUTINES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open Routines",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$activeCount",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (totalCount > 0) "active ($totalCount)" else "active",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (schedules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "Tap to set",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    } else {
                        schedules.take(5).forEach { schedule ->
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (schedule.isEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            if (activeCount > 0) "$activeCount on" else "Paused",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                            color = if (activeCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Shielded Card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    RoundedCornerShape(22.dp)
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        "SHIELDED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeRestrictions = restrictions.filter { it.isRestricted }.take(4)
                    
                    if (activeRestrictions.isEmpty()) {
                        Text(
                            "No apps blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        activeRestrictions.forEachIndexed { index, app ->
                            val pm = context.packageManager
                            val icon = remember(app.packageName) {
                                com.focusbyrj.app.util.ImageUtils.getAppIcon(pm, app.packageName)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .offset(x = (-6 * index).dp)
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.2.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (icon != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(3.dp)
                                    )
                                } else {
                                    val text = app.appName.take(2).uppercase()
                                    Text(
                                        text,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "${restrictions.count { it.isRestricted }} shielded apps",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SwipeableAppRestrictionCard(
    app: AppRestriction,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val maxActionWidthDp = 84.dp
    val maxActionWidthPx = with(density) { maxActionWidthDp.toPx() }

    val currentOffset = offsetX.value
    val isSwipingRight = currentOffset > 1f
    val isSwipingLeft = currentOffset < -1f

    val editProgress = (currentOffset / maxActionWidthPx).coerceIn(0f, 1f)
    val deleteProgress = (-currentOffset / maxActionWidthPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
    ) {
        if (isSwipingRight) {
            Box(
                modifier = Modifier
                    .width(maxActionWidthDp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                        onEdit()
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = editProgress
                        scaleX = 0.75f + 0.25f * editProgress
                        scaleY = 0.75f + 0.25f * editProgress
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Restriction",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (isSwipingLeft) {
            Box(
                modifier = Modifier
                    .width(maxActionWidthDp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                        onDelete()
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = deleteProgress
                        scaleX = 0.75f + 0.25f * deleteProgress
                        scaleY = 0.75f + 0.25f * deleteProgress
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Restriction",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val current = offsetX.value
                                val target = when {
                                    current > maxActionWidthPx * 0.4f -> maxActionWidthPx
                                    current < -maxActionWidthPx * 0.4f -> -maxActionWidthPx
                                    else -> 0f
                                }
                                offsetX.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(
                                    -maxActionWidthPx * 1.15f,
                                    maxActionWidthPx * 1.15f
                                )
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            AppRestrictionCard(
                app = app,
                onToggle = {
                    if (kotlin.math.abs(offsetX.value) > 10f) {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    } else {
                        onToggle()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRestrictionBottomSheet(
    app: AppRestriction,
    onDismiss: () -> Unit,
    onSave: (AppRestriction) -> Unit,
    onDelete: (AppRestriction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var selectedMode by remember { mutableStateOf(app.mode) }
    var restrictionMode by remember { mutableStateOf(if (app.restrictionMode.isNotBlank()) app.restrictionMode else "SIMPLE") }
    var timeLimitMinutes by remember { mutableStateOf(if (app.timeLimitMinutes > 0) app.timeLimitMinutes else 15) }
    var clickLimitCount by remember { mutableStateOf(if (app.clickLimitCount > 0) app.clickLimitCount else 5) }
    var customQuote by remember { mutableStateOf(app.customQuote) }
    var isShieldActive by remember { mutableStateOf(app.isRestricted) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        tonalElevation = 0.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                height = 4.dp,
                width = 44.dp
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val pm = context.packageManager
                val icon = remember(app.packageName) {
                    com.focusbyrj.app.util.ImageUtils.getAppIcon(pm, app.packageName)
                }
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(2).uppercase(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EDIT RESTRICTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                                onDelete(app)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Restriction",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Shield Active",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isShieldActive) "Blocking enabled for this app" else "Blocking is currently paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isShieldActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isShieldActive,
                        onCheckedChange = { isShieldActive = it },
                        modifier = Modifier.scale(0.85f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "BLOCKING STRICTNESS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeSelector(
                    title = "HARD MODE",
                    description = "No bypass allowed",
                    isSelected = selectedMode == "HARD",
                    onClick = { selectedMode = "HARD" },
                    modifier = Modifier.weight(1f)
                )
                ModeSelector(
                    title = "SOFT MODE",
                    description = "10 sec wait bypass",
                    isSelected = selectedMode == "SOFT",
                    onClick = { selectedMode = "SOFT" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            CustomRestrictionSection(
                restrictionMode = restrictionMode,
                onRestrictionModeChange = { restrictionMode = it },
                timeLimitMinutes = timeLimitMinutes,
                onTimeLimitChange = { timeLimitMinutes = it },
                clickLimitCount = clickLimitCount,
                onClickLimitChange = { clickLimitCount = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CUSTOM QUOTE (OPTIONAL)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = customQuote,
                onValueChange = { customQuote = it },
                placeholder = { Text("Why are you blocking this app?", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        val updated = app.copy(
                            mode = selectedMode,
                            restrictionMode = restrictionMode,
                            timeLimitMinutes = if (restrictionMode == "TIME_LIMIT") timeLimitMinutes else 0,
                            clickLimitCount = if (restrictionMode == "CLICK_LIMIT") clickLimitCount else 0,
                            customQuote = customQuote.trim(),
                            isRestricted = isShieldActive
                        )
                        sheetState.hide()
                        onDismiss()
                        onSave(updated)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(27.dp)
            ) {
                Text(
                    "Save Changes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AppRestrictionCard(app: AppRestriction, onToggle: () -> Unit) {
    val isLocked = app.isRestricted
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardColor = if (isLocked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isLocked) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val pm = LocalContext.current.packageManager
                val icon = remember(app.packageName) { com.focusbyrj.app.util.ImageUtils.getAppIcon(pm, app.packageName) }
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(app.appName.take(2).uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isLocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            val badgeText = when (app.restrictionMode) {
                                "TIME_LIMIT" -> if (app.timeLimitMinutes > 0) "${app.timeLimitMinutes}m" else "TIME"
                                "CLICK_LIMIT" -> if (app.clickLimitCount > 0) "${app.clickLimitCount}x" else "LIMIT"
                                else -> app.mode.uppercase()
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Switch(
                checked = isLocked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}
