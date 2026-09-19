package com.focusbyrj.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.TaskType
import com.focusbyrj.app.util.SmartDateParser
import com.focusbyrj.app.ui.viewmodels.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodosScreen(
    viewModel: TaskViewModel, 
    initialOpenAdd: Boolean = false,
    onOpenAddHandled: (() -> Unit)? = null
) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Today", "Upcoming", "All", "Occasions")
    
    var showAddDialog by remember { mutableStateOf(initialOpenAdd) }
    LaunchedEffect(initialOpenAdd) {
        if (initialOpenAdd) {
            showAddDialog = true
            onOpenAddHandled?.invoke()
        }
    }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    var pendingDeleteTask by remember { mutableStateOf<Task?>(null) }
    var deleteCountdown by remember { mutableStateOf(4) }
    
    LaunchedEffect(pendingDeleteTask) {
        if (pendingDeleteTask != null) {
            deleteCountdown = 4
            while (deleteCountdown > 0) {
                delay(1000L)
                deleteCountdown -= 1
            }
            pendingDeleteTask?.let { viewModel.deleteTask(it) }
            pendingDeleteTask = null
        }
    }

    // Filter tasks
    val filteredTasks = remember(tasks, selectedTab, pendingDeleteTask) {
        val now = Calendar.getInstance()
        val todayStart = now.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86400000L

        val baseList = when (selectedTab) {
            0 -> tasks.filter { 
                (it.dueDate != null && it.dueDate <= todayEnd) || 
                (it.dueDate == null && it.type == TaskType.TASK) 
            } // Today & Overdue
            1 -> tasks.filter { 
                it.type == TaskType.TASK && it.dueDate != null && it.dueDate > todayEnd 
            } // Upcoming
            2 -> tasks.filter { it.type == TaskType.TASK } // All
            3 -> tasks.filter { it.type != TaskType.TASK } // Occasions
            else -> emptyList()
        }
        baseList
            .filter { !it.isCompleted && it.id != pendingDeleteTask?.id }
            .sortedWith(
                compareByDescending<Task> { it.isPriority }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
                    .thenBy { it.id }
            )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Task")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                TodoSegmentedPill(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                if (filteredTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 3) Icons.Outlined.Cake else Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (selectedTab == 3) "No upcoming occasions" else "No pending tasks",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap + to create a new reminder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                        ) {
                            itemsIndexed(
                                filteredTasks, 
                                key = { _, it -> it.id },
                                contentType = { _, _ -> "TaskItem" }
                            ) { index, task ->
                                val isFirst = index == 0
                                val isLast = index == filteredTasks.lastIndex
                                
                                val cardShape = when {
                                    isFirst && isLast -> RoundedCornerShape(16.dp)
                                    isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    isLast -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                    else -> RoundedCornerShape(4.dp)
                                }

                                TaskItem(
                                    task = task,
                                    modifier = Modifier.animateItem(),
                                    shape = cardShape,
                                    onToggle = {
                                        coroutineScope.launch {
                                            delay(350)
                                            viewModel.toggleTaskCompletion(it)
                                        }
                                    },
                                    onDelete = {
                                        pendingDeleteTask?.let { deleted -> viewModel.deleteTask(deleted) }
                                        pendingDeleteTask = it
                                        deleteCountdown = 4
                                    },
                                    onEdit = { editingTask = it }
                                )
                            }
                        }
                    }
                }
            }
            
            // Undo Snackbar
            AnimatedVisibility(
                visible = pendingDeleteTask != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { deleteCountdown / 4f },
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                            Text(
                                text = deleteCountdown.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(14.dp))
                        
                        Text(
                            text = "Task removed",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = { pendingDeleteTask = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "UNDO",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingTask != null) {
        AddTaskDialog(
            initialTask = editingTask,
            defaultType = if (selectedTab == 3) TaskType.BIRTHDAY else TaskType.TASK,
            onDismiss = { 
                showAddDialog = false
                editingTask = null
            },
            onSave = { task ->
                if (editingTask != null) {
                    viewModel.updateTask(task.copy(id = editingTask!!.id))
                } else {
                    viewModel.addTask(task)
                }
                showAddDialog = false
                editingTask = null
            },
            onDelete = if (editingTask != null) {
                { taskToDelete ->
                    pendingDeleteTask?.let { deleted -> viewModel.deleteTask(deleted) }
                    pendingDeleteTask = taskToDelete
                    deleteCountdown = 4
                    showAddDialog = false
                    editingTask = null
                }
            } else null
        )
    }
}

@Composable
fun TodoSegmentedPill(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "tabContent"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskItem(
    task: Task, 
    modifier: Modifier = Modifier, 
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    onToggle: (Task) -> Unit, 
    onDelete: (Task) -> Unit, 
    onEdit: (Task) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isLocalCompleted by remember(task.isCompleted) { mutableStateOf(task.isCompleted) }
    val isCompleted = isLocalCompleted

    val textColor by animateColorAsState(
        targetValue = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "textColor"
    )

    val explicitPurple = Color(0xFFB388FF)
    val explicitOrange = Color(0xFFFF7043)
    val metadataColor = remember(task.dueDate, isCompleted, textColor) {
        if (isCompleted) {
            textColor.copy(alpha = 0.5f)
        } else if (task.dueDate != null) {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            
            cal.timeInMillis = now
            val currentDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
            val currentYear = cal.get(java.util.Calendar.YEAR)
            
            cal.timeInMillis = task.dueDate
            val dueDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
            val dueYear = cal.get(java.util.Calendar.YEAR)
            
            if (task.dueDate < now) {
                explicitOrange // Overdue -> Orange
            } else if (currentDay == dueDay && currentYear == dueYear) {
                explicitPurple // Today -> Explicitly Purple
            } else {
                textColor.copy(alpha = 0.8f) // Future -> Neutral
            }
        } else {
            textColor.copy(alpha = 0.8f) // No due date -> Neutral
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                onDelete(task)
                true
            } else false
        }
    )
    
    LaunchedEffect(task.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .clip(shape),
        backgroundContent = {
            // Background is completely invisible unless user is actually dragging
            val isSwiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled || dismissState.progress > 0.05f
            if (isSwiping) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    ) {
        // Clean card surface without any tonal color tint
        val cardColor = if (isCompleted) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surface
        }

        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(task) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Checkbox
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = if (isCompleted) MaterialTheme.colorScheme.primary 
                                    else if (task.isPriority) Color(0xFFFF7043)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isLocalCompleted = !isLocalCompleted
                            onToggle(task)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                // Content Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (task.details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.details,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = textColor.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    if (task.dueDate != null || task.recurrence != RecurrencePattern.NONE || task.isPersistent) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (task.dueDate != null) {
                                val isOverdue = !isCompleted && task.dueDate < System.currentTimeMillis()
                                val dateColor = if (isOverdue) Color(0xFFE53935) else metadataColor
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOverdue) Icons.Outlined.Warning else Icons.Outlined.Event,
                                        contentDescription = null,
                                        tint = dateColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isOverdue) "Overdue • ${SmartDateParser.formatDueDate(task.dueDate)}" else SmartDateParser.formatDueDate(task.dueDate),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 13.sp, 
                                            fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = dateColor
                                    )
                                }
                            }
                            
                            if (task.recurrence != RecurrencePattern.NONE) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Repeat,
                                        contentDescription = null,
                                        tint = metadataColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = task.recurrence.name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                                        color = metadataColor
                                    )
                                }
                            }

                            if (task.isPersistent) {
                                val showPersistText = task.dueDate == null || task.recurrence == RecurrencePattern.NONE
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = "Persistent reminder",
                                        tint = metadataColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    if (showPersistText) {
                                        Text(
                                            text = "Persist",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                                            color = metadataColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Occasion Icon if Birthday / Anniversary
                if (task.type != TaskType.TASK) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (task.type == TaskType.BIRTHDAY) Icons.Outlined.Cake else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    initialTask: Task? = null, 
    defaultType: TaskType = TaskType.TASK,
    onDismiss: () -> Unit, 
    onSave: (Task) -> Unit,
    onDelete: ((Task) -> Unit)? = null
) {
    val initialEffectiveType = initialTask?.type ?: defaultType
    var title by remember(initialTask) { mutableStateOf(initialTask?.title ?: "") }
    var details by remember(initialTask) { mutableStateOf(initialTask?.details ?: "") }
    var type by remember(initialTask, defaultType) { mutableStateOf(initialEffectiveType) }
    var userManuallySelectedType by remember(initialTask) { mutableStateOf(initialTask != null) }
    var recurrence by remember(initialTask, defaultType) { 
        mutableStateOf(
            initialTask?.recurrence ?: if (initialEffectiveType == TaskType.BIRTHDAY || initialEffectiveType == TaskType.ANNIVERSARY) RecurrencePattern.YEARLY else RecurrencePattern.NONE
        ) 
    }
    var userManuallySetRecurrence by remember(initialTask) { 
        mutableStateOf(initialTask?.recurrence != null && initialTask.recurrence != RecurrencePattern.NONE) 
    }
    var isPersistent by remember(initialTask) { mutableStateOf(initialTask?.isPersistent ?: false) }
    var isPriority by remember(initialTask) { mutableStateOf(initialTask?.isPriority ?: false) }
    var manualDueDate by remember(initialTask) { mutableStateOf<Long?>(initialTask?.dueDate) }
    var userManuallySetDate by remember(initialTask) { mutableStateOf(initialTask?.dueDate != null) }
    
    val parsedResult = remember(title, userManuallySetDate) {
        if (!userManuallySetDate && title.isNotBlank()) SmartDateParser.parse(title) else null
    }

    LaunchedEffect(title) {
        if (!userManuallySelectedType && initialTask == null && title.isNotBlank()) {
            val lower = title.lowercase()
            if (lower.contains("birthday") || lower.contains("bday")) {
                type = TaskType.BIRTHDAY
                if (!userManuallySetRecurrence) {
                    recurrence = RecurrencePattern.YEARLY
                }
            } else if (lower.contains("anniversary")) {
                type = TaskType.ANNIVERSARY
                if (!userManuallySetRecurrence) {
                    recurrence = RecurrencePattern.YEARLY
                }
            }
        }
    }

    LaunchedEffect(parsedResult?.note) {
        if (parsedResult?.note != null && details.isBlank() && initialTask == null) {
            details = parsedResult.note
        }
    }
    
    val effectiveDueDate = parsedResult?.timestamp ?: manualDueDate
    val effectiveRecurrence = if (!userManuallySetRecurrence && parsedResult?.recurrence != null && parsedResult.recurrence != RecurrencePattern.NONE) {
        parsedResult.recurrence
    } else if (!userManuallySetRecurrence && (type == TaskType.BIRTHDAY || type == TaskType.ANNIVERSARY)) {
        RecurrencePattern.YEARLY
    } else {
        recurrence
    }
    
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    if (effectiveDueDate != null) {
        calendar.timeInMillis = effectiveDueDate
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTask == null) "New Task" else "Edit Task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = {
                        val finalTitle = parsedResult?.cleanText?.takeIf { it.isNotBlank() } ?: title
                        if (finalTitle.isNotBlank()) {
                            val finalDetails = if (details.isBlank() && parsedResult?.note != null) {
                                parsedResult.note
                            } else if (parsedResult?.note != null && !details.contains("29")) {
                                "$details\n${parsedResult.note}"
                            } else if (effectiveDueDate != null && (type == TaskType.BIRTHDAY || type == TaskType.ANNIVERSARY) && (title.contains("29") || details.contains("29"))) {
                                val checkCal = Calendar.getInstance().apply { timeInMillis = effectiveDueDate }
                                if (checkCal.get(Calendar.MONTH) == Calendar.FEBRUARY && checkCal.get(Calendar.DAY_OF_MONTH) == 28 && !SmartDateParser.isLeapYear(checkCal.get(Calendar.YEAR)) && !details.contains("reminded on Feb 28th")) {
                                    if (details.isBlank()) "Note: Event is on Feb 29th (reminded on Feb 28th in non-leap years)" else "$details\nNote: Event is on Feb 29th (reminded on Feb 28th in non-leap years)"
                                } else {
                                    details
                                }
                            } else {
                                details
                            }

                            val base = initialTask ?: Task(title = finalTitle)
                            onSave(
                                base.copy(
                                    title = finalTitle, 
                                    details = finalDetails, 
                                    dueDate = effectiveDueDate, 
                                    type = type, 
                                    recurrence = effectiveRecurrence, 
                                    isPersistent = isPersistent,
                                    isPriority = isPriority
                                )
                            )
                        }
                    },
                    enabled = title.isNotBlank()
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            AnimatedVisibility(visible = parsedResult?.timestamp != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = "Detected time",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Setting due: ${parsedResult?.timestamp?.let { SmartDateParser.formatDueDate(it) }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Details (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Category", 
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = type == TaskType.TASK,
                    onClick = { 
                        type = TaskType.TASK
                        userManuallySelectedType = true
                        if (!userManuallySetRecurrence) {
                            recurrence = RecurrencePattern.NONE
                        }
                    },
                    label = { Text("Task") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = type == TaskType.BIRTHDAY,
                    onClick = { 
                        type = TaskType.BIRTHDAY
                        userManuallySelectedType = true
                        if (!userManuallySetRecurrence) {
                            recurrence = RecurrencePattern.YEARLY
                        }
                    },
                    label = { Text("Birthday") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = type == TaskType.ANNIVERSARY,
                    onClick = { 
                        type = TaskType.ANNIVERSARY
                        userManuallySelectedType = true
                        if (!userManuallySetRecurrence) {
                            recurrence = RecurrencePattern.YEARLY
                        }
                    },
                    label = { Text("Anniversary") },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = isPriority,
                    onClick = { isPriority = !isPriority },
                    label = { Text("Priority", color = if (isPriority) Color.White else Color(0xFFFF9800)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9800)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Due Date & Time", 
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (effectiveDueDate != null) {
                        IconButton(
                            onClick = {
                                manualDueDate = null
                                userManuallySetDate = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear Due Date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    calendar.set(year, month, dayOfMonth)
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            calendar.set(Calendar.MINUTE, minute)
                                            calendar.set(Calendar.SECOND, 0)
                                            manualDueDate = calendar.timeInMillis
                                            userManuallySetDate = true
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) {
                        Text(
                            text = if (effectiveDueDate == null) "Set Time" else SmartDateParser.formatDueDate(effectiveDueDate),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Recurrence", 
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(
                            text = effectiveRecurrence.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        RecurrencePattern.entries.forEach { pattern ->
                            DropdownMenuItem(
                                text = { Text(pattern.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = { 
                                    recurrence = pattern
                                    userManuallySetRecurrence = true
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Persistent Reminder", 
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Alerts periodically until completed", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPersistent, 
                    onCheckedChange = { isPersistent = it }
                )
            }
            
            if (initialTask != null && onDelete != null) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { onDelete(initialTask) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Task",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (initialTask.recurrence != RecurrencePattern.NONE) "Delete Recurring Task" else "Delete Task",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            

        }
    }
}
