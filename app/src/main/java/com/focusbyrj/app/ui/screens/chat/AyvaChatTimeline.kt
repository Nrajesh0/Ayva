package com.focusbyrj.app.ui.screens.chat

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.components.CatMagicRiveView
import com.focusbyrj.app.ui.components.MysteryBoxChatCard
import com.focusbyrj.app.ui.components.TalkActionChips
import com.focusbyrj.app.ui.components.VocabBriefContent
import com.focusbyrj.app.ui.components.VocabRetentionHubChatCard
import com.focusbyrj.app.ui.screens.*
import com.focusbyrj.app.util.AyvaAlertCategory
import com.focusbyrj.app.util.AyvaDialogueEngine
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.SmartDateParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class TaskItemData(
    val id: Long,
    val title: String,
    val isPriority: Boolean,
    val dueDate: Long,
    val isCompleted: Boolean,
    val isPersistent: Boolean = false,
    val filterMode: String = "today"
)

fun parseRichFormattedText(rawText: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val n = rawText.length

    while (i < n) {
        if (i + 1 < n && rawText[i] == '*' && rawText[i + 1] == '*') {
            val closeIdx = rawText.indexOf("**", i + 2)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 2, closeIdx)
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
                i = closeIdx + 2
                continue
            }
        } else if (i + 2 < n && rawText[i] == '*' && rawText[i + 1] == '_' && rawText[i + 2] == '_') {
            val closeIdx = rawText.indexOf("__*", i + 3)
            if (closeIdx != -1) {
                val content = rawText.substring(i + 3, closeIdx)
                builder.withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(content)
                }
                i = closeIdx + 3
                continue
            }
        } else if (i + 1 < n && rawText[i] == '_' && rawText[i + 1] == '_') {
            val closeIdx = rawText.indexOf("__", i + 2)
            if (closeIdx != -1) {
                val content = rawText.substring(i + 2, closeIdx)
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(content)
                }
                i = closeIdx + 2
                continue
            }
        } else if (rawText[i] == '*') {
            val closeIdx = rawText.indexOf('*', i + 1)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 1, closeIdx)
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
                i = closeIdx + 1
                continue
            }
        } else if (rawText[i] == '_') {
            val closeIdx = rawText.indexOf('_', i + 1)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 1, closeIdx)
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
                i = closeIdx + 1
                continue
            }
        } else if (rawText[i] == '`') {
            val closeIdx = rawText.indexOf('`', i + 1)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 1, closeIdx)
                builder.withStyle(
                    SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(content)
                }
                i = closeIdx + 1
                continue
            }
        }

        builder.append(rawText[i])
        i++
    }

    return builder.toAnnotatedString()
}

