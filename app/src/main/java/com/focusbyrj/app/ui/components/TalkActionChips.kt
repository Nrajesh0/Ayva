package com.focusbyrj.app.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
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
import com.focusbyrj.app.util.TalkAction
import org.json.JSONObject

data class ActionColorTheme(
    val faceColor: Color,
    val bevelColor: Color,
    val textColor: Color,
    val badgeBg: Color
)

fun cleanLabelAndEmoji(rawLabel: String, rawEmoji: String): Pair<String, String> {
    var label = rawLabel.trim()
    var emoji = rawEmoji.trim()

    // Regex to match leading emoji / pictograph / symbols
    val emojiRegex = Regex("""^([\p{So}\p{Sk}\p{Sc}\p{Sm}\uD800-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2B50-\u2B55\uFE0F]+)\s*(.*)""", RegexOption.DOT_MATCHES_ALL)
    val match = emojiRegex.find(label)
    if (match != null) {
        val extractedEmoji = match.groupValues[1].trim()
        val restText = match.groupValues[2].trim()
        if (restText.isNotBlank()) {
            label = restText
            if (emoji.isBlank() || emoji == "💬" || emoji == "⚡") {
                emoji = extractedEmoji
            }
        }
    }

    if (emoji.isBlank()) {
        emoji = "⚡"
    }

    return Pair(label, emoji)
}

