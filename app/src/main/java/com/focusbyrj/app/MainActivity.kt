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

package com.focusbyrj.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import com.focusbyrj.app.ui.screens.notes.NotesScreen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusbyrj.app.service.FocusBlockerService
import com.focusbyrj.app.ui.components.FocusBottomBar
import com.focusbyrj.app.ui.components.SetupPermissionsDialog
import com.focusbyrj.app.ui.navigation.Screen
import com.focusbyrj.app.ui.screens.AccountScreen
import com.focusbyrj.app.ui.screens.AddRestrictionScreen
import com.focusbyrj.app.ui.screens.DashboardScreen
import com.focusbyrj.app.ui.screens.SchedulesScreen
import com.focusbyrj.app.ui.screens.SecurityScreen
import com.focusbyrj.app.ui.screens.SettingsScreen
import com.focusbyrj.app.ui.screens.SubscriptionScreen
import com.focusbyrj.app.ui.screens.TimeScreen
import com.focusbyrj.app.ui.theme.BorderGlass
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.ui.viewmodels.FocusViewModel
import com.focusbyrj.app.ui.viewmodels.FocusViewModelFactory
import com.focusbyrj.app.ui.viewmodels.TaskViewModel
import com.focusbyrj.app.ui.viewmodels.TaskViewModelFactory
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.FocusStatsManager
import com.focusbyrj.app.util.PermissionUtils
import com.focusbyrj.app.util.ProfileAvatarManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.focusbyrj.app.util.EconomyEvent

class MainActivity : FragmentActivity() {

    lateinit var viewModel: FocusViewModel
    lateinit var taskViewModel: com.focusbyrj.app.ui.viewmodels.TaskViewModel
    lateinit var habitViewModel: com.focusbyrj.app.ui.viewmodels.HabitViewModel

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val navigateTo = intent.getStringExtra("navigate_to") ?: intent.getStringExtra("NAV_DESTINATION")
        val openAddDialog = intent.getBooleanExtra("open_add_dialog", false)
        val openNoteId = intent.getLongExtra("open_note_id", -1L).takeIf { it != -1L }
        val openNewNote = intent.getBooleanExtra("open_new_note", false)
        val asChecklist = intent.getBooleanExtra("as_checklist", false)
        val openVoiceNote = intent.getBooleanExtra("open_voice_note", false)

