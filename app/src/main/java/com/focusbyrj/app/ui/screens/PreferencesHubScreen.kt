package com.focusbyrj.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.focusbyrj.app.ui.components.*
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.LicenseManager
import com.focusbyrj.app.util.ProfileAvatarManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesHubScreen(navController: NavController) {
    val profile by FocusEconomyManager.profileFlow.collectAsStateWithLifecycle()
    val isPro by LicenseManager.isProFlow.collectAsStateWithLifecycle()
    val permissions by rememberDevicePermissions()
    val scene = rememberVizagScene(navController)
    var searching by remember { mutableStateOf(false) }
    if (searching) SettingsSearchSheet(onDismiss = { searching = false }, onChoose = { entry ->
        searching = false
        SettingsNavigation.open(navController, entry.id)
    })
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.testTag("preferences_hub_back_button")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search settings") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { navController.navigate("account") { launchSingleTop = true } }.testTag("preferences_profile_card"),
                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .18f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(ProfileAvatarManager.getAvatarImageRes(profile.selectedAvatar, profile.avatarTier)), "User avatar", Modifier.size(42.dp).clip(CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name.ifBlank { "Focus Warrior" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Offline · Local storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { SettingsNavigation.open(navController, "profile.pro") }) {
                        Text(if (isPro) "PRO" else "Pro license", fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, "Profile & achievements", Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            val menu = listOf(
                Triple("appearance.home", "Appearance", Icons.Default.Palette),
                Triple("focus.home", "Focus & behavior", Icons.Default.Lock),
                Triple("notifications.home", "Notifications & reminders", Icons.Default.Notifications),
                Triple("security.home", "Security & permissions", Icons.Default.Security),
                Triple("data.home", "Data & recovery", Icons.Default.Storage),
                Triple("bubble.home", "Bubble Settings", Icons.Default.Chat),
                Triple("help.home", "Help & about", Icons.Default.Info)
            )
            menu.forEachIndexed { index, (id, title, icon) ->
                val tag = when (id) { "appearance.home" -> "menu_app_settings"; "security.home" -> "menu_security_permissions"; "bubble.home" -> "menu_bubble_settings"; "help.home" -> "menu_help_about"; else -> "menu_$id" }
                SettingsMenuRow(icon, title, Modifier.testTag(tag), onClick = { SettingsNavigation.open(navController, id) }) {
                    if (id == "security.home") Surface(shape = CircleShape,
                        color = (if (permissions.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = .12f)) {
                        Text(if (permissions.ready) "Ready" else "Setup", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = if (permissions.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f), modifier = Modifier.size(16.dp))
                }
                if (index < menu.lastIndex) HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
            }
            Spacer(Modifier.height(56.dp))
            SettingsVizagFooter(scene)
            Spacer(Modifier.height(20.dp))
        }
    }
}