@Composable
fun DuolingoFreezeButton(
    canAfford: Boolean,
    enabled: Boolean,
    fontSizeSp: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = isSystemInDarkTheme()

    val topOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 60),
        label = "duo_btn_press"
    )

    val baseColor = when {
        !enabled -> if (isDark) Color(0xFF1E293B) else Color(0xFF94A3B8)
        canAfford -> Color(0xFF0284C7)
        else -> if (isDark) Color(0xFF1E293B) else Color(0xFF94A3B8)
    }

    val topFaceColor = when {
        !enabled -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
        canAfford -> Color(0xFF0EA5E9)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    }

    val textColor = when {
        !enabled -> Color.Gray
        canAfford -> Color.White
        else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(16.dp))
                .background(baseColor)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topOffset)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(topFaceColor)
                .border(
                    width = 1.dp,
                    color = if (canAfford && enabled) Color(0xFF7DD3FC).copy(alpha = 0.6f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "🧊",
                    fontSize = 17.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = if (canAfford) "FREEZE STREAK • 1,000 🪙" else "FREEZE STREAK (1,000 🪙)",
                    fontWeight = FontWeight.Black,
                    fontSize = (fontSizeSp * 0.85f).coerceIn(12f, 15f).sp,
                    letterSpacing = 0.5.sp,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun PendingActionCard(message: ChatMessage, fontSizeSp: Float, onMessageUpdate: (ChatMessage) -> Unit) {
    val context = LocalContext.current
    val json = remember(message.pendingActionJson) { JSONObject(message.pendingActionJson ?: "{}") }
    val status = json.optString("status", "pending")

    Spacer(modifier = Modifier.height(8.dp))

    if (status == "pending") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    val prefKey = json.optString("prefKey")
                    val prefType = json.optString("prefType")
                    val value = json.optString("value")

                    val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).edit()
                    val bubblePrefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE).edit()

                    if (prefType == "int") {
                        prefs.putInt(prefKey, value.toIntOrNull() ?: 0)
                        bubblePrefs.putInt(prefKey, value.toIntOrNull() ?: 0)
                    } else if (prefType == "boolean") {
                        prefs.putBoolean(prefKey, value == "true")
                        bubblePrefs.putBoolean(prefKey, value == "true")
                    } else if (prefType == "string") {
                        prefs.putString(prefKey, value)
                        bubblePrefs.putString(prefKey, value)
                    }
                    prefs.apply()
                    bubblePrefs.apply()

                    if (prefKey == "streak_notification_time" || prefKey == "streak_notification_enabled") {
                        com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleDrillReminders(context)
                    } else if (prefKey == "morning_brief_time" || prefKey == "evening_brief_time") {
                        com.focusbyrj.app.service.DailySummaryReceiver.scheduleDailySummaries(context)
                    } else if (prefKey == "vacation_mode") {
                        com.focusbyrj.app.util.AptitudeManager.setVacationMode(context, value == "true")
                    }

                    json.put("status", "executed")
                    val updatedMessage = message.copy(pendingActionJson = json.toString())
                    onMessageUpdate(updatedMessage)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Confirm", fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    json.put("status", "cancelled")
                    val updatedMessage = message.copy(pendingActionJson = json.toString())
                    onMessageUpdate(updatedMessage)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Cancel", fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (status == "executed") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Changed successfully.", color = Color(0xFF10B981), fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancelled", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Action cancelled.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TaskSummaryCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    onTaskToggle: ((Long) -> Unit)? = null,
    onRescheduleClick: (() -> Unit)? = null,
    onFilterChange: ((String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (300 + (fontSizeSp - 15f) * 12f).coerceIn(300f, 380f).dp

    var showFilterDropdown by remember { mutableStateOf(false) }

    val taskItems = remember(message.taskSummaryJson) {
        val list = mutableListOf<TaskItemData>()
        if (!message.taskSummaryJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(message.taskSummaryJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        TaskItemData(
                            id = obj.optLong("id"),
                            title = obj.optString("title", ""),
                            isPriority = obj.optBoolean("isPriority", false),
                            dueDate = obj.optLong("dueDate", 0L),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            isPersistent = obj.optBoolean("isPersistent", false),
                            filterMode = obj.optString("filterMode", "today")
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        list
    }

    val isAllMode = taskItems.firstOrNull()?.filterMode == "all"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 20.dp
                ),
                color = if (isDark) Color(0xFF131316) else Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.1f else 0.25f)
                ),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Bar with Filter Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAllMode) "All Tasks" else "Today's Tasks",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSizeSp * 0.95f).sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (taskItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    val leftCount = taskItems.count { !it.isCompleted }
                                    Text(
                                        text = "$leftCount left",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Dropdown Selector Button
                        Box {
                            Surface(
                                onClick = { showFilterDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF1C1C20) else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isAllMode) "All" else "Today",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Select task view",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showFilterDropdown,
                                onDismissRequest = { showFilterDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Today's Tasks") },
                                    onClick = {
                                        showFilterDropdown = false
                                        onFilterChange?.invoke("/tasks")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("All Tasks") },
                                    onClick = {
                                        showFilterDropdown = false
                                        onFilterChange?.invoke("/tasks all")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (taskItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAllMode) "No tasks found" else "No tasks scheduled for today",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (fontSizeSp * 0.95f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            taskItems.forEach { task ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) Color(0xFF131B26) else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (task.isPriority && !task.isCompleted) Color(0xFFEF4444).copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.1f else 0.18f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onTaskToggle?.invoke(task.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = when {
                                                        task.isCompleted -> MaterialTheme.colorScheme.primary
                                                        task.isPriority -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                                    },
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (task.isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Completed",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = (fontSizeSp * 0.95f).sp,
                                                        fontWeight = if (task.isPriority && !task.isCompleted) FontWeight.Bold else FontWeight.SemiBold,
                                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                                    ),
                                                    color = if (task.isCompleted) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    } else if (task.isPriority) {
                                                        Color(0xFFEF4444)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }

                                            if (task.dueDate > 0L) {
                                                val now = System.currentTimeMillis()
                                                val isOverdue = task.dueDate < now && !task.isCompleted
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.AccessTime,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(11.dp),
                                                        tint = if (isOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isOverdue) "Overdue: ${SmartDateParser.formatDueDate(task.dueDate)}" else SmartDateParser.formatDueDate(task.dueDate),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { onRescheduleClick?.invoke() },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.1f else 0.08f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.3f else 0.25f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = "Reschedule",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reschedule",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (fontSizeSp * 0.8f).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            onClick = { onFilterChange?.invoke(if (isAllMode) "/tasks" else "/tasks all") },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF1C1C20) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.2f else 0.2f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isAllMode) Icons.Filled.Today else Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAllMode) "Show Today" else "Show All",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (fontSizeSp * 0.8f).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 40.dp)
        )
    }
}

@Composable
fun StreakPromptCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    isActiveDrillRunning: Boolean = false,
    onStartDrill: () -> Unit,
    onSkipWithFreeze: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val economyProfile by FocusEconomyManager.profileFlow.collectAsState()
    val canAffordFreeze = economyProfile.gold >= 1000
    val json = remember(message.streakPromptJson) {
        try {
            if (message.streakPromptJson != null) JSONObject(message.streakPromptJson) else null
        } catch (_: Exception) {
            null
        }
    }

    val streak = json?.optInt("streak", 0) ?: 0
    val bonusPercent = json?.optInt("bonusPercent", 0) ?: 0

    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (290 + (fontSizeSp - 15f) * 10f).coerceIn(290f, 360f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 20.dp
                ),
                color = if (isDark) Color(0xFF1E140A) else Color(0xFFFFF7ED),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF97316).copy(alpha = 0.7f)),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF97316).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (streak > 0) "🔥 $streak-DAY STREAK" else "⚡ DAILY STREAK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEA580C)
                                )
                            }
                        }

                        if (bonusPercent > 0) {
                            Text(
                                text = "+$bonusPercent% XP Bonus",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = parseRichFormattedText(message.text),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onStartDrill,
                        enabled = !isActiveDrillRunning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEA580C),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFEA580C).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isActiveDrillRunning) Icons.Filled.CheckCircle else Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isActiveDrillRunning) "Drill in Progress" else "Start 10-Question Drill",
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSizeSp * 0.95f).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    DuolingoFreezeButton(
                        canAfford = canAffordFreeze,
                        enabled = !isActiveDrillRunning,
                        fontSizeSp = fontSizeSp,
                        onClick = onSkipWithFreeze
                    )
                }
            }
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 40.dp)
        )
    }
}

