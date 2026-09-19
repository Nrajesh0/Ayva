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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.focusbyrj.app.data.AppRestriction
import com.focusbyrj.app.ui.components.CustomRestrictionSection
import com.focusbyrj.app.ui.theme.*
import com.focusbyrj.app.ui.viewmodels.FocusViewModel
import com.focusbyrj.app.util.CustomCategory
import com.focusbyrj.app.util.CustomCategoryManager
import com.focusbyrj.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppCategory(val title: String) {
    ALL("All"),
    SOCIAL("Social"),
    PAYMENT("Finance"),
    SHOPPING("Shopping"),
    GAMES("Games"),
    UTILITY("Utility"),
    OTHERS("Others")
}

fun getCategoryForApp(appInfo: ApplicationInfo, packageName: String): AppCategory {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        when (appInfo.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.UTILITY
        }
    }
    
    val lowerPkg = packageName.lowercase()
    if (lowerPkg.contains("whatsapp") || lowerPkg.contains("instagram") || 
        lowerPkg.contains("facebook") || lowerPkg.contains("twitter") || 
        lowerPkg.contains("tiktok") || lowerPkg.contains("snapchat") ||
        lowerPkg.contains("reddit") || lowerPkg.contains("telegram") ||
        lowerPkg.contains("discord")) {
        return AppCategory.SOCIAL
    }
    
    if (lowerPkg.contains("pay") || lowerPkg.contains("bank") || 
        lowerPkg.contains("cash") || lowerPkg.contains("wallet") ||
        lowerPkg.contains("paypal") || lowerPkg.contains("venmo") ||
        lowerPkg.contains("stripe") || lowerPkg.contains("finance")) {
        return AppCategory.PAYMENT
    }
    
    if (lowerPkg.contains("shop") || lowerPkg.contains("store") || 
        lowerPkg.contains("amazon") || lowerPkg.contains("flipkart") ||
        lowerPkg.contains("ebay") || lowerPkg.contains("cart") ||
        lowerPkg.contains("myntra") || lowerPkg.contains("alibaba") ||
        lowerPkg.contains("aliexpress") || lowerPkg.contains("buy") || lowerPkg.contains("retail") || lowerPkg.contains("commerce")) {
        return AppCategory.SHOPPING
    }
    
    if (lowerPkg.contains("game") || lowerPkg.contains("unity") || 
        lowerPkg.contains("unreal") || lowerPkg.contains("roblox") ||
        lowerPkg.contains("mojang") || lowerPkg.contains("ea") ||
        lowerPkg.contains("supercell") || lowerPkg.contains("king") ||
        lowerPkg.contains("epic")) {
        return AppCategory.GAMES
    }
    
    if (lowerPkg.contains("tool") || lowerPkg.contains("util") || 
        lowerPkg.contains("calculator") || lowerPkg.contains("calendar") ||
        lowerPkg.contains("clock") || lowerPkg.contains("camera") ||
        lowerPkg.contains("weather") || lowerPkg.contains("notes") ||
        lowerPkg.contains("file") || lowerPkg.contains("settings") ||
        lowerPkg.contains("drive") || lowerPkg.contains("docs") ||
        lowerPkg.contains("sheet") || lowerPkg.contains("slide") ||
        lowerPkg.contains("gmail") || lowerPkg.contains("mail")) {
        return AppCategory.UTILITY
    }
    
    return AppCategory.OTHERS
}

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val category: AppCategory
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRestrictionScreen(
    navController: NavController,
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Any>(AppCategory.ALL) }
    var selectedApps by remember { mutableStateOf<Set<InstalledApp>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var appModes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var restrictionMode by remember { mutableStateOf("SIMPLE") }
    var timeLimitMinutes by remember { mutableStateOf(15) }
    var clickLimitCount by remember { mutableStateOf(5) }
    var customQuote by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var currentStep by remember { mutableStateOf(1) } 
    var showCustomCategoryEditor by remember { mutableStateOf(false) }
    var editingCustomCategory by remember { mutableStateOf<CustomCategory?>(null) }
    var customCategories by remember { mutableStateOf<List<CustomCategory>>(CustomCategoryManager.getCategories(context)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val apps = packages
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
                .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), getCategoryForApp(it, it.packageName)) }
                .sortedBy { it.appName }
            installedApps = apps
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (currentStep == 1) "Shield New Apps" else "Configure Shield", 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (currentStep == 2) currentStep = 1 
                        else navController.popBackStack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background, 
                    titleContentColor = MaterialTheme.colorScheme.onBackground, 
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentStep == 1) {
                AnimatedVisibility(
                    visible = selectedApps.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Button(
                                onClick = { currentStep = 2 },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                                shape = RoundedCornerShape(26.dp)
                            ) {
                                Text("Configure Shield (${selectedApps.size} Apps)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = {
                                val newRestrictions = selectedApps.map { app ->
                                    AppRestriction(
                                        packageName = app.packageName,
                                        appName = app.appName,
                                        mode = appModes[app.packageName] ?: "HARD",
                                        restrictionMode = restrictionMode,
                                        timeLimitMinutes = if (restrictionMode == "TIME_LIMIT") timeLimitMinutes else 0,
                                        clickLimitCount = if (restrictionMode == "CLICK_LIMIT") clickLimitCount else 0,
                                        customQuote = customQuote.trim(),
                                        isRestricted = true
                                    )
                                }
                                viewModel.addRestrictions(newRestrictions)
                                navController.popBackStack()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Text("Enable Shield for ${selectedApps.size} Apps", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "stepAnimation"
            ) { step ->
                if (step == 1) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search applications...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(AppCategory.entries.toTypedArray()) { category ->
                                val isSelected = selectedCategory == category
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                        .clickable { selectedCategory = category }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category.title,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            items(customCategories, key = { it.id }) { cat ->
                                val isSelected = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cat.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Edit Category",
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    editingCustomCategory = cat
                                                    showCustomCategoryEditor = true
                                                }
                                        )
                                    }
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                        .clickable {
                                            editingCustomCategory = null
                                            showCustomCategoryEditor = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add Filter", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "New Filter",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }

                        val filteredApps = remember(installedApps, selectedCategory, searchQuery) {
                            var list = when (val cat = selectedCategory) {
                                is AppCategory -> {
                                    if (cat == AppCategory.ALL) installedApps
                                    else installedApps.filter { it.category == cat }
                                }
                                is CustomCategory -> {
                                    installedApps.filter { cat.packages.contains(it.packageName) }
                                }
                                else -> installedApps
                            }
                            if (searchQuery.isNotBlank()) {
                                list = list.filter { it.appName.contains(searchQuery, ignoreCase = true) }
                            }
                            list
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${filteredApps.size} apps available",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val allSelected = filteredApps.isNotEmpty() && selectedApps.containsAll(filteredApps)
                                        if (allSelected) {
                                            TextButton(
                                                onClick = { selectedApps = selectedApps - filteredApps.toSet() }
                                            ) {
                                                Text("Deselect All", color = MaterialTheme.colorScheme.error)
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { selectedApps = selectedApps + filteredApps }
                                            ) {
                                                Text("Select All", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }

                                items(filteredApps, key = { it.packageName }) { app ->
                                    val isSelected = selectedApps.contains(app)
                                    val pm = context.packageManager
                                    var icon by remember(app.packageName) {
                                        mutableStateOf(ImageUtils.getAppIcon(pm, app.packageName))
                                    }
                                    LaunchedEffect(app.packageName) {
                                        if (icon == null) {
                                            val loaded = withContext(Dispatchers.IO) {
                                                ImageUtils.getAppIcon(pm, app.packageName)
                                            }
                                            icon = loaded
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                            .border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                                RoundedCornerShape(20.dp)
                                            )
                                            .clickable {
                                                selectedApps = if (isSelected) {
                                                    selectedApps - app
                                                } else {
                                                    selectedApps + app
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val currentIcon = icon
                                            if (currentIcon != null) {
                                                Image(
                                                    bitmap = currentIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.appName,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = app.category.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) } 
                            }
                        }
                    }
                } else {
                    // STEP 2: Configure Shield
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val hardApps = selectedApps.filter { appModes[it.packageName] != "SOFT" }
                        val softApps = selectedApps.filter { appModes[it.packageName] == "SOFT" }

                        com.focusbyrj.app.ui.components.AppModeDropZone(
                            title = "HARD MODE",
                            description = "No bypass allowed",
                            apps = hardApps,
                            onAppClick = { appModes = appModes + (it.packageName to "SOFT") },
                            borderColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        com.focusbyrj.app.ui.components.AppModeDropZone(
                            title = "SOFT MODE",
                            description = "10 sec wait bypass",
                            apps = softApps,
                            onAppClick = { appModes = appModes + (it.packageName to "HARD") },
                            borderColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        
                        // CUSTOM RESTRICTIONS (3 boxes: Simple, Time Limit, Click Limit)
                        CustomRestrictionSection(
                            restrictionMode = restrictionMode,
                            onRestrictionModeChange = { restrictionMode = it },
                            timeLimitMinutes = timeLimitMinutes,
                            onTimeLimitChange = { timeLimitMinutes = it },
                            clickLimitCount = clickLimitCount,
                            onClickLimitChange = { clickLimitCount = it },
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "CUSTOM QUOTE (OPTIONAL)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = customQuote,
                            onValueChange = { customQuote = it },
                            placeholder = { Text(text = "Is this urgent, or are you chasing cheap dopamine?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
        
        if (showCustomCategoryEditor) {
            CustomCategoryEditor(
                context = context,
                installedApps = installedApps,
                editingCategory = editingCustomCategory,
                onSave = { name, packages ->
                    CustomCategoryManager.saveCategory(
                        context, 
                        editingCustomCategory?.id, 
                        name.trim(), 
                        packages
                    )
                    customCategories = CustomCategoryManager.getCategories(context)
                    showCustomCategoryEditor = false
                },
                onDismiss = { showCustomCategoryEditor = false }
            )
        }
    }
}


@Composable
fun ModeSelector(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
