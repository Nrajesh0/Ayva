package com.focusbyrj.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.focusbyrj.app.ui.components.LocalSettingsSearchTarget
import com.focusbyrj.app.ui.components.SettingsSearchCatalog
import com.focusbyrj.app.ui.components.SettingsSearchDestination
import com.focusbyrj.app.ui.screens.*

internal object SettingsRoutes {
    val all = setOf("preferences_hub", "settings", "security", "bubble_settings", "subscription",
        "settings_appearance", "settings_focus", "settings_notifications", "settings_widgets",
        "settings_data", "settings_help", "settings_diagnostics", "settings_vault")
    val sensitive = setOf("security", "settings_data", "settings_vault")
}

/** Shared by the real navigation host and its navigation regression tests. */
internal fun NavGraphBuilder.settingsDestinations(
    nav: NavController,
    onSetup: (() -> Unit)? = null,
    onDataRestored: () -> Unit = {},
    vaultContent: @Composable () -> Unit
) {
    composable("preferences_hub") { PreferencesHubScreen(nav) }
    composable("settings") { SettingsSearchDestination(nav) {
        // Retain old deep links. Their legacy target, if present, selects its new category.
        val category = when (SettingsSearchCatalog.resolve(LocalSettingsSearchTarget.current)?.route) {
            "settings_focus" -> SettingsCategory.FOCUS
            "settings_notifications" -> SettingsCategory.NOTIFICATIONS
            else -> SettingsCategory.APPEARANCE
        }
        SettingsScreen(nav, category)
    } }
    composable("settings_appearance") { SettingsSearchDestination(nav) { SettingsScreen(nav, SettingsCategory.APPEARANCE) } }
    composable("settings_focus") { SettingsSearchDestination(nav) { SettingsScreen(nav, SettingsCategory.FOCUS) } }
    composable("settings_notifications") { SettingsSearchDestination(nav) { SettingsScreen(nav, SettingsCategory.NOTIFICATIONS) } }
    composable("settings_widgets") { SettingsSearchDestination(nav) { WidgetsSettingsScreen(nav) } }
    composable("security") { SettingsSearchDestination(nav) { SecurityScreen(nav) } }
    composable("settings_data") { SettingsSearchDestination(nav) { DataRecoveryScreen(nav, onDataRestored) } }
    composable("settings_help") { SettingsSearchDestination(nav) { HelpSettingsScreen(nav, onSetup) } }
    composable("settings_diagnostics") { SettingsSearchDestination(nav) { DiagnosticsSettingsScreen(nav) } }
    composable("settings_vault") { SettingsSearchDestination(nav) { vaultContent() } }
    composable("bubble_settings") { SettingsSearchDestination(nav) { BubbleSettingsScreen(nav) } }
    composable("subscription") { SubscriptionScreen(nav) }
}