fun resolveActionColorTheme(
    action: TalkAction,
    isDark: Boolean,
    primaryColor: Color
): ActionColorTheme {
    val label = action.label.lowercase()
    val emoji = action.emoji

    return when {
        // Green: Complete, Done, Habit Tracker
        emoji in listOf("✅", "🌱") || label.contains("complete") || label.contains("mark done") || label.contains("done") || label.contains("finish") || label.contains("habit") -> {
            ActionColorTheme(
                faceColor = DuolingoPalette.Green,
                bevelColor = DuolingoPalette.GreenBevel,
                textColor = Color.White,
                badgeBg = Color.White.copy(alpha = 0.25f)
            )
        }
        // Amber / Yellow: Reschedule, Time, Quests, Chest, Rewards, Stats, Profile
        emoji in listOf("⏰", "🎁", "🏆", "📈", "⭐") || label.contains("reschedule") || label.contains("time") || label.contains("quest") || label.contains("chest") || label.contains("reward") || label.contains("summary") || label.contains("profile") || label.contains("stats") -> {
            ActionColorTheme(
                faceColor = DuolingoPalette.Yellow,
                bevelColor = DuolingoPalette.YellowBevel,
                textColor = DuolingoPalette.YellowDark,
                badgeBg = Color.Black.copy(alpha = 0.12f)
            )
        }
        // Blue / Cyan: Math Drill, Blitz, Arithmetic, Quiz, Practice
        emoji in listOf("🧮", "⚡") || label.contains("drill") || label.contains("math") || label.contains("blitz") || label.contains("quiz") || label.contains("arithmetic") -> {
            ActionColorTheme(
                faceColor = DuolingoPalette.Blue,
                bevelColor = DuolingoPalette.BlueBevel,
                textColor = Color.White,
                badgeBg = Color.White.copy(alpha = 0.25f)
            )
        }
        // Purple / Violet: Focus Session, Routines, Deep Work, Schedules
        emoji in listOf("⏱️", "📅", "🛡️") || label.contains("focus") || label.contains("routine") || label.contains("schedule") || label.contains("deep work") || label.contains("pomodoro") -> {
            ActionColorTheme(
                faceColor = DuolingoPalette.Purple,
                bevelColor = DuolingoPalette.PurpleBevel,
                textColor = Color.White,
                badgeBg = Color.White.copy(alpha = 0.25f)
            )
        }
        // Red: Delete, Remove, Trash, Clear, Cancel, Wipe, Block
        emoji in listOf("🗑️", "❌", "🧹") || label.contains("delete") || label.contains("remove") || label.contains("trash") || label.contains("cancel") || label.contains("clear") || label.contains("wipe") || label.contains("block") -> {
            ActionColorTheme(
                faceColor = DuolingoPalette.Red,
                bevelColor = DuolingoPalette.RedBevel,
                textColor = Color.White,
                badgeBg = Color.White.copy(alpha = 0.25f)
            )
        }
        // Primary / Slate: Create Task, Add to Todo, View Tasks, Default
        else -> {
            ActionColorTheme(
                faceColor = primaryColor,
                bevelColor = primaryColor.copy(alpha = 0.72f),
                textColor = Color.White,
                badgeBg = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TalkActionChips(
    talkActionJson: String?,
    fontSizeSp: Float = 15f,
    onQueryClick: ((String) -> Unit)? = null,
    onActionApplied: ((String) -> Unit)? = null
) {
    if (talkActionJson.isNullOrBlank()) return

    val context = LocalContext.current
    var appliedActionKey by remember { mutableStateOf<String?>(null) }

    val (topicId, parsedActions) = remember(talkActionJson) {
        val list = mutableListOf<TalkAction>()
        var topic = ""
        try {
            val root = JSONObject(talkActionJson)
            topic = root.optString("topicId", "")
            val arr = root.optJSONArray("actions") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val type = obj.optString("type")
                val rawLabel = obj.optString("label")
                val rawEmoji = obj.optString("emoji", "")
                val (cleanLabel, cleanEmoji) = cleanLabelAndEmoji(rawLabel, rawEmoji)
                when (type) {
                    "navigate" -> {
                        val route = obj.optString("route")
                        list.add(TalkAction.NavigateAppScreen(route, cleanLabel, cleanEmoji))
                    }
                    "ask_query" -> {
                        val query = obj.optString("query")
                        list.add(TalkAction.AskQuery(query, cleanLabel, cleanEmoji))
                    }
                    "system_setting" -> {
                        val action = obj.optString("action")
                        val packageUri = obj.optBoolean("packageUri", false)
                        list.add(TalkAction.OpenSystemSetting(action, cleanLabel, cleanEmoji, packageUri))
                    }
                    "pref_update" -> {
                        val key = obj.optString("prefKey")
                        val prefType = obj.optString("prefType")
                        val targetVal = obj.optString("targetValue")
                        val dispVal = obj.optString("displayValue")
                        list.add(TalkAction.DirectPrefUpdate(key, prefType, targetVal, dispVal, cleanLabel, cleanEmoji))
                    }
                    "routine_toggle" -> {
                        val scheduleId = obj.optInt("scheduleId")
                        val isEnabled = obj.optBoolean("isEnabled", true)
                        val routineName = obj.optString("routineName", cleanLabel)
                        list.add(TalkAction.RoutineToggle(scheduleId, isEnabled, routineName, cleanLabel, cleanEmoji))
                    }
                }
            }
        } catch (_: Exception) {}
        Pair(topic, list)
    }

    if (parsedActions.isEmpty()) return

    val isConflictOrChoice = topicId == "conflict" || topicId == "tasks" || parsedActions.size in 2..4
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary

    val onActionClick: (TalkAction) -> Unit = { action ->
        if (action is TalkAction.AskQuery && onQueryClick != null) {
            onQueryClick(action.query)
        } else {
            val success = action.execute(context)
            if (success) {
                if (action is TalkAction.DirectPrefUpdate) {
                    appliedActionKey = "${action.prefKey}_${action.targetValue}"
                    Toast.makeText(context, "Updated: ${action.label} ✅", Toast.LENGTH_SHORT).show()
                    onActionApplied?.invoke("Updated: ${action.label}")
                } else if (action is TalkAction.RoutineToggle) {
                    appliedActionKey = "routine_${action.scheduleId}_${action.isEnabled}"
                    val statusWord = if (action.isEnabled) "Started" else "Stopped"
                    Toast.makeText(context, "${action.routineName}: $statusWord ✅", Toast.LENGTH_SHORT).show()
                    onActionApplied?.invoke("${action.routineName}: $statusWord")
                } else {
                    Toast.makeText(context, "Opening ${action.label}...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        if (isConflictOrChoice) {
            // Stacked tactile prompt cards full-width for maximum readability and zero text clipping
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                parsedActions.forEach { action ->
                    val isSelected = when (action) {
                        is TalkAction.DirectPrefUpdate -> appliedActionKey == "${action.prefKey}_${action.targetValue}"
                        is TalkAction.RoutineToggle -> appliedActionKey == "routine_${action.scheduleId}_${action.isEnabled}"
                        else -> false
                    }
                    val theme = resolveActionColorTheme(action, isDark, primaryColor)
                    Tactile3DChoiceButton(
                        action = action,
                        isSelected = isSelected,
                        theme = theme,
                        fontSizeSp = fontSizeSp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onActionClick(action) }
                    )
                }
            }
        } else {
            // General Quick Action suggestions in FlowRow with tactile pill styling
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                parsedActions.forEach { action ->
                    val isSelected = when (action) {
                        is TalkAction.DirectPrefUpdate -> appliedActionKey == "${action.prefKey}_${action.targetValue}"
                        is TalkAction.RoutineToggle -> appliedActionKey == "routine_${action.scheduleId}_${action.isEnabled}"
                        else -> false
                    }
                    val theme = resolveActionColorTheme(action, isDark, primaryColor)
                    TactilePillActionChip(
                        action = action,
                        isSelected = isSelected,
                        theme = theme,
                        fontSizeSp = fontSizeSp,
                        onClick = { onActionClick(action) }
                    )
                }
            }
        }
    }
}

@Composable
fun Tactile3DChoiceButton(
    action: TalkAction,
    isSelected: Boolean,
    theme: ActionColorTheme,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetY by animateFloatAsState(
        targetValue = if (isPressed) 3f else 0f,
        animationSpec = tween(durationMillis = 60),
        label = "choice_btn_press"
    )

    val bevelDepthDp = 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bevelDepthDp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // 3D Bevel Shadow / Lip
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = bevelDepthDp)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.bevelColor)
        )

        // Top Tactile Face
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = pressOffsetY.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.faceColor)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Round Emoji Badge
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(theme.badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = action.emoji,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Label Text - supports 2 lines, legible sizing and lineHeight
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizeSp * 0.90f).coerceIn(12.5f, 15f).sp,
                        lineHeight = (fontSizeSp * 1.18f).coerceIn(15.5f, 18.5f).sp,
                        letterSpacing = 0.15.sp
                    ),
                    color = theme.textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Active",
                        tint = theme.textColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Select",
                        tint = theme.textColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TactilePillActionChip(
    action: TalkAction,
    isSelected: Boolean,
    theme: ActionColorTheme,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetY by animateFloatAsState(
        targetValue = if (isPressed) 2f else 0f,
        animationSpec = tween(durationMillis = 60),
        label = "pill_chip_press"
    )

    val bevelDepthDp = 3.dp

    Box(
        modifier = modifier
            .padding(bottom = bevelDepthDp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Bottom shadow bevel
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = bevelDepthDp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.bevelColor.copy(alpha = if (isSelected) 0.9f else 0.45f))
        )

        // Top tactile pill
        Box(
            modifier = Modifier
                .offset(y = pressOffsetY.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) theme.faceColor 
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                )
                .padding(horizontal = 11.dp, vertical = 7.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = action.emoji,
                    fontSize = 13.5.sp
                )
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = (fontSizeSp * 0.82f).coerceIn(11.5f, 13.5f).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        letterSpacing = 0.15.sp
                    ),
                    color = if (isSelected) theme.textColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Active",
                        tint = theme.textColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
