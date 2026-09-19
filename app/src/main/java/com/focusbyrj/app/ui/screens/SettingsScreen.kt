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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }

    var softLockDuration by remember { mutableStateOf(prefs.getInt("soft_lock_duration", 10)) }
    var softUnlockDuration by remember { mutableStateOf(prefs.getInt("soft_unlock_duration", 5)) }
    var routineNotifications by remember { mutableStateOf(prefs.getBoolean("routine_notifications", true)) }
    var persistentReminderInterval by remember { mutableStateOf(prefs.getInt("persistent_reminder_interval", 15)) }
    var defaultStartTab by remember {
        mutableStateOf(prefs.getString("default_start_tab", "dashboard") ?: "dashboard")
    }
    var showTabDropdown by remember { mutableStateOf(false) }

    var taskNotificationStyle by remember {
        mutableStateOf(prefs.getString("task_notification_style", "Both") ?: "Both")
    }
    var showNotificationDropdown by remember { mutableStateOf(false) }

    var habitTopNotifications by remember {
        mutableStateOf(prefs.getBoolean("habit_top_notifications", false))
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- APPEARANCE SECTION (2026 Calm Minimal) ---
            SettingsSectionHeader(title = "APPEARANCE")

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsStringDropdownRow(
                    icon = Icons.Filled.DarkMode,
                    title = "App Background Theme",
                    subtitle = "Select overall app appearance",
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
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )
                SettingsStringDropdownRow(
                    icon = Icons.Filled.ScreenLockPortrait,
                    title = "Locked Screen Overlay",
                    subtitle = "Appearance of the blocking screen",
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
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Accent Color Row
                SettingsNavigationRow(
                    icon = Icons.Filled.Palette,
                    iconTint = currentAppTheme.primary,
                    title = "Accent Theme",
                    subtitle = currentAppTheme.displayName,
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

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Heatmap Palette Row
                SettingsNavigationRow(
                    icon = Icons.Filled.Whatshot,
                    iconTint = currentHeatmapTheme.colors.last(),
                    title = "Heatmap Palette",
                    subtitle = currentHeatmapTheme.displayName,
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

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Streak Display Source Row
                SettingsNavigationRow(
                    icon = Icons.Filled.Whatshot,
                    iconTint = Color(0xFFFF9600),
                    title = "Streak Source",
                    subtitle = currentStreakSource.title,
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

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // App Launcher Icon Row
                val currentIconOption = remember(currentAppIconId) {
                    AppIconManager.getCurrentIcon(context)
                }

                SettingsNavigationRow(
                    icon = Icons.Filled.Stars,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "App Launcher Icon",
                    subtitle = currentIconOption.title,
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

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Home Screen Widget Customization Row
                SettingsNavigationRow(
                    icon = Icons.Filled.Palette,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Home Screen Widget",
                    subtitle = "Themes, accent colors & opacity",
                    onClick = {
                        val intent = android.content.Intent(context, com.focusbyrj.app.widget.TodoWidgetConfigureActivity::class.java).apply {
                            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                            val ids = appWidgetManager.getAppWidgetIds(android.content.ComponentName(context, com.focusbyrj.app.widget.TodoWidgetProvider::class.java))
                            val id = if (ids.isNotEmpty()) ids[0] else 0
                            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        }
                        context.startActivity(intent)
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- BLOCKING & CONTROLS SECTION (2026 Calm Minimal) ---
            SettingsSectionHeader(title = "BLOCKING & PREFERENCES")

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Soft Lock Wait Timer Stepper
                SettingsStepperRow(
                    icon = Icons.Filled.HourglassTop,
                    title = "Soft Mode Wait Timer",
                    subtitle = "Delay before unlock button activates",
                    valueText = "${softLockDuration}s",
                    onDecrement = {
                        if (softLockDuration > 5) {
                            softLockDuration -= 5
                            prefs.edit().putInt("soft_lock_duration", softLockDuration).apply()
                        }
                    },
                    onIncrement = {
                        if (softLockDuration < 60) {
                            softLockDuration += 5
                            prefs.edit().putInt("soft_lock_duration", softLockDuration).apply()
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Soft Unlock Relief Duration Stepper
                SettingsStepperRow(
                    icon = Icons.Filled.LockOpen,
                    title = "Soft Mode Relief Duration",
                    subtitle = "Temporary unlock access window",
                    valueText = "${softUnlockDuration}m",
                    onDecrement = {
                        if (softUnlockDuration > 1) {
                            softUnlockDuration -= if (softUnlockDuration > 5) 5 else 1
                            prefs.edit().putInt("soft_unlock_duration", softUnlockDuration).apply()
                        }
                    },
                    onIncrement = {
                        if (softUnlockDuration < 60) {
                            softUnlockDuration += if (softUnlockDuration >= 5) 5 else 1
                            prefs.edit().putInt("soft_unlock_duration", softUnlockDuration).apply()
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Persistent Reminder Interval Stepper
                SettingsStepperRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Persistent Reminder Interval",
                    subtitle = "Alert interval for pending tasks",
                    valueText = if (persistentReminderInterval >= 60) {
                        val h = persistentReminderInterval / 60
                        val m = persistentReminderInterval % 60
                        if (m > 0) "${h}h ${m}m" else "${h}h"
                    } else {
                        "${persistentReminderInterval}m"
                    },
                    onDecrement = {
                        val newInterval = when (persistentReminderInterval) {
                            360 -> 300 // 6h -> 5h
                            300 -> 240 // 5h -> 4h
                            240 -> 180 // 4h -> 3h
                            180 -> 120 // 3h -> 2h
                            120 -> 60  // 2h -> 1h
                            60 -> 30
                            30 -> 15
                            15 -> 10
                            10 -> 5
                            else -> persistentReminderInterval
                        }
                        if (newInterval != persistentReminderInterval) {
                            persistentReminderInterval = newInterval
                            prefs.edit().putInt("persistent_reminder_interval", newInterval).apply()
                        }
                    },
                    onIncrement = {
                        val newInterval = when (persistentReminderInterval) {
                            5 -> 10
                            10 -> 15
                            15 -> 30
                            30 -> 60
                            60 -> 120
                            120 -> 180
                            180 -> 240
                            240 -> 300
                            300 -> 360
                            else -> persistentReminderInterval
                        }
                        if (newInterval != persistentReminderInterval) {
                            persistentReminderInterval = newInterval
                            prefs.edit().putInt("persistent_reminder_interval", newInterval).apply()
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Routine Notifications Toggle
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Routine Notifications",
                    subtitle = "Alerts when routines start and end",
                    checked = routineNotifications,
                    onCheckedChange = { isEnabled ->
                        routineNotifications = isEnabled
                        prefs.edit().putBoolean("routine_notifications", isEnabled).apply()
                        com.focusbyrj.app.service.FocusBlockerService.updateNotificationState(context)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                SettingsStringDropdownRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Task Notification Style",
                    subtitle = "Alert style for due tasks",
                    selectedText = taskNotificationStyle,
                    isExpanded = showNotificationDropdown,
                    onExpandChange = { showNotificationDropdown = it },
                    options = listOf("Notification Only", "Floating Bar", "Both"),
                    onOptionSelected = { option ->
                        taskNotificationStyle = option
                        prefs.edit().putString("task_notification_style", option).apply()
                        showNotificationDropdown = false
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Habit Top Notifications Toggle
                SettingsSwitchRow(
                    icon = Icons.Filled.Notifications,
                    title = "Habit Top Notifications",
                    subtitle = "Status bar alerts alongside floating reminder",
                    checked = habitTopNotifications,
                    onCheckedChange = { isEnabled ->
                        habitTopNotifications = isEnabled
                        prefs.edit().putBoolean("habit_top_notifications", isEnabled).apply()
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, end = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                // Default Launch Tab Dropdown Row
                val currentTabOption = remember(defaultStartTab) {
                    launchTabOptions.find { it.route == defaultStartTab } ?: launchTabOptions[0]
                }

                SettingsDropdownRow(
                    icon = currentTabOption.icon,
                    title = "Default Launch Tab",
                    subtitle = "Screen shown when opening app",
                    selectedText = currentTabOption.title,
                    isExpanded = showTabDropdown,
                    onExpandChange = { showTabDropdown = it },
                    options = launchTabOptions,
                    selectedOptionRoute = defaultStartTab,
                    onOptionSelected = { option ->
                        defaultStartTab = option.route
                        prefs.edit().putString("default_start_tab", option.route).apply()
                        context.getSharedPreferences("focus_app_prefs", Context.MODE_PRIVATE)
                            .edit().putString("default_start_tab", option.route).apply()
                        showTabDropdown = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Footer info
            Text(
                text = "Focus by RJ",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold, 
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
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
        text = title,
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
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun SettingsStepperRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
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
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
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
    subtitle: String,
    selectedText: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    options: List<LaunchTabOption>,
    selectedOptionRoute: String,
    onOptionSelected: (LaunchTabOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
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
    subtitle: String,
    selectedText: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
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