data class TaskAddedDetails(
    val title: String,
    val dueText: String? = null
)

fun parseTaskAddedDetails(rawText: String): TaskAddedDetails {
    val lines = rawText.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    var title = ""
    var dueText: String? = null

    val contentLines = lines.dropWhile { it.contains("radar", ignoreCase = true) }

    if (contentLines.isNotEmpty()) {
        val firstLine = contentLines[0]
            .removePrefix("•")
            .removePrefix("-")
            .removePrefix("*")
            .trim()
            .replace("**", "")

        if (firstLine.contains("(Due:", ignoreCase = true)) {
            val parts = firstLine.split(Regex("(?i)\\(Due:"), limit = 2)
            title = parts[0].trim()
            val rawDue = parts.getOrNull(1)?.removeSuffix(")")?.trim()
            if (!rawDue.isNullOrBlank()) {
                dueText = "Due: $rawDue"
            }
        } else {
            title = firstLine
        }

        if (contentLines.size > 1 && dueText == null) {
            val secondLine = contentLines[1]
                .removePrefix("•")
                .removePrefix("-")
                .removePrefix("*")
                .trim()
                .replace("**", "")

            if (secondLine.isNotBlank()) {
                dueText = secondLine.replace("(", "• ").replace(")", "").replace("  ", " ").trim()
            }
        }
    }

    if (title.isBlank()) {
        title = rawText
            .replace("✅", "")
            .replace(Regex("(?i)\\*\\*Added to your radar:\\*\\*"), "")
            .replace(Regex("(?i)Added to your radar:"), "")
            .replace("•", "")
            .replace("**", "")
            .trim()
    }

    return TaskAddedDetails(title = title, dueText = dueText)
}

