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

package com.focusbyrj.app.ui.screens.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.statusBarsPadding
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.ProfileAvatarManager
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(
    viewModel: NotesViewModel = viewModel(),
    activeStreakDays: Int = 0,
    onOpenAccount: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToTodos: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isGridView by viewModel.isGridView.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val selectedColorFilter by viewModel.selectedColorFilter.collectAsState()
    val selectedLabelFilter by viewModel.selectedLabelFilter.collectAsState()
    val allNotes by viewModel.displayedNotes.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()
    val editingState by viewModel.editingState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val economyProfile by FocusEconomyManager.profileFlow.collectAsState()
    val vaultStatus by viewModel.vaultStatus.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()

    var showSelectionColorPicker by remember { mutableStateOf(false) }
    var showSelectionLabelsDialog by remember { mutableStateOf(false) }
    var showSelectionMoreMenu by remember { mutableStateOf(false) }
    var showVaultUnlockDialog by remember { mutableStateOf(false) }
    var showVaultFirstTimeDialog by remember { mutableStateOf(false) }
    var showVaultSettingsDialog by remember { mutableStateOf(false) }
    var showVaultChangePinDialog by remember { mutableStateOf(false) }
    var showVaultDisableConfirmDialog by remember { mutableStateOf(false) }

    val selectedNotes = remember(allNotes, selectedNoteIds) { allNotes.filter { it.id in selectedNoteIds } }
    val allSelectedPinned = remember(selectedNotes) { selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned } }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    BackHandler(enabled = !isSelectionMode && currentFolder == NoteFolder.ARCHIVE) {
        viewModel.lockVault()
    }

    // Auto-lock vault when screen goes into background or is disposed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (viewModel.currentFolder.value == NoteFolder.ARCHIVE) {
                    viewModel.lockVault()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (viewModel.currentFolder.value == NoteFolder.ARCHIVE) {
                viewModel.lockVault()
            }
        }
    }

    var showEditLabelsDialog by remember { mutableStateOf(false) }
    var showSketchDialogFromDock by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startVoiceRecording()
            } else {
                Toast.makeText(context, "Microphone permission is required to record voice memos", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun checkAndStartRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startVoiceRecording()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val dockPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.openNewNoteWithImages(uris)
            }
        }
    )

    val dockSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    viewModel.openNewNoteWithVoice(matches[0])
                }
            }
        }
    )

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val gridState = rememberLazyStaggeredGridState()
    val haptic = LocalHapticFeedback.current
    val pinnedNotes = remember(allNotes) { allNotes.filter { it.isPinned } }
    val otherNotes = remember(allNotes) { allNotes.filter { !it.isPinned } }

    val cardBounds = remember { mutableStateMapOf<Long, Rect>() }
    var draggedNoteId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var fingerRootPosition by remember { mutableStateOf(Offset.Zero) }

    var localPinnedNotes by remember(pinnedNotes) { mutableStateOf(pinnedNotes) }
    var localOtherNotes by remember(otherNotes) { mutableStateOf(otherNotes) }

    val effectivePinnedNotes = if (draggedNoteId != null) localPinnedNotes else pinnedNotes
    val effectiveOtherNotes = if (draggedNoteId != null) localOtherNotes else otherNotes

    LaunchedEffect(pinnedNotes) {
        if (draggedNoteId == null) localPinnedNotes = pinnedNotes
    }
    LaunchedEffect(otherNotes) {
        if (draggedNoteId == null) localOtherNotes = otherNotes
    }

    val handleDragStart: (Long, Offset) -> Unit = { noteId, downPos ->
        draggedNoteId = noteId
        val bounds = cardBounds[noteId]
        val origin = bounds?.topLeft ?: Offset.Zero
        fingerRootPosition = origin + downPos
        dragOffset = Offset.Zero
    }

    val handleDrag: (NoteEntity, Offset) -> Unit = { note, delta ->
        fingerRootPosition += delta
        dragOffset += delta

        val isPinned = note.isPinned
        val targetList = if (isPinned) localPinnedNotes else localOtherNotes
        val fromIndex = targetList.indexOfFirst { it.id == note.id }
        if (fromIndex != -1) {
            val toIndex = targetList.indices.minByOrNull { idx ->
                val other = targetList[idx]
                if (other.id == note.id) {
                    Float.MAX_VALUE
                } else {
                    val b = cardBounds[other.id]
                    if (b != null) {
                        val dist = (b.center - fingerRootPosition).getDistance()
                        if (b.contains(fingerRootPosition)) {
                            dist * 0.5f // Strongly prioritize bounding box overlap
                        } else if (dist < 400f) {
                            dist
                        } else {
                            Float.MAX_VALUE
                        }
                    } else Float.MAX_VALUE
                }
            } ?: -1

            if (toIndex != -1 && toIndex != fromIndex) {
                val targetBounds = cardBounds[targetList[toIndex].id]
                val currentBounds = cardBounds[note.id]
                val updated = targetList.toMutableList()
                val item = updated.removeAt(fromIndex)
                updated.add(toIndex, item)
                
                // Adjust dragOffset smoothly so the dragging card doesn't jump when slots swap
                if (targetBounds != null && currentBounds != null) {
                    val posDelta = targetBounds.topLeft - currentBounds.topLeft
                    dragOffset -= posDelta
                    cardBounds[note.id] = targetBounds
                    cardBounds[targetList[toIndex].id] = currentBounds
                }
                
                if (isPinned) {
                    localPinnedNotes = updated
                } else {
                    localOtherNotes = updated
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val handleDragEnd: (NoteEntity) -> Unit = { note ->
        val isPinned = note.isPinned
        val listToSave = if (isPinned) localPinnedNotes else localOtherNotes
        draggedNoteId = null
        dragOffset = Offset.Zero
        viewModel.reorderNotes(listToSave)
    }

    val searchBarBg = MaterialTheme.colorScheme.surfaceVariant
    val searchBarBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val contentTextColor = MaterialTheme.colorScheme.onSurface

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = contentTextColor,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = contentTextColor
                    )
                }

                HorizontalDivider(color = contentTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 8.dp))

                // 1. NOTES FOLDER
                val isNotesSelected = currentFolder == NoteFolder.NOTES && selectedLabelFilter == null
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = if (isNotesSelected) Icons.AutoMirrored.Filled.StickyNote2 else Icons.AutoMirrored.Outlined.StickyNote2,
                            contentDescription = "Notes",
                            tint = if (isNotesSelected) MaterialTheme.colorScheme.primary else contentTextColor.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isNotesSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isNotesSelected) MaterialTheme.colorScheme.primary else contentTextColor
                        )
                    },
                    selected = isNotesSelected,
                    onClick = {
                        viewModel.setLabelFilter(null)
                        viewModel.setFolder(NoteFolder.NOTES)
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                HorizontalDivider(color = contentTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                // LABELS SECTION HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LABELS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            fontSize = 11.sp
                        ),
                        color = contentTextColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                showEditLabelsDialog = true
                                coroutineScope.launch { drawerState.close() }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // DYNAMIC LABELS LIST
                allLabels.forEach { label ->
                    val isLabelSelected = selectedLabelFilter == label
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = if (isLabelSelected) Icons.Filled.Label else Icons.Outlined.Label,
                                contentDescription = label,
                                tint = if (isLabelSelected) MaterialTheme.colorScheme.primary else contentTextColor.copy(alpha = 0.7f)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isLabelSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isLabelSelected) MaterialTheme.colorScheme.primary else contentTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = isLabelSelected,
                        onClick = {
                            viewModel.setLabelFilter(if (isLabelSelected) null else label)
                            viewModel.setFolder(NoteFolder.NOTES)
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                // CREATE NEW LABEL ITEM
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create new label",
                            tint = contentTextColor.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Text(
                            text = "Create new label",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = contentTextColor
                        )
                    },
                    selected = false,
                    onClick = {
                        showEditLabelsDialog = true
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                HorizontalDivider(color = contentTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                // 3. ARCHIVE FOLDER (SECRET VAULT)
                val isArchiveSelected = currentFolder == NoteFolder.ARCHIVE
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = if (isArchiveSelected) Icons.Filled.Archive else Icons.Outlined.Archive,
                            contentDescription = "Archive",
                            tint = if (isArchiveSelected) MaterialTheme.colorScheme.primary else contentTextColor.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Archive",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isArchiveSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isArchiveSelected) MaterialTheme.colorScheme.primary else contentTextColor
                            )
                            if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) {
                                Icon(
                                    imageVector = if (isVaultUnlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                    contentDescription = "Vault Protected",
                                    tint = if (isArchiveSelected) MaterialTheme.colorScheme.primary else contentTextColor.copy(alpha = 0.50f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    },
                    selected = isArchiveSelected,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        when (vaultStatus) {
                            ArchiveVaultSecurity.VaultStatus.NOT_CONFIGURED -> {
                                showVaultFirstTimeDialog = true
                            }
                            ArchiveVaultSecurity.VaultStatus.ENABLED -> {
                                if (isVaultUnlocked) {
                                    viewModel.setFolder(NoteFolder.ARCHIVE)
                                } else {
                                    showVaultUnlockDialog = true
                                }
                            }
                            ArchiveVaultSecurity.VaultStatus.DISABLED -> {
                                viewModel.setFolder(NoteFolder.ARCHIVE)
                            }
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // 4. TRASH FOLDER
                val isTrashSelected = currentFolder == NoteFolder.TRASH
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = if (isTrashSelected) Icons.Filled.Delete else Icons.Outlined.Delete,
                            contentDescription = "Trash",
                            tint = if (isTrashSelected) MaterialTheme.colorScheme.primary else contentTextColor.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Text(
                            text = "Trash",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isTrashSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isTrashSelected) MaterialTheme.colorScheme.primary else contentTextColor
                        )
                    },
                    selected = isTrashSelected,
                    onClick = {
                        viewModel.setFolder(NoteFolder.TRASH)
                        coroutineScope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                HorizontalDivider(color = contentTextColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "FOCUS BY RJ",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        fontSize = 11.sp
                    ),
                    color = contentTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                )

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Dashboard",
                            tint = contentTextColor.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = contentTextColor
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToDashboard()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Todos",
                            tint = contentTextColor.copy(alpha = 0.7f)
                        )
                    },
                    label = {
                        Text(
                            text = "Tasks & Todos",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = contentTextColor
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToTodos()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // =========================================================
                // GOOGLE KEEP TOP BAR: SELECTION BAR or SEARCH CAPSULE PILL
                // =========================================================
                if (isSelectionMode) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 4.dp,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.clearSelection() },
                                    modifier = Modifier.size(48.dp).testTag("selection_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Close selection",
                                        tint = contentTextColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "${selectedNoteIds.size}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 19.sp
                                    ),
                                    color = contentTextColor
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (currentFolder == NoteFolder.TRASH) {
                                    // Restore selected notes from trash
                                    IconButton(
                                        onClick = { viewModel.restoreSelectedNotes() },
                                        modifier = Modifier.size(44.dp).testTag("selection_restore_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Restore,
                                            contentDescription = "Restore",
                                            tint = contentTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Permanently delete selected notes
                                    IconButton(
                                        onClick = { viewModel.deleteSelectedNotesPermanently() },
                                        modifier = Modifier.size(44.dp).testTag("selection_delete_forever_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteForever,
                                            contentDescription = "Delete forever",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else {
                                    // Pin / unpin selected notes
                                    IconButton(
                                        onClick = { viewModel.pinSelectedNotes() },
                                        modifier = Modifier.size(44.dp).testTag("selection_pin_button")
                                    ) {
                                        Icon(
                                            imageVector = if (allSelectedPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = if (allSelectedPinned) "Unpin selected notes" else "Pin selected notes",
                                            tint = if (allSelectedPinned) MaterialTheme.colorScheme.primary else contentTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Recolor selected notes
                                    IconButton(
                                        onClick = { showSelectionColorPicker = true },
                                        modifier = Modifier.size(44.dp).testTag("selection_color_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Palette,
                                            contentDescription = "Color selected notes",
                                            tint = contentTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Label selected notes
                                    IconButton(
                                        onClick = { showSelectionLabelsDialog = true },
                                        modifier = Modifier.size(44.dp).testTag("selection_label_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Label,
                                            contentDescription = "Label selected notes",
                                            tint = contentTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Archive / unarchive selected notes
                                    IconButton(
                                        onClick = {
                                            if (currentFolder == NoteFolder.ARCHIVE) {
                                                viewModel.unarchiveSelectedNotes()
                                            } else {
                                                viewModel.archiveSelectedNotes()
                                            }
                                        },
                                        modifier = Modifier.size(44.dp).testTag("selection_archive_button")
                                    ) {
                                        Icon(
                                            imageVector = if (currentFolder == NoteFolder.ARCHIVE) Icons.Filled.Unarchive else Icons.Outlined.Archive,
                                            contentDescription = if (currentFolder == NoteFolder.ARCHIVE) "Unarchive notes" else "Archive notes",
                                            tint = contentTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // More overflow menu
                                    Box {
                                        IconButton(
                                            onClick = { showSelectionMoreMenu = true },
                                            modifier = Modifier.size(44.dp).testTag("selection_more_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "More selection actions",
                                                tint = contentTextColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showSelectionMoreMenu,
                                            onDismissRequest = { showSelectionMoreMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                                                },
                                                onClick = {
                                                    showSelectionMoreMenu = false
                                                    viewModel.trashSelectedNotes()
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Make a copy") },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                                },
                                                onClick = {
                                                    showSelectionMoreMenu = false
                                                    viewModel.duplicateSelectedNotes()
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Send") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                                                },
                                                onClick = {
                                                    showSelectionMoreMenu = false
                                                    viewModel.shareSelectedNotes(context)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Copy to clipboard") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                                },
                                                onClick = {
                                                    showSelectionMoreMenu = false
                                                    viewModel.copySelectedNotesToClipboard(context)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Select all") },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.SelectAll, contentDescription = null, modifier = Modifier.size(20.dp))
                                                },
                                                onClick = {
                                                    showSelectionMoreMenu = false
                                                    viewModel.selectAllNotes()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(start = 16.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .shadow(
                                    elevation = 2.dp,
                                    shape = CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.08f),
                                    spotColor = Color.Black.copy(alpha = 0.04f)
                                ),
                            shape = CircleShape,
                            color = searchBarBg,
                            border = BorderStroke(1.dp, searchBarBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { coroutineScope.launch { drawerState.open() } },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Open navigation drawer",
                                        tint = contentTextColor.copy(alpha = 0.75f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("keep_search_input"),
                                    textStyle = TextStyle(
                                        color = contentTextColor,
                                        fontSize = 15.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = when (currentFolder) {
                                                    NoteFolder.NOTES -> "Search your notes"
                                                    NoteFolder.ARCHIVE -> "Search archive"
                                                    NoteFolder.TRASH -> "Search trash"
                                                },
                                                style = TextStyle(
                                                    color = contentTextColor.copy(alpha = 0.50f),
                                                    fontSize = 15.sp
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            tint = contentTextColor.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Layout Mode Toggle (2-column masonry grid vs 1-column single list view)
                                IconButton(
                                    onClick = { viewModel.toggleLayoutMode() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("keep_layout_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                                        contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                                        tint = contentTextColor.copy(alpha = 0.75f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Account Profile Avatar - matching MainActivity TopAppBar profile avatar alignment & border
                        val avatarRes = ProfileAvatarManager.getAvatarImageRes(economyProfile.selectedAvatar, economyProfile.avatarTier)
                        val avatarBorder = ProfileAvatarManager.getAvatarBorderColor(economyProfile.selectedAvatar, economyProfile.avatarTier)

                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.8.dp, avatarBorder, CircleShape)
                                .clickable(onClick = onOpenAccount),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = avatarRes),
                                contentDescription = "Profile Account",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                // Header title if in Archive or Trash
                if (currentFolder != NoteFolder.NOTES) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentFolder.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = contentTextColor
                            )
                            if (currentFolder == NoteFolder.ARCHIVE && vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Encrypted",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        if (currentFolder == NoteFolder.ARCHIVE) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { showVaultSettingsDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                        contentDescription = "Vault Settings",
                                        tint = if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) MaterialTheme.colorScheme.primary else contentTextColor.copy(alpha = 0.60f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) "Vault Options" else "Set Passcode",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED) MaterialTheme.colorScheme.onSurface else contentTextColor.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }

                        if (currentFolder == NoteFolder.TRASH && allNotes.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showEmptyTrashDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "Empty trash",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Empty Trash",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // =========================================================
                // HORIZONTAL FILTER CHIPS (IF LABELS OR COLOR FILTERS EXIST)
                // =========================================================
                if (allLabels.isNotEmpty() || selectedColorFilter != null) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedColorFilter != null) {
                            item {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier.clickable { viewModel.setColorFilter(null) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        val themeName = KeepColorPalette.getColor(selectedColorFilter).name
                                        Text(
                                            text = "Theme: $themeName",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear color filter",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(allLabels) { label ->
                            val isSelected = label.equals(selectedLabelFilter, ignoreCase = true)
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else searchBarBg,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else searchBarBorder),
                                modifier = Modifier.clickable { viewModel.setLabelFilter(label) }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else contentTextColor.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // =========================================================
                // MASONRY STAGGERED GRID (OR EMPTY STATE)
                // =========================================================
                if (allNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (currentFolder) {
                                            NoteFolder.NOTES -> Icons.AutoMirrored.Outlined.StickyNote2
                                            NoteFolder.ARCHIVE -> Icons.Outlined.Archive
                                            NoteFolder.TRASH -> Icons.Outlined.Delete
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No matching notes found"
                                } else {
                                    when (currentFolder) {
                                        NoteFolder.NOTES -> "Notes you add appear here"
                                        NoteFolder.ARCHIVE -> "Archived notes appear here"
                                        NoteFolder.TRASH -> "No notes in Trash"
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                ),
                                color = contentTextColor.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = when (currentFolder) {
                                    NoteFolder.NOTES -> "Capture ideas, quick checklists, and thoughts seamlessly."
                                    NoteFolder.ARCHIVE -> "Your saved archive is safely kept out of sight."
                                    NoteFolder.TRASH -> "Deleted notes can be restored anytime before emptying trash."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = contentTextColor.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        state = gridState,
                        columns = StaggeredGridCells.Fixed(if (isGridView) 2 else 1),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // PINNED SECTION (Only in Notes folder)
                        if (currentFolder == NoteFolder.NOTES && effectivePinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Text(
                                    text = "PINNED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp,
                                        fontSize = 11.sp
                                    ),
                                    color = contentTextColor.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
                                )
                            }

                            items(effectivePinnedNotes, key = { it.id }) { note ->
                                val isSelected = selectedNoteIds.contains(note.id)
                                val isDragging = draggedNoteId == note.id
                                KeepNoteCard(
                                    note = note,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    isDragging = isDragging,
                                    dragOffset = if (isDragging) dragOffset else Offset.Zero,
                                    onStartDrag = { pos -> handleDragStart(note.id, pos) },
                                    onDrag = { delta -> handleDrag(note, delta) },
                                    onEndDrag = { handleDragEnd(note) },
                                    onToggleSelect = { viewModel.toggleNoteSelection(note.id) },
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleNoteSelection(note.id)
                                        } else if (currentFolder != NoteFolder.TRASH) {
                                            viewModel.openExistingNote(note)
                                        }
                                    },
                                    onLongClick = null,
                                    onTogglePin = { viewModel.togglePin(note) },
                                    activePlayingAudioPath = playbackState.currentPath,
                                    isAudioPlaying = playbackState.isPlaying,
                                    onToggleAudioPlay = { uri -> viewModel.toggleAudioPlayback(uri) },
                                    modifier = Modifier
                                        .animateItem()
                                        .onGloballyPositioned { coords ->
                                            if (draggedNoteId != note.id) {
                                                cardBounds[note.id] = coords.boundsInRoot()
                                            }
                                        }
                                )
                            }

                            if (effectiveOtherNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text(
                                        text = "OTHERS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.1.sp,
                                            fontSize = 11.sp
                                        ),
                                        color = contentTextColor.copy(alpha = 0.55f),
                                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }

                        // UNPINNED / REGULAR / ARCHIVED / TRASHED NOTES
                        val displayList = if (currentFolder == NoteFolder.NOTES) {
                            effectiveOtherNotes
                        } else {
                            allNotes
                        }
                        items(displayList, key = { it.id }) { note ->
                            val isSelected = selectedNoteIds.contains(note.id)
                            val isDragging = draggedNoteId == note.id
                            KeepNoteCard(
                                note = note,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                isDragging = isDragging,
                                dragOffset = if (isDragging) dragOffset else Offset.Zero,
                                onStartDrag = { pos -> handleDragStart(note.id, pos) },
                                onDrag = { delta -> handleDrag(note, delta) },
                                onEndDrag = { handleDragEnd(note) },
                                onToggleSelect = { viewModel.toggleNoteSelection(note.id) },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleNoteSelection(note.id)
                                    } else if (currentFolder != NoteFolder.TRASH) {
                                        viewModel.openExistingNote(note)
                                    }
                                },
                                onLongClick = null,
                                onTogglePin = if (currentFolder == NoteFolder.NOTES) { { viewModel.togglePin(note) } } else null,
                                onRestore = if (currentFolder == NoteFolder.TRASH) { { viewModel.restoreNote(note) } } else null,
                                onDeletePermanently = if (currentFolder == NoteFolder.TRASH) { { viewModel.deletePermanently(note) } } else null,
                                onUnarchive = if (currentFolder == NoteFolder.ARCHIVE) { { viewModel.unarchiveNote(note) } } else null,
                                activePlayingAudioPath = playbackState.currentPath,
                                isAudioPlaying = playbackState.isPlaying,
                                onToggleAudioPlay = { uri -> viewModel.toggleAudioPlayback(uri) },
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { coords ->
                                        if (draggedNoteId != note.id) {
                                            cardBounds[note.id] = coords.boundsInRoot()
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // =========================================================
            // SPEED DIAL FLOATING ACTION BUTTON (Notes)
            // =========================================================
            if (currentFolder == NoteFolder.NOTES && !isSelectionMode) {
                NotesSpeedDialFab(
                    onNewTextNote = { viewModel.openNewNote(asChecklist = false) },
                    onNewChecklist = { viewModel.openNewNote(asChecklist = true) },
                    onNewDrawing = { showSketchDialogFromDock = true },
                    onNewAudio = { checkAndStartRecording() },
                    onNewImage = {
                        dockPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // =========================================================
            // FULL SCREEN NOTE EDITOR OVERLAY (Google Keep Container Transform Style)
            // =========================================================
            AnimatedVisibility(
                visible = editingState != null,
                enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ) +
                        slideInVertically(
                            initialOffsetY = { fullHeight -> (fullHeight * 0.08f).toInt() },
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ),
                exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 0.94f,
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) +
                        slideOutVertically(
                            targetOffsetY = { fullHeight -> (fullHeight * 0.06f).toInt() },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        )
            ) {
                editingState?.let { state ->
                    KeepNoteEditor(
                        state = state,
                        allLabels = allLabels,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onTitleChange = { viewModel.updateEditorTitle(it) },
                        onContentChange = { viewModel.updateEditorContent(it) },
                        onTogglePin = { viewModel.toggleEditorPin() },
                        onColorChange = { viewModel.updateEditorColor(it) },
                        onFontChange = { viewModel.updateEditorFont(it) },
                        onToggleChecklistMode = { viewModel.toggleChecklistMode() },
                        onToggleChecklistItem = { viewModel.toggleChecklistItem(it) },
                        onUpdateChecklistItemText = { idx, text -> viewModel.updateChecklistItemText(idx, text) },
                        onAddChecklistItem = { afterIdx, text, customId -> viewModel.addChecklistItem(afterIdx, text, customId) },
                        onRemoveChecklistItem = { viewModel.removeChecklistItem(it) },
                        onMoveChecklistItem = { from, to -> viewModel.moveChecklistItem(from, to) },
                        onAddImageUri = { viewModel.addImageUriToEditor(it) },
                        onAddDrawing = { viewModel.addDrawingToEditor(it) },
                        onRemoveImage = { viewModel.removeImageFromEditor(it) },
                        onAddLabel = { viewModel.addLabelToEditor(it) },
                        onRemoveLabel = { viewModel.removeLabelFromEditor(it) },
                        onToggleLabel = { label ->
                            if (state.labels.contains(label)) {
                                viewModel.removeLabelFromEditor(label)
                            } else {
                                viewModel.addLabelToEditor(label)
                            }
                        },
                        onCreateAndAddLabel = { label ->
                            viewModel.addCustomLabel(label)
                            viewModel.addLabelToEditor(label)
                        },
                        onArchive = { viewModel.archiveCurrentNote() },
                        onDelete = { viewModel.deleteCurrentNote() },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onDuplicate = { viewModel.duplicateCurrentNote() },
                        onShare = { viewModel.shareCurrentNote(context) },
                        onCopyText = { viewModel.copyCurrentNoteToClipboard(context) },
                        onVoiceInput = { viewModel.appendVoiceTranscription(it) },
                        onPhotoTaken = { viewModel.addCapturedPhoto(it) },
                        onStartVoiceRecording = { checkAndStartRecording() },
                        activePlayingAudioPath = playbackState.currentPath,
                        isAudioPlaying = playbackState.isPlaying,
                        audioPositionMs = playbackState.currentPositionMs,
                        audioDurationMs = playbackState.durationMs,
                        audioPlaybackSpeed = playbackState.speed,
                        onSetAudioPlaybackSpeed = { speed -> viewModel.setAudioPlaybackSpeed(speed) },
                        onSkipAudio = { delta -> viewModel.skipAudioPlayback(delta) },
                        onToggleAudioPlay = { uri -> viewModel.toggleAudioPlayback(uri) },
                        onSeekAudio = { pos -> viewModel.seekAudio(pos) },
                        onRemoveAudio = { uri -> viewModel.removeAudioFromEditor(uri) },
                        onClose = { viewModel.closeEditor() }
                    )
                }
            }

            // =========================================================
            // EDIT LABELS GLOBAL DIALOG
            // =========================================================
            if (showEditLabelsDialog) {
                EditLabelsDialog(
                    allLabels = allLabels,
                    onAddLabel = { viewModel.addCustomLabel(it) },
                    onRenameLabel = { old, new -> viewModel.renameCustomLabel(old, new) },
                    onDeleteLabel = { viewModel.deleteCustomLabel(it) },
                    onDismiss = { showEditLabelsDialog = false }
                )
            }

            // =========================================================
            // DOCK TRIGGERED DRAWING DIALOG
            // =========================================================
            if (showSketchDialogFromDock) {
                KeepSketchDialog(
                    onDismiss = { showSketchDialogFromDock = false },
                    onSaveDrawing = { bitmap ->
                        viewModel.openNewNoteWithDrawing(bitmap)
                        showSketchDialogFromDock = false
                    }
                )
            }

            // =========================================================
            // SELECTION MODE COLOR DIALOG
            // =========================================================
            if (showSelectionColorPicker) {
                KeepColorDialog(
                    selectedColorKey = null,
                    onColorSelected = { colorKey ->
                        viewModel.setSelectedNotesColor(colorKey)
                    },
                    onDismiss = { showSelectionColorPicker = false }
                )
            }

            // =========================================================
            // SELECTION MODE LABELS DIALOG
            // =========================================================
            if (showSelectionLabelsDialog) {
                val commonLabels = remember(selectedNotes) {
                    if (selectedNotes.isEmpty()) emptyList()
                    else {
                        val labelSets = selectedNotes.map { it.getLabels().toSet() }
                        labelSets.reduce { acc, set -> acc.intersect(set) }.toList()
                    }
                }
                NoteLabelsDialog(
                    allLabels = allLabels,
                    selectedLabels = commonLabels,
                    onToggleLabel = { label ->
                        viewModel.toggleLabelForSelectedNotes(label)
                    },
                    onCreateAndAddLabel = { newLabel ->
                        viewModel.addCustomLabel(newLabel)
                        viewModel.toggleLabelForSelectedNotes(newLabel)
                    },
                    onDismiss = { showSelectionLabelsDialog = false }
                )
            }

            // =========================================================
            // OFFLINE VOICE RECORDING DIALOG (Option 2 Hybrid Audio Memo)
            // =========================================================
            if (recordingState.isRecording) {
                VoiceRecordDialog(
                    elapsedSeconds = recordingState.elapsedSeconds,
                    amplitude = recordingState.currentAmplitude,
                    liveTranscript = recordingState.liveTranscript,
                    statusMessage = recordingState.statusMessage,
                    onCancel = { viewModel.cancelVoiceRecording() },
                    onDone = { viewModel.stopVoiceRecordingAndAttach() }
                )
            }

            // =========================================================
            // EMPTY TRASH CONFIRMATION DIALOG
            // =========================================================
            if (showEmptyTrashDialog) {
                AlertDialog(
                    onDismissRequest = { showEmptyTrashDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text("Empty trash?")
                    },
                    text = {
                        Text("All notes in Trash will be permanently deleted. This action cannot be undone.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showEmptyTrashDialog = false
                                viewModel.emptyTrash()
                            }
                        ) {
                            Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEmptyTrashDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // =========================================================
            // SECRET ARCHIVE VAULT DIALOGS
            // =========================================================
            if (showVaultUnlockDialog) {
                ArchiveVaultUnlockDialog(
                    onDismiss = { showVaultUnlockDialog = false },
                    onVerify = { pin -> viewModel.verifyVaultPasscode(pin) },
                    onSuccess = {
                        showVaultUnlockDialog = false
                        Toast.makeText(context, "Archive Vault unlocked 🔓", Toast.LENGTH_SHORT).show()
                    },
                    initialLockoutSeconds = viewModel.getRemainingLockoutSeconds()
                )
            }

            if (showVaultFirstTimeDialog) {
                ArchiveVaultFirstTimeDialog(
                    onDismiss = { showVaultFirstTimeDialog = false },
                    onSkip = {
                        showVaultFirstTimeDialog = false
                        viewModel.skipVaultPasscode()
                        Toast.makeText(context, "Passcode skipped. You can enable it anytime in Vault Options.", Toast.LENGTH_SHORT).show()
                    },
                    onPasscodeSet = { pin ->
                        showVaultFirstTimeDialog = false
                        viewModel.setVaultPasscode(pin)
                        Toast.makeText(context, "Secret Vault passcode set! 🔒", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showVaultSettingsDialog) {
                ArchiveVaultSettingsDialog(
                    vaultStatus = vaultStatus,
                    onDismiss = { showVaultSettingsDialog = false },
                    onLockVault = {
                        showVaultSettingsDialog = false
                        viewModel.lockVault()
                        Toast.makeText(context, "Archive Vault locked 🔒", Toast.LENGTH_SHORT).show()
                    },
                    onChangePasscode = {
                        showVaultSettingsDialog = false
                        showVaultChangePinDialog = true
                    },
                    onDisablePasscode = {
                        showVaultSettingsDialog = false
                        showVaultDisableConfirmDialog = true
                    },
                    onSetPasscode = {
                        showVaultSettingsDialog = false
                        showVaultFirstTimeDialog = true
                    }
                )
            }

            if (showVaultChangePinDialog) {
                ArchiveVaultChangePasscodeDialog(
                    onDismiss = { showVaultChangePinDialog = false },
                    onVerifyCurrentPin = { pin -> viewModel.verifyVaultPasscode(pin) },
                    onSaveNewPin = { newPin -> viewModel.setVaultPasscode(newPin) },
                    onSuccess = {
                        showVaultChangePinDialog = false
                        Toast.makeText(context, "Vault passcode updated successfully! 🔒", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showVaultDisableConfirmDialog) {
                ArchiveVaultDisableConfirmDialog(
                    onDismiss = { showVaultDisableConfirmDialog = false },
                    onVerifyCurrentPin = { pin -> viewModel.verifyVaultPasscode(pin) },
                    onDisableConfirmed = {
                        showVaultDisableConfirmDialog = false
                        viewModel.disableVaultPasscode()
                        Toast.makeText(context, "Passcode protection turned off 🔓", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
