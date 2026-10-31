package com.focusbyrj.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

internal data class SettingsSearchEntry(val title: String, val subtitle: String, val section: String, val route: String) {
    fun matches(query: String): Boolean = query.trim().split(Regex("\\s+")).all { word ->
        (title+" "+subtitle+" "+section+" "+SettingsSearchAliases[title].orEmpty()+ (if(route=="security") " privacy permissions safety" else "")).contains(
            if(word.length>3 && !word.endsWith("ss")) word.removeSuffix("s") else word, ignoreCase=true)
    }
}
private val SettingsSearchAliases = mapOf(
    "App Background Theme" to "dark light oled system mode appearance",
    "Locked Screen Overlay" to "dark light oled blocker display lock",
    "Accent Theme" to "color colour palette customization",
    "Heatmap Palette" to "color colour screen time stats",
    "Secret Vault Passcode" to "pin password archive hidden notes lock",
    "Battery Restrictions" to "optimization unrestricted background power",
    "Uninstall Protection" to "device admin administrator remove app",
    "Profile & Rank" to "username display name avatar identity achievements",
    "Live transcription style" to "voice speech microphone listening appearance",
    "Data & recovery" to "draft storage database cache snapshots verify backup",
    "Hidden Opacity" to "transparency bubble fade",
    "Amount Hidden (Tuck Depth)" to "bubble docking edge hide depth",
    "Default Launch Tab" to "startup start home page screen"
)
internal val LocalSettingsSearchTarget = staticCompositionLocalOf<String?> { null }

@Composable
fun SettingsSearchDestination(nav: NavController, content: @Composable () -> Unit) {
    val handle = nav.previousBackStackEntry?.savedStateHandle
    val target = remember { handle?.get<String>("settings_search_target") }
    LaunchedEffect(Unit) { handle?.remove<String>("settings_search_target") }
    CompositionLocalProvider(LocalSettingsSearchTarget provides target, content=content)
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.settingsSearchTarget(title: String): Modifier = composed {
    val target = LocalSettingsSearchTarget.current
    val requester = remember { BringIntoViewRequester() }
    var highlighted by remember { mutableStateOf(false) }
    LaunchedEffect(target,title) {
        if(target==title) { delay(350); requester.bringIntoView(); highlighted=true; delay(2400); highlighted=false }
    }
    this.bringIntoViewRequester(requester).background(
        if(highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha=.5f) else androidx.compose.ui.graphics.Color.Transparent,
        RoundedCornerShape(12.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSearchSheet(onDismiss: () -> Unit, onChoose: (SettingsSearchEntry) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val matches = remember(query) { SettingsSearchCatalog.entries.filter { it.matches(query) }
        .sortedByDescending { if(query.isNotBlank() && it.title.startsWith(query.trim(),true)) 1 else 0 } }
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=20.dp)) {
            Text("Search settings",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().focusRequester(focus).testTag("settings_search_query"),
                singleLine=true,placeholder={Text("Themes, reminders, privacy…")},leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Clear search")}},shape=RoundedCornerShape(16.dp))
            Spacer(Modifier.height(12.dp))
            Text("${matches.size} settings",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                if(matches.isEmpty()) item {Text("No matching settings. Try another word.",Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                items(matches,key={it.route+it.title}) { entry ->
                    ListItem(headlineContent={Text(entry.title)},supportingContent={Text(listOf(entry.section,entry.subtitle).filter { it.isNotBlank() }.joinToString(" · "))},
                        modifier=Modifier.clickable{keyboard?.hide();onChoose(entry)}.testTag("setting_result_${entry.title}"),
                        colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surface))
                }
            }
        }
        LaunchedEffect(Unit) { delay(150);focus.requestFocus() }
    }
}