fun parseConflictPrompt(rawText: String): String {
    return rawText
        .replace("🤔", "")
        .replace(Regex("(?i)^\\s*\\*\\*Conflict Detected:\\*\\*\\s*"), "")
        .replace(Regex("(?i)^\\s*Conflict Detected:\\s*"), "")
        .trim()
}

@Composable
fun TaskAddedCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    onQueryClick: ((String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (300 + (fontSizeSp - 15f) * 10f).coerceIn(300f, 380f).dp

    val taskInfo = remember(message.text) { parseTaskAddedDetails(message.text) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar_beacon_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_scale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_halo_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFF10B981), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 22.dp
                ),
                color = if (isDark) Color(0xFF091C14) else Color(0xFFF0FDF4),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isDark) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF86EFAC)
                ),
                shadowElevation = if (isDark) 2.dp else 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 12.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .scale(pulseScale)
                                    .background(Color(0xFF10B981).copy(alpha = haloAlpha), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = if (isDark) 0.22f else 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "ADDED TO YOUR RADAR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tracking in your schedule",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF132B20) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFDCFCE7)
                        ),
                        shadowElevation = if (isDark) 0.dp else 1.5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF10B981).copy(alpha = if (isDark) 0.25f else 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = taskInfo.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (fontSizeSp * 1.05f).coerceIn(15f, 19f).sp,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (!taskInfo.dueText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDark) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFDEF7EC),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        Color(0xFF10B981).copy(alpha = 0.35f)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AccessTime,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = taskInfo.dueText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp
                                            ),
                                            color = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (message.isTalkAction && !message.talkActionJson.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TalkActionChips(
                            talkActionJson = message.talkActionJson,
                            fontSizeSp = fontSizeSp,
                            onQueryClick = onQueryClick
                        )
                    }
                }
            }
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 40.dp)
        )
    }
}

