/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Laptop

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Focus", Icons.Filled.Home)
    object Schedules : Screen("schedules", "Routines", Icons.Filled.Schedule)
    object Account : Screen("account", "Account", Icons.Filled.Person)
    object AddRestriction : Screen("add_restriction", "Add", Icons.Filled.Add)
    object Security : Screen("security", "Security", Icons.Filled.Security)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object BubbleSettings : Screen("bubble_settings", "Bubble Settings", Icons.Filled.Chat)
    object Subscription : Screen("subscription", "Subscription", androidx.compose.material.icons.Icons.Filled.Star)
    object Todos : Screen("todos", "Todos", Icons.Filled.CheckCircle)
    object Habits : Screen("habits", "Habits", Icons.Filled.Schedule)
    object Empty : Screen("empty", "Notes", Icons.AutoMirrored.Filled.StickyNote2)
    object Time : Screen("time", "Screen Time", Icons.Filled.Schedule)
    object EmptyTab : Screen("empty_tab", "Empty", Icons.Filled.CropSquare)
    object PreferencesHub : Screen("preferences_hub", "Preferences", Icons.Filled.Settings)
}