        if (openNoteId != null) {
            viewModel.triggerOpenNote(openNoteId)
            viewModel.triggerNavigation(Screen.Empty.route)
        } else if (openNewNote) {
            viewModel.triggerOpenNewNote(asChecklist)
            viewModel.triggerNavigation(Screen.Empty.route)
        } else if (openVoiceNote) {
            viewModel.triggerOpenVoiceNote()
            viewModel.triggerNavigation(Screen.Empty.route)
        } else if (navigateTo != null) {
            viewModel.triggerNavigation(navigateTo)
        }
        if (openAddDialog) {
            viewModel.triggerOpenAddDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FocusApplication
        val vm: FocusViewModel by viewModels {
            FocusViewModelFactory(app.repository, app)
        }
        viewModel = vm

        val tvm: com.focusbyrj.app.ui.viewmodels.TaskViewModel by viewModels {
            com.focusbyrj.app.ui.viewmodels.TaskViewModelFactory(app.taskRepository, app)
        }
        taskViewModel = tvm

        val hvm: com.focusbyrj.app.ui.viewmodels.HabitViewModel by viewModels {
            com.focusbyrj.app.ui.viewmodels.HabitViewModelFactory(app.habitRepository, app)
        }
        habitViewModel = hvm

        kotlin.runCatching { FocusBlockerService.startService(this) }
        kotlin.runCatching { com.focusbyrj.app.service.BubbleService.startIfEnabled(this) }
        kotlin.runCatching { com.focusbyrj.app.service.DailySummaryReceiver.scheduleDailySummaries(this) }
        kotlin.runCatching { com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleRandomDrillReminders(this) }
        kotlin.runCatching { com.focusbyrj.app.util.TaskReminderHelper.scheduleAllPendingReminders(this) }
        kotlin.runCatching { com.focusbyrj.app.util.HabitAlarmScheduler.rescheduleAllHabits(this) }
        kotlin.runCatching { com.focusbyrj.app.widget.TodoWidgetProvider.updateAllWidgets(this) }
        kotlin.runCatching { com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(this) }

        handleIncomingIntent(intent)

        val navigateTo = intent?.getStringExtra("navigate_to") ?: intent?.getStringExtra("NAV_DESTINATION")
        val openAddDialog = intent?.getBooleanExtra("open_add_dialog", false) ?: false

        setContent {
            FocusByRjTheme {
                MainAppScreen(
                    viewModel = viewModel, 
                    taskViewModel = taskViewModel,
                    habitViewModel = habitViewModel,
                    initialNavigateTo = navigateTo,
                    initialOpenAdd = openAddDialog
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        kotlin.runCatching { com.focusbyrj.app.widget.TodoWidgetProvider.updateAllWidgets(this) }
        kotlin.runCatching { com.focusbyrj.app.widget.NoteWidgetProvider.updateAllWidgets(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: FocusViewModel, 
    taskViewModel: com.focusbyrj.app.ui.viewmodels.TaskViewModel,
    habitViewModel: com.focusbyrj.app.ui.viewmodels.HabitViewModel,
    initialNavigateTo: String? = null,
    initialOpenAdd: Boolean = false
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val notesViewModel: com.focusbyrj.app.ui.screens.notes.NotesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val pendingRoute by viewModel.pendingNavigationRoute.collectAsStateWithLifecycle()
    val pendingOpenAdd by viewModel.pendingOpenAddDialog.collectAsStateWithLifecycle()
    val pendingOpenNoteId by viewModel.pendingOpenNoteId.collectAsStateWithLifecycle()
    val pendingOpenNewNote by viewModel.pendingOpenNewNote.collectAsStateWithLifecycle()
    val pendingOpenVoiceNote by viewModel.pendingOpenVoiceNote.collectAsStateWithLifecycle()

    LaunchedEffect(pendingOpenNoteId) {
        pendingOpenNoteId?.let { noteId ->
            notesViewModel.openNoteById(noteId)
            viewModel.clearOpenNote()
        }
    }

    LaunchedEffect(pendingOpenNewNote) {
        pendingOpenNewNote?.let { asChecklist ->
            notesViewModel.openNewNote(asChecklist = asChecklist)
            viewModel.clearOpenNewNote()
        }
    }

    LaunchedEffect(pendingOpenVoiceNote) {
        if (pendingOpenVoiceNote) {
            notesViewModel.startVoiceRecording()
            viewModel.clearOpenVoiceNote()
        }
    }

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            viewModel.clearNavigation()
        }
    }

    val items = listOf(
        Screen.Dashboard,
        Screen.Todos,
        Screen.Empty,
        Screen.Time,
        Screen.Account
    )

    val scope = rememberCoroutineScope()
    val isSessionActive by viewModel.isSessionActive.collectAsStateWithLifecycle()
    val economyProfile by FocusEconomyManager.profileFlow.collectAsStateWithLifecycle()
    val focusStats by FocusStatsManager.statsFlow.collectAsStateWithLifecycle()
    val drillProfile by com.focusbyrj.app.util.AptitudeManager.profileFlow.collectAsStateWithLifecycle()
    val streakSource by com.focusbyrj.app.util.StreakManager.streakSourceFlow.collectAsStateWithLifecycle()
    val activeStreakDays = when (streakSource) {
        com.focusbyrj.app.util.StreakSource.DRILL -> drillProfile.currentStreak
        com.focusbyrj.app.util.StreakSource.FOCUS -> focusStats.currentStreak
    }
    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences("focus_app_prefs", Context.MODE_PRIVATE) }
    val hasSeenOnboarding = remember { prefs.getBoolean("has_seen_permission_onboarding", false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var hasUsageStats by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    var hasNotifications by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME || event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                hasUsageStats = PermissionUtils.hasUsageStatsPermission(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
                isBatteryUnrestricted = PermissionUtils.isIgnoringBatteryOptimizations(context)
                hasNotifications = PermissionUtils.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allConfigured = hasUsageStats && hasOverlay && isBatteryUnrestricted && hasNotifications

    var showSetupDialog by remember { mutableStateOf(!hasSeenOnboarding && !allConfigured) }

    var currentEconomyEvent by remember { mutableStateOf<EconomyEvent?>(null) }
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        FocusEconomyManager.economyEvents.collect { event ->
            currentEconomyEvent = event
            delay(4000)
            currentEconomyEvent = null
            delay(500)
        }
    }


    androidx.compose.runtime.LaunchedEffect(showSetupDialog) {
        if (showSetupDialog) {
            while (true) {
                hasUsageStats = PermissionUtils.hasUsageStatsPermission(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
                isBatteryUnrestricted = PermissionUtils.isIgnoringBatteryOptimizations(context)
                hasNotifications = PermissionUtils.hasNotificationPermission(context)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    if (showSetupDialog) {
        SetupPermissionsDialog(
            hasUsageStats = hasUsageStats,
            hasOverlay = hasOverlay,
            isBatteryUnrestricted = isBatteryUnrestricted,
            hasNotifications = hasNotifications,
            onDismiss = {
                prefs.edit().putBoolean("has_seen_permission_onboarding", true).apply()
                showSetupDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                val hideTopBarRoutes = listOf(
                    Screen.Habits.route,
                    Screen.Empty.route,
                    Screen.PreferencesHub.route,
                    Screen.Settings.route,
                    Screen.Security.route,
                    Screen.BubbleSettings.route,
                    Screen.Subscription.route,
                    Screen.AddRestriction.route
                )
                if (!isSessionActive && currentDestination?.route !in hideTopBarRoutes) {
                    TopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = currentDestination?.route,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith 
                                    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing))
                                },
                                label = "TopBarTitleTransition"
                            ) { route ->
                                when (route) {
                                    Screen.Dashboard.route -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.app_icon),
                                                contentDescription = "RuN App Logo",
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                            )
                                            Text(
                                                text = "RuN",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                    Screen.Todos.route -> {
                                        Text(
                                            text = "Todos",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Screen.Schedules.route -> {
                                        Text(
                                            text = "Routines",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Screen.Time.route -> {
                                        Text(
                                            text = "Screen Time",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Screen.Account.route -> {
                                        Text(
                                            text = "Profile & Stats",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Screen.Empty.route -> {
                                        Text(
                                            text = "Notes",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    else -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Image(
                                                 painter = painterResource(id = R.drawable.app_icon),
                                                 contentDescription = "RuN App Logo",
                                                 modifier = Modifier
                                                     .size(30.dp)
                                                     .clip(RoundedCornerShape(8.dp))
                                                     .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                            )
                                            Text(
                                                 text = "RuN",
                                                 style = MaterialTheme.typography.titleLarge.copy(
                                                     fontWeight = FontWeight.Bold,
                                                     fontSize = 20.sp,
                                                     letterSpacing = 0.5.sp
                                                 ),
                                                 color = MaterialTheme.colorScheme.onBackground
                                             )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            if (currentDestination?.route == Screen.Schedules.route) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 12.dp, end = 4.dp)
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .clickable { navController.popBackStack() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            // Live Streak Flame Pill
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        navController.navigate(Screen.Account.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔥", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${activeStreakDays}d",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Interactive Profile Avatar Ring -> Opens full-page Preferences & Settings Hub
                            val avatarRes = ProfileAvatarManager.getAvatarImageRes(economyProfile.selectedAvatar, economyProfile.avatarTier)
                            val avatarBorder = ProfileAvatarManager.getAvatarBorderColor(economyProfile.selectedAvatar, economyProfile.avatarTier)

                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 12.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.8.dp, avatarBorder, CircleShape)
                                    .clickable {
                                        navController.navigate(Screen.PreferencesHub.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = avatarRes),
                                    contentDescription = "Settings & Preferences",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            },
            bottomBar = {
                val hideBottomBarRoutes = listOf(
                    Screen.Schedules.route,
                    Screen.AddRestriction.route,
                    Screen.Security.route,
                    Screen.Settings.route,
                    Screen.BubbleSettings.route,
                    Screen.Subscription.route,
                    Screen.Habits.route,
                    Screen.PreferencesHub.route
                )
                val notesEditingState by notesViewModel.editingState.collectAsStateWithLifecycle()
                val isEditingNote = currentDestination?.route == Screen.Empty.route && notesEditingState != null
                if (currentDestination?.route !in hideBottomBarRoutes && !isSessionActive && !isEditingNote) {
                    FocusBottomBar(
                        items = items,
                        currentDestination = currentDestination,
                        navController = navController
                    )
                }
            },
            floatingActionButton = {
                if (currentDestination?.route == Screen.Dashboard.route) {
                    FloatingActionButton(
                        onClick = { navController.navigate(Screen.AddRestriction.route) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 6.dp
                        )
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Restriction")
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                val defaultStartTab = remember {
                    val saved = prefs.getString("default_start_tab", null)
                        ?: context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).getString("default_start_tab", Screen.Dashboard.route)
                        ?: Screen.Dashboard.route
                    if (items.any { it.route == saved }) saved else Screen.Dashboard.route
                }
                val startDest = initialNavigateTo ?: defaultStartTab

                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    enterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    },
                    exitTransition = {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = 150,
                                easing = FastOutSlowInEasing
                            )
                        )
                    },
                    popEnterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    },
                    popExitTransition = {
                        fadeOut(
                            animationSpec = tween(
                                durationMillis = 150,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                ) {
                composable(Screen.Dashboard.route) {
                    val restrictions by viewModel.combinedRestrictions.collectAsStateWithLifecycle()
                    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
                    val timeRemaining by viewModel.timeRemaining.collectAsStateWithLifecycle()
                    val initialTime by viewModel.initialTime.collectAsStateWithLifecycle()
                    val habitsWithProgress by habitViewModel.habitsWithProgress.collectAsStateWithLifecycle()

                    DashboardScreen(
                        restrictions = restrictions,
                        schedules = schedules,
                        habits = habitsWithProgress,
                        onToggle = { app -> viewModel.toggleRestriction(app) },
                        onDelete = { app -> viewModel.deleteRestriction(app) },
                        onUpdate = { app -> viewModel.updateRestriction(app) },
                        isSessionActive = isSessionActive,
                        timeRemaining = timeRemaining,
                        initialTime = initialTime,
                        onToggleSession = { viewModel.toggleFocusSession() },
                        onSetTime = { time -> viewModel.setTimeRemaining(time) },
                        onOpenRoutines = {
                            navController.navigate(Screen.Schedules.route) {
                                launchSingleTop = true
                            }
                        },
                        onOpenHabits = {
                            navController.navigate(Screen.Habits.route) {
                                launchSingleTop = true
                            }
                        },
                        onHabitIncrement = { habit ->
                            habitViewModel.incrementProgress(habit)
                        }
                    )
                }
                composable(Screen.Empty.route) {
                    NotesScreen(
                        viewModel = notesViewModel,
                        activeStreakDays = activeStreakDays,
                        onOpenAccount = {
                            navController.navigate(Screen.PreferencesHub.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToDashboard = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToTodos = {
                            navController.navigate(Screen.Todos.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Screen.Schedules.route) {
                    SchedulesScreen(viewModel)
                }
                composable(Screen.Account.route) {
                    AccountScreen()
                }
                composable(Screen.PreferencesHub.route) {
                    com.focusbyrj.app.ui.screens.PreferencesHubScreen(
                        navController = navController,
                        onOpenSetupGuide = { showSetupDialog = true }
                    )
                }
                composable(Screen.Time.route) {
                    TimeScreen()
                }
                composable(Screen.AddRestriction.route) {
                    AddRestrictionScreen(navController, viewModel)
                }
                composable(Screen.Security.route) {
                    SecurityScreen(navController)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(navController)
                }
                composable(Screen.BubbleSettings.route) {
                    com.focusbyrj.app.ui.screens.BubbleSettingsScreen(navController)
                }
                composable(Screen.Subscription.route) {
                    SubscriptionScreen(navController)
                }
                composable(Screen.Todos.route) {
                    com.focusbyrj.app.ui.screens.TodosScreen(
                        taskViewModel, 
                        initialOpenAdd = initialOpenAdd || pendingOpenAdd,
                        onOpenAddHandled = { viewModel.clearOpenAddDialog() }
                    )
                }
                composable(Screen.Habits.route) {
                    com.focusbyrj.app.ui.screens.HabitsScreen(
                        habitViewModel = habitViewModel,
                        onBack = { navController.popBackStack() },
                        initialOpenCreate = initialOpenAdd || pendingOpenAdd
                    )
                }
            }
            
            // Economy Popup - Limited to Profile / Account tab only
            AnimatedVisibility(
                visible = currentEconomyEvent != null && currentDestination?.route == Screen.Account.route,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).padding(horizontal = 16.dp)
            ) {
                currentEconomyEvent?.let { event ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val iconRes = when (event) {
                                is EconomyEvent.AchievementUnlocked -> R.drawable.ic_achievement_century_club // generic for now if none provided
                                is EconomyEvent.RewardsEarned -> R.drawable.ic_achievement_wealthy
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha=0.2f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column {
                                when (event) {
                                    is EconomyEvent.AchievementUnlocked -> {
                                        Text("Achievement Unlocked!", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(event.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                    }
                                    is EconomyEvent.RewardsEarned -> {
                                        Text(event.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text("+${event.xp} XP  •  +${event.gold} Gold", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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

