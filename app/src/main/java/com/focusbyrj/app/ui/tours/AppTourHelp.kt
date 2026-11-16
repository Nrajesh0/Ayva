package com.focusbyrj.app.ui.tours

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.focusbyrj.app.ui.components.SettingsMenuPage
import com.focusbyrj.app.ui.components.SettingsMenuRow
import com.focusbyrj.app.ui.components.SettingsMenuSectionHeader

@Composable
internal fun TourWelcome(enabled: Boolean) {
    val store = rememberTourPreferences()
    if (enabled && store.welcomePending)
        AlertDialog(
            onDismissRequest = { store.chooseWelcome(false) },
            title = { Text("A quick look around?") },
            text = {
                Text(
                    "Find your way around, then get short tips as you try features. You can skip any tour."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { store.chooseWelcome(true) },
                    modifier = Modifier.testTag("tour_welcome_start"),
                ) {
                    Text("Show me around")
                }
            },
            dismissButton = {
                TextButton(onClick = { store.chooseWelcome(false) }) { Text("Explore myself") }
            },
        )
}

@Composable
internal fun AppToursScreen(nav: NavController) {
    val store = rememberTourPreferences()
    SettingsMenuPage("App tours", nav) {
        SettingsMenuRow(
            Icons.Default.Explore,
            "Show first-use tips",
            Modifier.testTag("tour_automatic")
                .toggleable(
                    value = store.automatic,
                    role = Role.Switch,
                    onValueChange = store::setAutomatic,
                ),
        ) {
            Switch(store.automatic, onCheckedChange = null)
        }
        Text(
            "Short, skippable guides. Replay any tour below.",
            Modifier.padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        SettingsMenuSectionHeader("REPLAY A TOUR")
        AppTours.replayable.forEach { tour ->
            SettingsMenuRow(
                Icons.Default.Explore,
                tour.title,
                modifier = Modifier.testTag("replay_${tour.id}"),
                onClick = {
                    store.replay(tour)
                    nav.navigate(tour.route) {
                        // Remove the tour picker and return to the existing destination where
                        // possible.
                        popUpTo("settings_tours") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            ) {
                Text("Show", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
