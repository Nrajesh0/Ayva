/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

import com.focusbyrj.app.ui.components.settingsSearchTarget
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.util.SettingsPreferenceRules
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.res.painterResource
import android.widget.Toast
import com.focusbyrj.app.util.AppIconManager
import com.focusbyrj.app.util.AppThemeColor
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.FocusStatsManager
import com.focusbyrj.app.util.HeatmapTheme
import com.focusbyrj.app.util.ThemeMode

data class LaunchTabOption(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val description: String
)

val launchTabOptions = listOf(
    LaunchTabOption("dashboard", "Focus (Apps)", Icons.Filled.Home, "App blocker & active focus timer"),
    LaunchTabOption("todos", "To-Do (Tasks)", Icons.Filled.CheckCircle, "Tasks, checklists & occasions"),
    LaunchTabOption("time", "Time (Stats)", Icons.Filled.DateRange, "Screen time insights & heatmaps"),
    LaunchTabOption("account", "Account", Icons.Filled.Person, "Profile, rank & trophy achievements")
)

enum class SettingsCategory(val title: String) {
    APPEARANCE("Appearance"), FOCUS("Focus & behavior"), NOTIFICATIONS("Notifications & reminders")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, category: SettingsCategory = SettingsCategory.APPEARANCE) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }

    var softLockDuration by rememberSetting(prefs, "soft_lock_duration", 10)
    var softUnlockDuration by rememberSetting(prefs, "soft_unlock_duration", 5)
    var routineNotifications by rememberSetting(prefs, "routine_notifications", true)
    var persistentReminderInterval by rememberSetting(prefs, "persistent_reminder_interval", 15)
    var defaultStartTab by rememberSetting(prefs, "default_start_tab", "dashboard")
    var showTabDropdown by remember { mutableStateOf(false) }

    var taskNotificationStyle by rememberSetting(prefs, "task_notification_style", "Both")
    var showNotificationDropdown by remember { mutableStateOf(false) }

    var habitTopNotifications by rememberSetting(prefs, "habit_top_notifications", false)
    var showLockedReminderContent by rememberSetting(prefs, com.focusbyrj.app.util.ReminderPrivacy.KEY, false)

    val currentAppTheme by AppThemeManager.themeFlow.collectAsState()
    val currentThemeMode by AppThemeManager.themeModeFlow.collectAsState()
    val currentOverlayThemeMode by AppThemeManager.overlayThemeModeFlow.collectAsState()
    val currentHeatmapTheme by FocusStatsManager.themeFlow.collectAsState()
    val currentAppIconId by AppIconManager.currentIconFlow.collectAsState()
    val currentStreakSource by com.focusbyrj.app.util.StreakManager.streakSourceFlow.collectAsState()
    val drillProfileForStreak by com.focusbyrj.app.util.AptitudeManager.profileFlow.collectAsState()
    val focusStatsForStreak by com.focusbyrj.app.util.FocusStatsManager.statsFlow.collectAsState()

    var showAppThemeSheet by remember { mutableStateOf(false) }
    var showHeatmapThemeSheet by remember { mutableStateOf(false) }
    var showAppIconSheet by remember { mutableStateOf(false) }
    var showStreakSourceSheet by remember { mutableStateOf(false) }
    var showAppThemeModeDropdown by remember { mutableStateOf(false) }
    var showOverlayThemeModeDropdown by remember { mutableStateOf(false) }

    SettingsMenuPage(category.title, navController) {
        when (category) {
            SettingsCategory.APPEARANCE -> {
                SettingsMenuSectionHeader("APP & DISPLAY")
                com.focusbyrj.app.ui.speech.VoiceStyleSettingsRow()
                SettingsStringDropdownRow(
                    icon = Icons.Filled.DarkMode,
                    title = "App Background Theme",
                    selectedText = currentThemeMode.displayName,
                    isExpanded = showAppThemeModeDropdown,
                    onExpandChange = { showAppThemeModeDropdown = it },
                    options = ThemeMode.entries.map { it.displayName },
                    onOptionSelected = { option ->
                        val mode = ThemeMode.entries.find { it.displayName == option } ?: ThemeMode.SYSTEM
                        AppThemeManager.setThemeMode(context, mode)
                        showAppThemeModeDropdown = false
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsStringDropdownRow(
                    icon = Icons.Filled.ScreenLockPortrait,
                    title = "Locked Screen Overlay",
                    selectedText = currentOverlayThemeMode.displayName,
                    isExpanded = showOverlayThemeModeDropdown,
                    onExpandChange = { showOverlayThemeModeDropdown = it },
                    options = ThemeMode.entries.map { it.displayName },
                    onOptionSelected = { option ->
                        val mode = ThemeMode.entries.find { it.displayName == option } ?: ThemeMode.SYSTEM
                        AppThemeManager.setOverlayThemeMode(context, mode)
                        showOverlayThemeModeDropdown = false
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsNavigationRow(
                    icon = Icons.Filled.Palette,
                    iconTint = currentAppTheme.primary,
                    title = "Accent Theme",
                    onClick = { showAppThemeSheet = true },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            currentAppTheme.swatch.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsNavigationRow(
                    icon = Icons.Filled.Whatshot,
                    iconTint = currentHeatmapTheme.colors.last(),
                    title = "Heatmap Palette",
                    onClick = { showHeatmapThemeSheet = true },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            currentHeatmapTheme.colors.forEachIndexed { idx, color ->
                                val bg = if (idx == 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else color
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(bg)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                val currentIconOption = remember(currentAppIconId) { AppIconManager.getCurrentIcon(context) }
                SettingsNavigationRow(
                    icon = Icons.Filled.Stars,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "App Launcher Icon",
                    onClick = { showAppIconSheet = true },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = currentIconOption.previewRes),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsMenuLink(Icons.Filled.Widgets, "Widgets", "appearance.widgets", navController)
            }
            SettingsCategory.FOCUS -> {
                SettingsMenuSectionHeader("BLOCKING")
                SettingsMenuLink(Icons.Filled.Security, "Manage app boundaries", "focus.boundaries", navController)
                SettingsStepperRow(
                    icon = Icons.Filled.HourglassTop,
                    title = "Soft Mode Wait Timer",
                    valueText = "${softLockDuration}s",
                    onDecrement = {
                        if (softLockDuration > 5) {
                            softLockDuration = (softLockDuration - 5).coerceIn(SettingsPreferenceRules.softWait)
                            writeSetting(context, "soft_lock_duration", softLockDuration)
                        }
                    },
                    onIncrement = {
                        if (softLockDuration < SettingsPreferenceRules.softWait.last) {
                            softLockDuration = (softLockDuration + 5).coerceIn(SettingsPreferenceRules.softWait)
                            writeSetting(context, "soft_lock_duration", softLockDuration)
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsStepperRow(
                    icon = Icons.Filled.LockOpen,
                    title = "Soft Mode Relief Duration",
                    valueText = "${softUnlockDuration}m",
                    onDecrement = {
                        if (softUnlockDuration > 1) {
                            softUnlockDuration = (softUnlockDuration - if (softUnlockDuration > 5) 5 else 1).coerceIn(SettingsPreferenceRules.softRelief)
                            writeSetting(context, "soft_unlock_duration", softUnlockDuration)
                        }
                    },
                    onIncrement = {
                        if (softUnlockDuration < 60) {
                            softUnlockDuration = (softUnlockDuration + if (softUnlockDuration >= 5) 5 else 1).coerceIn(SettingsPreferenceRules.softRelief)
                            writeSetting(context, "soft_unlock_duration", softUnlockDuration)
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsMenuSectionHeader("STARTUP & PROGRESS")
                val currentTabOption = launchTabOptions.find { it.route == defaultStartTab } ?: launchTabOptions.first()
                SettingsDropdownRow(
                    icon = currentTabOption.icon,
                    title = "Default Launch Tab",
                    selectedText = currentTabOption.title,
                    isExpanded = showTabDropdown,
                    onExpandChange = { showTabDropdown = it },
                    options = launchTabOptions,
                    selectedOptionRoute = defaultStartTab,
                    onOptionSelected = { option ->
                        defaultStartTab = option.route
                        writeSetting(context, "default_start_tab", option.route)
                        showTabDropdown = false
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsNavigationRow(
                    icon = Icons.Filled.Whatshot,
                    iconTint = Color(0xFFFF9600),
                    title = "Streak Source",
                    onClick = { showStreakSourceSheet = true },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val activeVal = when (currentStreakSource) {
                                com.focusbyrj.app.util.StreakSource.DRILL -> drillProfileForStreak.currentStreak
                                com.focusbyrj.app.util.StreakSource.FOCUS -> focusStatsForStreak.currentStreak
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFF9600).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "🔥 ${activeVal}d",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFF9600),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsMenuLink(Icons.Filled.Whatshot, "Practice & vacation settings", "bubble.vacation", navController)
            }
            SettingsCategory.NOTIFICATIONS -> {
                SettingsMenuSectionHeader("TASKS & ROUTINES")
                SettingsStringDropdownRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Task Notification Style",
                    selectedText = SettingsPreferenceRules.taskStyleLabel(taskNotificationStyle),
                    isExpanded = showNotificationDropdown,
                    onExpandChange = { showNotificationDropdown = it },
                    options = listOf("Notification bar", "Notification + popup"),
                    onOptionSelected = { option ->
                        taskNotificationStyle = SettingsPreferenceRules.taskStyleValue(option)
                        writeSetting(context, "task_notification_style", option)
                        showNotificationDropdown = false
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsStepperRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Persistent Reminder Interval",
                    valueText = if (persistentReminderInterval >= 60) {
                        val h = persistentReminderInterval / 60
                        val m = persistentReminderInterval % 60
                        if (m > 0) "${h}h ${m}m" else "${h}h"
                    } else {
                        "${persistentReminderInterval}m"
                    },
                    onDecrement = {
                        val newInterval = SettingsScreenHelper.getNextReminderInterval(persistentReminderInterval, isIncrement = false)
                        if (newInterval != persistentReminderInterval) {
                            persistentReminderInterval = newInterval
                            writeSetting(context, "persistent_reminder_interval", newInterval)
                        }
                    },
                    onIncrement = {
                        val newInterval = SettingsScreenHelper.getNextReminderInterval(persistentReminderInterval, isIncrement = true)
                        if (newInterval != persistentReminderInterval) {
                            persistentReminderInterval = newInterval
                            writeSetting(context, "persistent_reminder_interval", newInterval)
                        }
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Routine Notifications",
                    checked = routineNotifications,
                    onCheckedChange = { isEnabled ->
                        routineNotifications = isEnabled
                        writeSetting(context, "routine_notifications", isEnabled)
                        com.focusbyrj.app.service.FocusBlockerService.updateNotificationState(context)
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Habit Top Notifications",
                    checked = habitTopNotifications,
                    onCheckedChange = { isEnabled ->
                        habitTopNotifications = isEnabled
                        writeSetting(context, "habit_top_notifications", isEnabled)
                    }
                )
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Allow reminder details on lock screen",
                    checked = showLockedReminderContent,
                    onCheckedChange = { enabled ->
                        try {
                            com.focusbyrj.app.util.ReminderPrivacy.setAllowsContent(context, enabled)
                            showLockedReminderContent = enabled
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "Could not save reminder privacy. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                )
                Text("Off by default: locked-screen reminders show a generic message. Android notification settings control final visibility and can keep details hidden even when this option is on.", Modifier.padding(horizontal = 6.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A notification-bar backup is always retained. Priority tasks also show a floating popup, even when notification-bar-only delivery is selected.", Modifier.padding(horizontal = 6.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsMenuSectionHeader("RELATED SETTINGS")
                SettingsMenuLink(Icons.Filled.Schedule, "Daily summaries & practice", "notifications.practice", navController)
                SettingsMenuLink(Icons.Filled.Notifications, "Device notification access", "permissions.notifications", navController)
                SettingsMenuLink(Icons.Filled.Alarm, "Alarms & reminders access", "permissions.alarms", navController)
            }
        }
    }

    // --- ACCENT THEME MODAL SHEET ---
    if (showAppThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppThemeSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Choose Accent Color",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personalize app highlights and active states",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeColor.entries.forEach { theme ->
                        val isSelected = currentAppTheme == theme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) theme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) theme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    AppThemeManager.setTheme(context, theme)
                                    showAppThemeSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) theme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = theme.description,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                theme.swatch.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- HEATMAP PALETTE MODAL SHEET ---
    if (showHeatmapThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHeatmapThemeSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Heatmap Palette",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select 5-tier intensity color scale for activity grids",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeatmapTheme.entries.forEach { theme ->
                        val isSelected = currentHeatmapTheme == theme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) theme.colors.last().copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) theme.colors.last() else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    FocusStatsManager.setHeatmapTheme(context, theme)
                                    showHeatmapThemeSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = theme.colors.last(),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) theme.colors.last() else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                theme.colors.forEachIndexed { idx, color ->
                                    val bg = if (idx == 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else color
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(bg)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- STREAK DISPLAY SOURCE MODAL SHEET ---
    if (showStreakSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStreakSourceSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Choose Streak Source",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select which streak is highlighted across the top bar and activity widgets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    com.focusbyrj.app.util.StreakSource.entries.forEach { source ->
                        val isSelected = currentStreakSource == source
                        val streakValue = when (source) {
                            com.focusbyrj.app.util.StreakSource.DRILL -> drillProfileForStreak.currentStreak
                            com.focusbyrj.app.util.StreakSource.FOCUS -> focusStatsForStreak.currentStreak
                        }
                        val longestValue = when (source) {
                            com.focusbyrj.app.util.StreakSource.DRILL -> drillProfileForStreak.longestStreak
                            com.focusbyrj.app.util.StreakSource.FOCUS -> focusStatsForStreak.longestStreak
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) Color(0xFFFF9600).copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFFF9600) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    com.focusbyrj.app.util.StreakManager.setStreakSource(context, source)
                                    showStreakSourceSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) Color(0xFFFF9600).copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(source.iconEmoji, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) Color(0xFFFF9600) else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (source == com.focusbyrj.app.util.StreakSource.DRILL) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Default",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = source.subtitle,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${streakValue}d",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color(0xFFFF9600) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Best: ${longestValue}d",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- APP ICON MODAL SHEET ---
    if (showAppIconSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppIconSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "App Launcher Icon",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Choose from classic emblem or profile avatars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(AppIconManager.iconOptions) { option ->
                        val isSelected = currentAppIconId == option.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (!isSelected) {
                                        val success = AppIconManager.setAppIcon(context, option.id)
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                "App icon updated to ${option.title}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    showAppIconSheet = false
                                }
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black)
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = option.previewRes),
                                    contentDescription = option.title,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stars,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Your phone's launcher will update the icon seamlessly.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- REUSABLE SETTINGS SUB-COMPONENTS (2026 Calm Minimal) ---

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = SettingsSearchCatalog.resolve(title)?.title ?: title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            fontSize = 11.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().settingsSearchTarget(title)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = SettingsSearchCatalog.resolve(title)?.title ?: title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun SettingsStepperRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().settingsSearchTarget(title)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = SettingsSearchCatalog.resolve(title)?.title ?: title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 3.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
            }

            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().settingsSearchTarget(title)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = SettingsSearchCatalog.resolve(title)?.title ?: title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    selectedText: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    options: List<LaunchTabOption>,
    selectedOptionRoute: String,
    onOptionSelected: (LaunchTabOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().settingsSearchTarget(title)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = SettingsSearchCatalog.resolve(title)?.title ?: title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable { onExpandChange(!isExpanded) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = "Select default tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { onExpandChange(false) },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                options.forEach { option ->
                    val isSelected = option.route == selectedOptionRoute
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = { onOptionSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsStringDropdownRow(
    icon: ImageVector,
    title: String,
    selectedText: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().settingsSearchTarget(title)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = SettingsSearchCatalog.resolve(title)?.title ?: title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable { onExpandChange(true) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = "Select option",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { onExpandChange(false) },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedText
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = { onOptionSelected(option) }
                    )
                }
            }
        }
    }
}
