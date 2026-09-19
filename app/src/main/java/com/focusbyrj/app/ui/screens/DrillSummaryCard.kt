package com.focusbyrj.app.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.roundToInt
import com.focusbyrj.app.ui.components.*

@Composable
fun DrillSummaryCard(
    message: ChatMessage, 
    fontSizeSp: Float,
    onMessageUpdate: ((ChatMessage) -> Unit)? = null,
    onViewSolutions: ((String) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    hideActions: Boolean = false,
    externalClaimTrigger: Int = 0,
    onClaimStateChanged: ((Int) -> Unit)? = null
) {
    val json = message.drillSummaryJson ?: return
    val context = LocalContext.current
    
    var title by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf(1) }
    var level by remember { mutableStateOf(1) }
    var xpEarned by remember { mutableStateOf(0) }
    var goldEarned by remember { mutableStateOf(0) }
    var totalQuestions by remember { mutableStateOf(0) }
    var correctQuestions by remember { mutableStateOf(0) }
    var xpBefore by remember { mutableStateOf(0) }
    var xpNow by remember { mutableStateOf(0) }
    var xpCurrentLevelStart by remember { mutableStateOf(0) }
    var xpNextLevelStart by remember { mutableStateOf(0) }
    var isPerfect by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableStateOf(0) }
    var longestStreak by remember { mutableStateOf(0) }
    var streakBonusPercent by remember { mutableStateOf(0) }
    var streakBonusXp by remember { mutableStateOf(0) }
    var maxCombo by remember { mutableStateOf(0) }
    var comboBonusXp by remember { mutableStateOf(0) }
    var isBlitz by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var timeFormatted by remember { mutableStateOf("1:41") }
    var weeklyXp by remember { mutableStateOf(0) }
    var freezeNotice by remember { mutableStateOf("") }
    
    val initiallyClaimed = remember(json) {
        try {
            JSONObject(json).optBoolean("isClaimed", false)
        } catch (_: Exception) {
            false
        }
    }
    // UI flow state: 0 = Initial Summary, 1 = Upgrading XP Bar View, 2 = Claim Finished
    var claimState by remember(initiallyClaimed) { mutableStateOf(if (initiallyClaimed) 2 else 0) }
    var showXpAddedBanner by remember(initiallyClaimed) { mutableStateOf(false) }

    LaunchedEffect(claimState) {
        onClaimStateChanged?.invoke(claimState)
        if (claimState == 2) {
            if (!initiallyClaimed) {
                showXpAddedBanner = true
                kotlinx.coroutines.delay(2200)
                showXpAddedBanner = false
            } else {
                showXpAddedBanner = false
            }
        }
    }

    val markAsClaimed = {
        try {
            val obj = JSONObject(json)
            obj.put("isClaimed", true)
            val updatedJson = obj.toString()
            onMessageUpdate?.invoke(message.copy(drillSummaryJson = updatedJson))
        } catch (_: Exception) {}
    }

    LaunchedEffect(externalClaimTrigger) {
        if (externalClaimTrigger == 1 && claimState == 0) {
            markAsClaimed()
            claimState = 1
        }
    }

    LaunchedEffect(json) {
        try {
            val obj = JSONObject(json)
            title = obj.optString("title", "XP Olympian")
            tier = obj.optInt("tier", 1)
            level = obj.optInt("level", 1)
            xpEarned = obj.optInt("xpEarned", 0)
            goldEarned = obj.optInt("goldEarned", 0)
            totalQuestions = obj.optInt("total", 0)
            correctQuestions = obj.optInt("correct", 0)
            xpBefore = obj.optInt("xpBefore", 0)
            xpNow = obj.optInt("xpNow", 0)
            xpCurrentLevelStart = obj.optInt("xpCurrentLevelStart", 0)
            xpNextLevelStart = obj.optInt("xpNextLevelStart", 100)
            isPerfect = obj.optBoolean("isPerfect", false)
            currentStreak = obj.optInt("currentStreak", 0)
            longestStreak = obj.optInt("longestStreak", 0)
            streakBonusPercent = obj.optInt("streakBonusPercent", 0)
            streakBonusXp = obj.optInt("streakBonusXp", 0)
            maxCombo = obj.optInt("maxCombo", 0)
            comboBonusXp = obj.optInt("comboBonusXp", 0)
            isBlitz = obj.optBoolean("isBlitz", false)
            elapsedSeconds = obj.optInt("elapsedSeconds", 45)
            timeFormatted = obj.optString("timeFormatted", "${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}")
            weeklyXp = obj.optInt("weeklyXp", 0)
            freezeNotice = obj.optString("freezeNotice", "")
        } catch (_: Exception) {}
    }

    val isDark = isSystemInDarkTheme()
    val accuracyPct = if (totalQuestions > 0) {
        ((correctQuestions.toFloat() / totalQuestions) * 100).roundToInt()
    } else {
        100
    }

    val accuracyHeader = when {
        accuracyPct >= 100 -> "PERFECT"
        accuracyPct >= 80 -> "GOOD"
        accuracyPct >= 60 -> "GOOD"
        else -> "ACCURACY"
    }

    val timeHeader = when {
        isBlitz -> "SPEEDY"
        accuracyPct >= 80 -> "SPEEDY"
        else -> "SPEEDY"
    }

    val xpNeeded = (xpNextLevelStart - xpNow).coerceAtLeast(0)
    val rankUpgradeTitle = if (title.isNotEmpty()) title else "Level ${level + 1}"

    // Animated XP progress state
    val range = (xpNextLevelStart - xpCurrentLevelStart).toFloat().takeIf { it > 0f } ?: 1f
    val initialProgress = ((xpBefore - xpCurrentLevelStart).toFloat() / range).coerceIn(0f, 1f)
    val finalProgress = ((xpNow - xpCurrentLevelStart).toFloat() / range).coerceIn(0f, 1f)

    var triggerBarFill by remember { mutableStateOf(false) }
    LaunchedEffect(claimState) {
        if (claimState == 1) {
            kotlinx.coroutines.delay(250)
            triggerBarFill = true
        }
    }

    val animatedXpProgress by animateFloatAsState(
        targetValue = if (triggerBarFill) finalProgress else initialProgress,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "xp_upgrade_fill"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF131F24) else Color(0xFF131F24), // Authentic Duolingo dark slate canvas
        border = BorderStroke(1.5.dp, Color(0xFF20343D)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedContent(
                targetState = claimState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "summary_claim_transition"
            ) { targetState ->
                when (targetState) {
                    0, 2 -> {
                        // --- 3 STAT BOXES VIEW ---
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header Row with Title and Close / Dismiss Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBlitz) "⚡ SPEED BLITZ SUMMARY" else "ARITHMETIC DRILL SUMMARY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color(0xFF839EAB)
                                )
                                if (onDismiss != null) {
                                    IconButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss Drill Summary",
                                            tint = Color(0xFF839EAB),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Row of 3 Duolingo Stat Boxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. TOTAL XP Card (Yellow)
                                DuolingoStatBox(
                                    modifier = Modifier.weight(1f),
                                    headerText = "TOTAL XP",
                                    headerColor = Color(0xFFFFC800),
                                    headerTextColor = Color(0xFF2B1D00),
                                    borderColor = Color(0xFFFFC800),
                                    icon = {
                                        DuolingoBoltIcon(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFFFFC800)
                                        )
                                    },
                                    valueText = "$xpEarned",
                                    valueColor = Color(0xFFFFC800)
                                )

                                // 2. ACCURACY Card (Green)
                                DuolingoStatBox(
                                    modifier = Modifier.weight(1f),
                                    headerText = accuracyHeader,
                                    headerColor = Color(0xFF58CC02),
                                    headerTextColor = Color(0xFF163C01),
                                    borderColor = Color(0xFF58CC02),
                                    icon = {
                                        DuolingoTargetIcon(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFF58CC02)
                                        )
                                    },
                                    valueText = "$accuracyPct%",
                                    valueColor = Color(0xFF58CC02)
                                )

                                // 3. SPEED Card (Blue)
                                DuolingoStatBox(
                                    modifier = Modifier.weight(1f),
                                    headerText = timeHeader,
                                    headerColor = Color(0xFF1CB0F6),
                                    headerTextColor = Color(0xFF03354E),
                                    borderColor = Color(0xFF1CB0F6),
                                    hasGloss = true,
                                    icon = {
                                        DuolingoClockIcon(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFF1CB0F6)
                                        )
                                    },
                                    valueText = timeFormatted,
                                    valueColor = Color(0xFF1CB0F6)
                                )
                            }

                            // Extra details if present
                            if (goldEarned > 0 || maxCombo >= 2 || currentStreak > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (goldEarned > 0) {
                                        Text(
                                            text = "🪙 +$goldEarned Gold",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            ),
                                            color = Color(0xFFFFC800)
                                        )
                                    }
                                    if (maxCombo >= 2) {
                                        if (goldEarned > 0) Text("  •  ", color = Color(0xFF4B606D), fontSize = 11.sp)
                                        Text(
                                            text = "🎯 ${maxCombo}x Combo (+${comboBonusXp} XP)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            ),
                                            color = Color(0xFF58CC02)
                                        )
                                    }
                                    if (currentStreak > 0) {
                                        if (goldEarned > 0 || maxCombo >= 2) Text("  •  ", color = Color(0xFF4B606D), fontSize = 11.sp)
                                        Text(
                                            text = "🔥 Day $currentStreak (+${streakBonusPercent}%)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            ),
                                            color = Color(0xFFFFC800)
                                        )
                                    }
                                }
                            }

                            // Bottom Buttons
                            if (!hideActions) {
                                if (targetState == 0) {
                                    // Claim XP State: Share Icon + Duolingo CLAIM XP Button + SOLUTIONS Button
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Share button
                                        DuolingoShareButton(
                                            onClick = {
                                                try {
                                                    val shareText = "⚡ Solved $totalQuestions questions with $accuracyPct% accuracy in $timeFormatted! Earned +$xpEarned XP on Focus App!"
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                                        type = "text/plain"
                                                    }
                                                    val shareIntent = Intent.createChooser(sendIntent, "Share Math Results")
                                                    shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    context.startActivity(shareIntent)
                                                } catch (_: Exception) {}
                                            }
                                        )

                                        // 3D "CLAIM XP" Button
                                        Duolingo3DButton(
                                            text = "CLAIM XP",
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                markAsClaimed()
                                                claimState = 1
                                            }
                                        )
                                    }

                                    // 3D "SOLUTIONS" Button
                                    Duolingo3DButton(
                                        text = "SOLUTIONS",
                                        buttonColor = Color(0xFF1CB0F6),
                                        bevelColor = Color(0xFF1899D6),
                                        textColor = Color(0xFF042C48),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onViewSolutions?.invoke(json)
                                        }
                                    )
                                }
                            } else {
                                // Claimed State: Claim button disappears, showing clean claimed badge + SOLUTIONS Button
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AnimatedVisibility(
                                        visible = showXpAddedBanner,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF58CC02).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(0xFF58CC02).copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("✓", color = Color(0xFF58CC02), fontWeight = FontWeight.Black, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "+$xpEarned XP ADDED TO YOUR TOTAL",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 12.sp,
                                                        letterSpacing = 0.5.sp
                                                    ),
                                                    color = Color(0xFF58CC02)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DuolingoShareButton(
                                            onClick = {
                                                try {
                                                    val shareText = "⚡ Solved $totalQuestions questions with $accuracyPct% accuracy in $timeFormatted! Earned +$xpEarned XP on Focus App!"
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                                        type = "text/plain"
                                                    }
                                                    val shareIntent = Intent.createChooser(sendIntent, "Share Math Results")
                                                    shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    context.startActivity(shareIntent)
                                                } catch (_: Exception) {}
                                            }
                                        )

                                        // 3D "SOLUTIONS" Button
                                        Duolingo3DButton(
                                            text = "SOLUTIONS",
                                            buttonColor = Color(0xFF1CB0F6),
                                            bevelColor = Color(0xFF1899D6),
                                            textColor = Color(0xFF042C48),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onViewSolutions?.invoke(json)
                                            }
                                        )
                                    }

                                    if (onDismiss != null) {
                                        TextButton(
                                            onClick = onDismiss,
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Text(
                                                text = "DISMISS",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    letterSpacing = 0.8.sp
                                                ),
                                                color = Color(0xFF839EAB)
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }

                    1 -> {
                        // --- XP UPGRADE PROGRESS SCREEN (Image 2) ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            // Upgrade Heading Text
                            Text(
                                text = if (xpNeeded > 0) {
                                    "$xpNeeded more XP will upgrade\n$rankUpgradeTitle!"
                                } else {
                                    "Congratulations!\nYou upgraded $rankUpgradeTitle!"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 21.sp,
                                    lineHeight = 28.sp,
                                    textAlign = TextAlign.Center
                                ),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Large Duolingo XP Progress Bar
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF20343D))
                                ) {
                                    // Animated progress bar fill
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedXpProgress.coerceIn(0.04f, 1f))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color(0xFF42C8FF),
                                                        Color(0xFF1CB0F6)
                                                    )
                                                )
                                            )
                                    ) {
                                        // Glossy top specular shine
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.White.copy(alpha = 0.35f))
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$xpNow / $xpNextLevelStart XP",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFF839EAB)
                                    )
                                    Text(
                                        text = "+$xpEarned XP",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        ),
                                        color = Color(0xFF1CB0F6)
                                    )
                                }
                            }

                            // 3D "CONTINUE" Button (claim button disappears, replaced by CONTINUE)
                            Duolingo3DButton(
                                text = "CONTINUE",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    markAsClaimed()
                                    claimState = 2 // Move to completed state
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