@Composable
fun ConflictDetectedCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    onQueryClick: ((String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (300 + (fontSizeSp - 15f) * 10f).coerceIn(300f, 380f).dp

    val cleanPrompt = remember(message.text) { parseConflictPrompt(message.text) }

    val infiniteTransition = rememberInfiniteTransition(label = "conflict_beacon_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conflict_scale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conflict_halo_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFF59E0B), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 22.dp
                ),
                color = if (isDark) Color(0xFF231606) else Color(0xFFFFFBEB),
                border = androidx.compose.foundation.BorderStroke(
                    1.4.dp,
                    if (isDark) Color(0xFFF59E0B).copy(alpha = 0.55f) else Color(0xFFFCD34D)
                ),
                shadowElevation = if (isDark) 2.dp else 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 12.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .scale(pulseScale)
                                    .background(Color(0xFFF59E0B).copy(alpha = haloAlpha), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFF1E140A),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = if (isDark) 0.25f else 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "CONFLICT DETECTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Action needed to resolve",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF33200A) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color(0xFFF59E0B).copy(alpha = 0.25f) else Color(0xFFFEF3C7)
                        ),
                        shadowElevation = if (isDark) 0.dp else 1.5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = parseRichFormattedText(cleanPrompt),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * 1.45f).sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (message.isTalkAction && !message.talkActionJson.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TalkActionChips(
                            talkActionJson = message.talkActionJson,
                            fontSizeSp = fontSizeSp,
                            onQueryClick = onQueryClick
                        )
                    }
                }
            }
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 40.dp)
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    isScrolling: Boolean = false,
    isActiveDrill: Boolean = false,
    isActiveDrillRunning: Boolean = false,
    currentCombo: Int = 0,
    drillProgress: Pair<Int, Int>? = null,
    isBlitzMode: Boolean = false,
    onDrillAnswer: ((Boolean, QuestionRecord) -> Unit)? = null,
    onDrillEnd: (() -> Unit)? = null,
    onStartStreakDrill: (() -> Unit)? = null,
    onSkipDayWithFreeze: ((ChatMessage) -> Unit)? = null,
    onRescheduleClick: (() -> Unit)? = null,
    onTaskToggle: ((Long) -> Unit)? = null,
    onFilterChange: ((String) -> Unit)? = null,
    onNavigateSummary: (() -> Unit)? = null,
    onQueryClick: ((String) -> Unit)? = null,
    onMessageUpdate: (ChatMessage) -> Unit = {},
    onViewSolutions: ((String) -> Unit)? = null,
    onOpenMysteryChest: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onHabitLog: ((Long) -> Unit)? = null,
    onHabitCreated: ((com.focusbyrj.app.data.Habit) -> Unit)? = null
) {
    val context = LocalContext.current
    if (message.isStreakPrompt) {
        StreakPromptCard(
            message = message,
            fontSizeSp = fontSizeSp,
            isActiveDrillRunning = isActiveDrillRunning,
            onStartDrill = { onStartStreakDrill?.invoke() },
            onSkipWithFreeze = { onSkipDayWithFreeze?.invoke(message) }
        )
        return
    }
    if (message.isMysteryBox) {
        val questState by com.focusbyrj.app.util.DailyQuestManager.stateFlow.collectAsState()
        val isChestAvailable = questState.isEarlyBirdAvailable || questState.isNightOwlAvailable
        MysteryBoxChatCard(
            isAvailable = isChestAvailable,
            onOpenBox = {
                if (isChestAvailable) {
                    onOpenMysteryChest?.invoke()
                }
            }
        )
        return
    }
    if (message.isDrillSummary) {
        DrillSummaryCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onMessageUpdate = onMessageUpdate,
            onViewSolutions = onViewSolutions,
            onDismiss = onDismiss
        )
        return
    }
    if (message.isAptitudeProfile) {
        AptitudeProfileCard()
        return
    }
    if (message.isDailyQuests) {
        DailyQuestsCard()
        return
    }
    if (message.isVocabHub) {
        var vocabStats by remember { mutableStateOf<com.focusbyrj.app.data.VocabStats?>(null) }
        LaunchedEffect(message.id) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val repo = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.vocabRepository
                vocabStats = repo?.getStats()
            }
        }
        VocabRetentionHubChatCard(
            stats = vocabStats,
            onLaunchQuiz = { onQueryClick?.invoke("/vocab_quiz") },
            onLearnMore = { onQueryClick?.invoke("/vocab learn_more") }
        )
        return
    }

    val isMorning = message.isMorningBrief || message.id.startsWith("morning_")
    val isEvening = message.isEveningBrief || message.id.startsWith("evening_")
    val isWelcome = !message.isUser && (
        message.isWelcome ||
        message.id.startsWith("welcome_") ||
        message.text.contains("Hey! Ayva is on deck", ignoreCase = true) ||
        message.text.contains("ready for action", ignoreCase = true) ||
        message.text.contains("Ayva here!", ignoreCase = true) ||
        message.text.contains("I'm Ayva", ignoreCase = true) ||
        message.text.contains("Hey there! I'm Ayva", ignoreCase = true) ||
        message.text.contains("anti-procrastination", ignoreCase = true) ||
        message.text.contains("cognitive mastery", ignoreCase = true) ||
        message.text.contains("focus & learning companion", ignoreCase = true)
    )

    if (message.isHabitsSummary && !message.isUser) {
        HabitsChatCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onHabitLog = onHabitLog,
            onHabitCreated = onHabitCreated,
            onOpenFullTracker = {
                val intent = Intent(context, com.focusbyrj.app.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "habits")
                    putExtra("NAV_DESTINATION", "habits")
                }
                context.startActivity(intent)
            }
        )
        return
    }

    if (message.isTaskSummary && !message.isUser && !isMorning && !isEvening) {
        TaskSummaryCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onTaskToggle = onTaskToggle,
            onRescheduleClick = onRescheduleClick,
            onFilterChange = onFilterChange
        )
        return
    }

    val isTaskAdded = !message.isUser && (
        message.text.contains("Added to your radar", ignoreCase = true) ||
        message.id.startsWith("create_")
    )
    if (isTaskAdded) {
        TaskAddedCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onQueryClick = onQueryClick
        )
        return
    }

    val isConflict = !message.isUser && (
        message.text.contains("Conflict Detected", ignoreCase = true) ||
        message.id.startsWith("conflict_")
    )
    if (isConflict) {
        ConflictDetectedCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onQueryClick = onQueryClick
        )
        return
    }

    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))

    val category = remember(message.text, message.isMorningBrief, message.isEveningBrief, message.isArithmetic, message.isStreakPrompt, message.id) {
        AyvaAlertCategory.infer(
            text = message.text,
            isMorning = isMorning,
            isEvening = isEvening,
            isDrill = message.isArithmetic || message.isDrillSummary,
            isStreakPrompt = message.isStreakPrompt,
            messageId = message.id
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "Ayva",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, category.getComposeNotificationAccent(context).copy(alpha = 0.85f), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            val maxBubbleWidth = if (isWelcome) (330 + (fontSizeSp - 15f) * 10f).coerceIn(330f, 380f).dp
            else if (isMorning || isEvening) (310 + (fontSizeSp - 15f) * 10f).coerceIn(310f, 360f).dp
            else (280 + (fontSizeSp - 15f) * 10f).coerceIn(280f, 350f).dp
            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = if (message.isUser) 24.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 24.dp
                ),
                color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (message.isUser) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                shadowElevation = if (message.isUser) 0.dp else 4.dp
            ) {
                if (message.isArithmetic && message.arithmeticJson != null) {
                    ArithmeticCard(
                        message = message,
                        fontSizeSp = fontSizeSp,
                        isActiveDrill = isActiveDrill,
                        currentCombo = currentCombo,
                        drillProgress = drillProgress,
                        isBlitzMode = isBlitzMode,
                        onAnswered = onDrillAnswer,
                        onEndDrill = onDrillEnd
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = (14 + (fontSizeSp - 15f) * 0.5f).coerceIn(12f, 20f).dp,
                            vertical = (10 + (fontSizeSp - 15f) * 0.5f).coerceIn(8f, 16f).dp
                        )
                    ) {
                        if (isMorning && !message.isUser) {
                            MorningBriefLottieHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(135.dp)
                                    .padding(bottom = 10.dp),
                                isScrolling = isScrolling
                            )
                        } else if (isEvening && !message.isUser) {
                            EveningBriefHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(135.dp)
                                    .padding(bottom = 10.dp),
                                messageId = message.id,
                                isScrolling = isScrolling
                            )
                        } else if (message.isStreakFreezeSkipped && !message.isUser) {
                            CatAngryLottieHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(bottom = 10.dp),
                                isScrolling = isScrolling
                            )
                        }

                        val parsedFormattedText = remember(message.text) {
                            parseRichFormattedText(message.text)
                        }
                        Text(
                            text = parsedFormattedText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp,
                                letterSpacing = 0.2.sp
                            )
                        )

                        if (isWelcome) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(295.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                )
                            ) {
                                CatMagicRiveView(
                                    modifier = Modifier.fillMaxSize(),
                                    messageId = message.id
                                )
                            }
                        }

                        if (message.isVocabBrief && message.vocabJson != null) {
                            val isLearnMoreSession = !message.isMorningBrief && !message.isEveningBrief
                            VocabBriefContent(
                                vocabJson = message.vocabJson,
                                fontSizeSp = fontSizeSp,
                                isLearnMoreSession = isLearnMoreSession,
                                messageId = message.id,
                                onLearnMoreClick = {
                                    onQueryClick?.invoke("/vocab learn_more")
                                },
                                onQuizClick = {
                                    if (!isLearnMoreSession) {
                                        onQueryClick?.invoke("/vocab_quiz")
                                    }
                                }
                            )
                        }

                        if (message.isTalkAction && !message.talkActionJson.isNullOrBlank()) {
                            TalkActionChips(
                                talkActionJson = message.talkActionJson,
                                fontSizeSp = fontSizeSp,
                                onQueryClick = onQueryClick
                            )
                        }

                        if (!message.pendingActionJson.isNullOrBlank()) {
                            PendingActionCard(
                                message = message,
                                fontSizeSp = fontSizeSp,
                                onMessageUpdate = onMessageUpdate
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(
                top = 4.dp,
                start = if (message.isUser) 0.dp else 40.dp,
                end = if (message.isUser) 4.dp else 0.dp
            )
        )
    }
}

