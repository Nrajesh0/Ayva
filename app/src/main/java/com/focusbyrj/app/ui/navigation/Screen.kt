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

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Focus", Icons.Filled.Home)
    object Schedules : Screen("schedules", "Routines", Icons.Filled.Schedule)
    object Account : Screen("account", "Account", Icons.Filled.Person)
    object Time : Screen("time", "Time", Icons.Filled.DateRange)
    object AddRestriction : Screen("add_restriction", "Add", Icons.Filled.Add)
    object Security : Screen("security", "Security", Icons.Filled.Security)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object BubbleSettings : Screen("bubble_settings", "Bubble Settings", Icons.Filled.Chat)
    object Subscription : Screen("subscription", "Subscription", androidx.compose.material.icons.Icons.Filled.Star)
    object Todos : Screen("todos", "Todos", Icons.Filled.CheckCircle)
    object Habits : Screen("habits", "Habits", Icons.Filled.Schedule)
    object Empty : Screen("empty", "Notes", Icons.AutoMirrored.Filled.StickyNote2)
    object PreferencesHub : Screen("preferences_hub", "Preferences", Icons.Filled.Settings)
}
