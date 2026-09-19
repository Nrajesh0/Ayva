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

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.Image
import com.focusbyrj.app.util.ImageUtils
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusbyrj.app.ui.components.CustomRestrictionSection
import com.focusbyrj.app.ui.theme.*
import com.focusbyrj.app.ui.viewmodels.FocusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(viewModel: FocusViewModel) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    var showCreateScreen by remember { mutableStateOf(false) }
    var scheduleToEdit by remember { mutableStateOf<com.focusbyrj.app.data.FocusSchedule?>(null) }

    if (showCreateScreen || scheduleToEdit != null) {
        CreateRoutineScreen(
            scheduleToEdit = scheduleToEdit,
            onBack = { 
                showCreateScreen = false
                scheduleToEdit = null
            },
            onSave = { name, startH, startM, endH, endM, days, mode, apps, restrictionMode, timeLimitMinutes, clickLimitCount ->
                if (scheduleToEdit != null) {
                    viewModel.updateSchedule(scheduleToEdit!!.copy(
                        name = name, startHour = startH, startMinute = startM,
                        endHour = endH, endMinute = endM, daysOfWeek = days,
                        mode = mode, appsToBlock = apps,
                        restrictionMode = restrictionMode,
                        timeLimitMinutes = timeLimitMinutes,
                        clickLimitCount = clickLimitCount
                    ))
                } else {
                    viewModel.addSchedule(name, startH, startM, endH, endM, days, mode, apps, restrictionMode, timeLimitMinutes, clickLimitCount)
                }
                showCreateScreen = false
                scheduleToEdit = null
            }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateScreen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Schedule")
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Automated focus windows",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (schedules.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No routines active.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap + to automate your boundaries.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(
                            schedules,
                            key = { it.id },
                            contentType = { "RoutineCard" }
                        ) { schedule ->
                            RoutineCard(
                                schedule = schedule, 
                                onToggle = { isChecked -> viewModel.updateSchedule(schedule.copy(isEnabled = isChecked)) },
                                onEdit = { scheduleToEdit = schedule },
                                onDelete = { viewModel.deleteSchedule(schedule) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineCard(
    schedule: com.focusbyrj.app.data.FocusSchedule, 
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit, 
    onDelete: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (schedule.isEnabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            )
            .border(
                1.dp, 
                if (schedule.isEnabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                }, 
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (schedule.isEnabled) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (schedule.isEnabled) "⚡" else "💤",
                            fontSize = 16.sp
                        )
                    }

                    Text(
                        text = schedule.name, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), 
                        color = if (schedule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Switch(
                            checked = schedule.isEnabled,
                            onCheckedChange = { onToggle(it) },
                            modifier = Modifier.padding(end = 2.dp)
                        )
                        if (!schedule.isEnabled) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 2.dp, end = 2.dp)
                            ) {
                                Text(
                                    text = "PAUSED",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val timeString = "${String.format("%02d:%02d", schedule.startHour, schedule.startMinute)} - ${String.format("%02d:%02d", schedule.endHour, schedule.endMinute)}"
            Text(
                timeString, 
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), 
                color = if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val days = listOf("S", "M", "T", "W", "T", "F", "S")
                val activeDays = schedule.daysOfWeek.split(",")
                days.forEachIndexed { index, day ->
                    val isActive = activeDays.contains((index + 1).toString())
                    val dayBg = if (!schedule.isEnabled) {
                        if (isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    } else {
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    val dayColor = if (!schedule.isEnabled) {
                        if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(dayBg)
                            .border(
                                1.dp, 
                                if (isActive && schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), 
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day, 
                            color = dayColor, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val context = LocalContext.current
            val pm = context.packageManager
            val appCount = remember(schedule.appsToBlock) {
                if (schedule.appsToBlock.isBlank()) 0
                else {
                    schedule.appsToBlock.split(",").count { entry ->
                        val pkg = entry.split("|")[0].trim()
                        if (pkg.isEmpty()) false
                        else {
                            try {
                                pm.getApplicationInfo(pkg, 0)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                }
            }
            Text(
                if (schedule.isEnabled) "🛡️ $appCount Apps Shielded" else "⏸️ $appCount Apps Shielded • Paused", 
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoutineScreen(
    scheduleToEdit: com.focusbyrj.app.data.FocusSchedule? = null,
    onBack: () -> Unit,
    onSave: (String, Int, Int, Int, Int, String, String, String, String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var name by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.name ?: "Deep Focus") }
    var startHour by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.startHour ?: 9) }
    var startMinute by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.startMinute ?: 0) }
    var endHour by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.endHour ?: 17) }
    var endMinute by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.endMinute ?: 0) }
    var selectedDays by remember(scheduleToEdit) { 
        mutableStateOf(
            scheduleToEdit?.daysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(2,3,4,5,6)
        ) 
    } 
    var restrictionMode by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.restrictionMode ?: "SIMPLE") }
    var timeLimitMinutes by remember(scheduleToEdit) { mutableStateOf(if (scheduleToEdit?.timeLimitMinutes != null && scheduleToEdit.timeLimitMinutes > 0) scheduleToEdit.timeLimitMinutes else 15) }
    var clickLimitCount by remember(scheduleToEdit) { mutableStateOf(if (scheduleToEdit?.clickLimitCount != null && scheduleToEdit.clickLimitCount > 0) scheduleToEdit.clickLimitCount.coerceIn(1, 20) else 5) }
    var mode by remember(scheduleToEdit) { mutableStateOf(scheduleToEdit?.mode ?: "HARD") }
    var appModes by remember(scheduleToEdit) { 
        val initialMap = scheduleToEdit?.appsToBlock?.split(",")?.mapNotNull { 
            val parts = it.split("|")
            if (parts.size > 1) parts[0].trim() to parts[1].trim() else null
        }?.toMap() ?: emptyMap<String, String>()
        
        val validMap = initialMap.filter { (pkg, _) ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
        mutableStateOf(validMap)
    }
    var selectedApps by remember(scheduleToEdit) { 
        val initialPkgs = scheduleToEdit?.appsToBlock?.split(",")?.map { it.split("|")[0].trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val validPkgs = initialPkgs.filter { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }.toSet()
        mutableStateOf(validPkgs)
    }

    var showAppSelector by remember { mutableStateOf(false) }

    val selectedInstalledApps = remember(selectedApps) {
        selectedApps.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                InstalledApp(pkg, appName, getCategoryForApp(appInfo, pkg))
            } catch (e: Exception) {
                null
            }
        }
    }

    if (showAppSelector) {
        MultiAppSelectorScreen(
            selectedApps = selectedApps,
            onClose = { showAppSelector = false },
            onSave = { newSelection ->
                selectedApps = newSelection
                showAppSelector = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                if (scheduleToEdit != null) "Edit Routine" else "Create Routine", 
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), 
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("TIME WINDOW", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TimePickerBox("Start Time", startHour, startMinute, modifier = Modifier.weight(1f)) { h, m -> startHour = h; startMinute = m }
                    TimePickerBox("End Time", endHour, endMinute, modifier = Modifier.weight(1f)) { h, m -> endHour = h; endMinute = m }
                }
                
                val isSameTime = (startHour * 60 + startMinute) == (endHour * 60 + endMinute)
                if (isSameTime) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start time and end time cannot be the same.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("REPEAT", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val days = listOf("S", "M", "T", "W", "T", "F", "S")
                    days.forEachIndexed { index, day ->
                        val isSelected = selectedDays.contains(index + 1)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    val newSet = selectedDays.toMutableSet()
                                    if (isSelected) newSet.remove(index + 1) else newSet.add(index + 1)
                                    selectedDays = newSet
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day, 
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Custom Restrictions (Simple, Time Limit, Click Limit)
                CustomRestrictionSection(
                    restrictionMode = restrictionMode,
                    onRestrictionModeChange = { restrictionMode = it },
                    timeLimitMinutes = timeLimitMinutes,
                    onTimeLimitChange = { timeLimitMinutes = it },
                    clickLimitCount = clickLimitCount,
                    onClickLimitChange = { clickLimitCount = it },
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APPS TO SHIELD", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showAppSelector = true }) {
                        Text(if (selectedApps.isEmpty()) "Select Apps" else "Add/Remove Apps", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                if (selectedApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .clickable { showAppSelector = true }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tap to select apps", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val hardApps = selectedInstalledApps.filter { (appModes[it.packageName] ?: mode) != "SOFT" }
                    val softApps = selectedInstalledApps.filter { (appModes[it.packageName] ?: mode) == "SOFT" }

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
                        borderColor = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = {
                        val daysString = selectedDays.sorted().joinToString(",")
                        val validSelectedApps = selectedApps.filter { pkg ->
                            try {
                                context.packageManager.getApplicationInfo(pkg, 0)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                        val appsString = validSelectedApps.joinToString(",") { pkg ->
                            "$pkg|${appModes[pkg] ?: mode}"
                        }
                        onSave(
                            name,
                            startHour,
                            startMinute,
                            endHour,
                            endMinute,
                            daysString,
                            mode,
                            appsString,
                            restrictionMode,
                            if (restrictionMode == "TIME_LIMIT") timeLimitMinutes else 0,
                            if (restrictionMode == "CLICK_LIMIT") clickLimitCount else 0
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary, 
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    enabled = name.isNotBlank() && selectedDays.isNotEmpty() && ((startHour * 60 + startMinute) != (endHour * 60 + endMinute))
                ) {
                    Text("Save Routine", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun TimePickerBox(label: String, hour: Int, minute: Int, modifier: Modifier = Modifier, onTimeSelected: (Int, Int) -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable {
                TimePickerDialog(context, { _, h, m -> onTimeSelected(h, m) }, hour, minute, true).show()
            }
            .padding(16.dp)
    ) {
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${String.format("%02d:%02d", hour, minute)}", 
                color = MaterialTheme.colorScheme.onSurface, 
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun ModeSelectorBox(title: String, desc: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha=0.15f) else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

sealed interface ScheduleCategoryOption {
    data class Default(val category: AppCategory) : ScheduleCategoryOption
    data class Custom(val custom: com.focusbyrj.app.util.CustomCategory) : ScheduleCategoryOption
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAppSelectorScreen(
    selectedApps: Set<String>,
    onClose: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var currentSelection by remember { mutableStateOf(selectedApps) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<ScheduleCategoryOption>(ScheduleCategoryOption.Default(AppCategory.ALL)) }
    
    val customCategories by com.focusbyrj.app.util.CustomCategoryManager.categories.collectAsState(initial = emptyList())
    var showCustomCategoryEditor by remember { mutableStateOf(false) }
    var editingCustomCategory by remember { mutableStateOf<com.focusbyrj.app.util.CustomCategory?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appsList = packages.mapNotNull { info ->
                val appName = pm.getApplicationLabel(info).toString()
                if (info.packageName != context.packageName && pm.getLaunchIntentForPackage(info.packageName) != null) {
                    InstalledApp(info.packageName, appName, getCategoryForApp(info, info.packageName))
                } else null
            }.sortedBy { it.appName.lowercase() }
            
            withContext(Dispatchers.Main) {
                installedApps = appsList
                isLoading = false
            }
        }
    }

    if (showCustomCategoryEditor) {
        CustomCategoryEditor(
            context = context,
            installedApps = installedApps,
            editingCategory = editingCustomCategory,
            onSave = { name, pkgs ->
                com.focusbyrj.app.util.CustomCategoryManager.saveCategory(context, editingCustomCategory?.id, name, pkgs)
                showCustomCategoryEditor = false
                editingCustomCategory = null
            },
            onDismiss = {
                showCustomCategoryEditor = false
                editingCustomCategory = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) }
            Text("Select Apps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            TextButton(onClick = { 
                val validSelection = currentSelection.filter { pkg -> installedApps.any { it.packageName == pkg } }.toSet()
                onSave(validSelection) 
            }) {
                Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val standardOptions = AppCategory.entries.map { ScheduleCategoryOption.Default(it) }
                val customOptions = customCategories.map { ScheduleCategoryOption.Custom(it) }
                val allOptions = standardOptions + customOptions

                items(allOptions) { option ->
                    val isCatSelected = selectedCategory == option
                    val title = when (option) {
                        is ScheduleCategoryOption.Default -> option.category.title
                        is ScheduleCategoryOption.Custom -> option.custom.name
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = option }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                color = if (isCatSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (option is ScheduleCategoryOption.Custom && isCatSelected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.Edit, 
                                    contentDescription = "Edit", 
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp).clickable {
                                        editingCustomCategory = option.custom
                                        showCustomCategoryEditor = true
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.Delete, 
                                    contentDescription = "Delete", 
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp).clickable {
                                        com.focusbyrj.app.util.CustomCategoryManager.deleteCategory(context, option.custom.id)
                                        selectedCategory = ScheduleCategoryOption.Default(AppCategory.ALL)
                                    }
                                )
                            }
                        }
                    }
                }
                
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                            .clickable { 
                                editingCustomCategory = null
                                showCustomCategoryEditor = true
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "+ Custom",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            
            val filteredApps = remember(installedApps, selectedCategory) {
                when (val cat = selectedCategory) {
                    is ScheduleCategoryOption.Default -> {
                        if (cat.category == AppCategory.ALL) installedApps
                        else installedApps.filter { it.category == cat.category }
                    }
                    is ScheduleCategoryOption.Custom -> {
                        installedApps.filter { cat.custom.packages.contains(it.packageName) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredApps.size} apps",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Row {
                    val filteredPackages = filteredApps.map { it.packageName }.toSet()
                    val allSelected = filteredPackages.isNotEmpty() && currentSelection.containsAll(filteredPackages)
                    if (allSelected) {
                        TextButton(
                            onClick = { currentSelection = currentSelection - filteredPackages }
                        ) {
                            Text("Deselect All", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(
                            onClick = { currentSelection = currentSelection + filteredPackages }
                        ) {
                            Text("Select All", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val pm = context.packageManager
                    val icon = remember(app.packageName) { ImageUtils.getAppIcon(pm, app.packageName) }
                    val isSelected = currentSelection.contains(app.packageName)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            .clickable {
                                val newSet = currentSelection.toMutableSet()
                                if (isSelected) newSet.remove(app.packageName) else newSet.add(app.packageName)
                                currentSelection = newSet
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(app.category.title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                            if (isSelected) {
                                Icon(androidx.compose.material.icons.Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            } else {
                                Box(modifier = Modifier.size(28.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