@Composable
fun AyvaChatTimeline(
    messages: List<ChatMessage>,
    listState: LazyListState,
    chatFontSizeSp: Float,
    activeDrillSession: DrillSession?,
    isCatActionPlaying: Boolean,
    onQueryClick: (String) -> Unit,
    onMessageUpdate: (ChatMessage) -> Unit,
    onViewSolutions: (String) -> Unit,
    onDismissMessage: (ChatMessage) -> Unit,
    onStartStreakDrill: () -> Unit,
    onSkipDayWithFreeze: (ChatMessage) -> Unit,
    onDrillAnswer: (Boolean, QuestionRecord) -> Unit,
    onDrillEnd: () -> Unit,
    onOpenMysteryChest: () -> Unit,
    onRescheduleClick: () -> Unit,
    onTaskToggle: (Long) -> Unit,
    onFilterChange: (String) -> Unit,
    onHabitLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (messages.isEmpty()) {
        val emptyChatText = remember(messages.isEmpty()) {
            AyvaDialogueEngine.getClearChatIntro(context)
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = parseRichFormattedText(emptyChatText),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = chatFontSizeSp.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = (chatFontSizeSp * 1.45f).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else {
        val reversedMessages = remember(messages) { messages.asReversed() }
        val catSpacerHeight by animateDpAsState(
            targetValue = if (isCatActionPlaying) 175.dp else 56.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "cat_spacer_height"
        )
        Box(
            modifier = modifier.fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                val isScrolling = listState.isScrollInProgress
                item(key = "cat_bottom_spacer") {
                    Spacer(modifier = Modifier.height(catSpacerHeight))
                }
                items(
                    count = reversedMessages.size,
                    key = { index -> reversedMessages[index].id },
                    contentType = { index ->
                        val m = reversedMessages[index]
                        when {
                            m.isUser -> "user_msg"
                            m.isMorningBrief || m.id.startsWith("morning_") -> "morning_brief"
                            m.isEveningBrief || m.id.startsWith("evening_") -> "evening_brief"
                            m.isVocabBrief -> "vocab_brief"
                            m.isArithmetic -> "arithmetic_msg"
                            m.isHabitsSummary -> "habits_msg"
                            m.isTaskSummary -> "task_msg"
                            !m.isUser && (m.text.contains("Added to your radar", ignoreCase = true) || m.id.startsWith("create_")) -> "task_added_msg"
                            !m.isUser && (m.text.contains("Conflict Detected", ignoreCase = true) || m.id.startsWith("conflict_")) -> "conflict_msg"
                            else -> "text_msg"
                        }
                    }
                ) { index ->
                    val msg = reversedMessages[index]
                    val isLatest = index == 0
                    val currentDrill = activeDrillSession
                    val isActiveDrill = isLatest && currentDrill != null && msg.isArithmetic
                    val currentCombo = if (isActiveDrill) currentDrill?.combo ?: 0 else 0
                    val drillProgress = if (isActiveDrill && currentDrill != null && currentDrill.targetQuestions > 0) {
                        Pair(currentDrill.total + 1, currentDrill.targetQuestions)
                    } else null
                    val isBlitzMode = currentDrill?.isBlitz ?: false

                    ChatBubble(
                        message = msg,
                        fontSizeSp = chatFontSizeSp,
                        isScrolling = isScrolling,
                        isActiveDrill = isActiveDrill,
                        isActiveDrillRunning = activeDrillSession != null,
                        currentCombo = currentCombo,
                        drillProgress = drillProgress,
                        isBlitzMode = isBlitzMode,
                        onQueryClick = onQueryClick,
                        onMessageUpdate = onMessageUpdate,
                        onViewSolutions = onViewSolutions,
                        onDismiss = { onDismissMessage(msg) },
                        onStartStreakDrill = onStartStreakDrill,
                        onSkipDayWithFreeze = onSkipDayWithFreeze,
                        onDrillAnswer = onDrillAnswer,
                        onDrillEnd = onDrillEnd,
                        onOpenMysteryChest = onOpenMysteryChest,
                        onRescheduleClick = onRescheduleClick,
                        onTaskToggle = onTaskToggle,
                        onFilterChange = onFilterChange,
                        onHabitLog = onHabitLog
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
