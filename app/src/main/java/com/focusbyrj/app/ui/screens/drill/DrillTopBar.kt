package com.focusbyrj.app.ui.screens.drill

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.screens.DrillSession

private val duolingoGreen = Color(0xFF58CC02)
private val duolingoYellow = Color(0xFFFFC800)
private val duolingoRed = Color(0xFFFF4B4B)
private val duolingoRedBevel = Color(0xFFEA2B2B)
private val duolingoPurple = Color(0xFFCE82FF)
private val duolingoPurpleBevel = Color(0xFFA55EEA)
private val duolingoOrange = Color(0xFFFF9600)
private val duolingoOrangeBevel = Color(0xFFE07A00)
private val duolingoYellowBevel = Color(0xFFE5A800)
private val duolingoBlue = Color(0xFF1CB0F6)
private val duolingoBlueBevel = Color(0xFF1899D6)
private val cardDarkBg = Color(0xFF1B1E23)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

@Composable
fun DrillTopBar(
    activeSession: DrillSession,
    questionIndex: Int,
    onEndSession: () -> Unit,
    onOpenOverview: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exit Button
            IconButton(
                onClick = onEndSession,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Exit Drill",
                    tint = if (isDark) Color(0xFF839EAB) else Color(0xFF64748B),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Glossy Progress Bar
            val progressFraction = if (activeSession.isBlitz) {
                val totalTime = 300f
                val elapsedSeconds = (300 - activeSession.blitzSecondsRemaining).coerceAtLeast(0)
                (elapsedSeconds.toFloat() / totalTime).coerceIn(0.03f, 1f)
            } else {
                val target = if (activeSession.targetQuestions > 0) activeSession.targetQuestions else 10
                (activeSession.total.toFloat() / target.toFloat()).coerceIn(0.03f, 1f)
            }

            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "drill_progress"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isDark) Color(0xFF20343D) else Color(0xFFE5E5E5))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (activeSession.isBlitz) {
                                if (activeSession.blitzSecondsRemaining <= 30) duolingoRed
                                else duolingoYellow
                            } else {
                                duolingoGreen
                            }
                        )
                ) {
                    // Specular gloss shine on top half
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.5.dp)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right HUD: Blitz Timer or Question Number Pill
            if (activeSession.isBlitz) {
                val mins = activeSession.blitzSecondsRemaining / 60
                val secs = activeSession.blitzSecondsRemaining % 60
                val timerText = if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s"
                val isUrgent = activeSession.blitzSecondsRemaining <= 30

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUrgent) duolingoRed.copy(alpha = 0.15f) else duolingoYellow.copy(alpha = 0.15f),
                    border = BorderStroke(2.dp, if (isUrgent) duolingoRed else duolingoYellow)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.Filled.ElectricBolt,
                            contentDescription = null,
                            tint = if (isUrgent) duolingoRed else duolingoYellow,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 12.5.sp),
                            color = if (isUrgent) duolingoRed else duolingoYellow
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) cardDarkBg else Color(0xFFF1F5F9),
                    border = BorderStroke(2.dp, if (isDark) Color(0xFF20343D) else Color(0xFFCBD5E1))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (activeSession.targetQuestions > 0) "${questionIndex + 1}/${activeSession.targetQuestions}" else "Q${questionIndex + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 12.5.sp),
                            color = if (isDark) Color(0xFF839EAB) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Action Pill Button (Overview / End)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeSession.targetQuestions > 0) {
                Button(
                    onClick = onOpenOverview,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF2B3A4A) else Color(0xFFE2E8F0),
                        contentColor = if (isDark) Color(0xFFA0AEC0) else Color(0xFF475569)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Grid View",
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    )
                }
            } else {
                Button(
                    onClick = onEndSession,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF5350),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "End Session",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DrillComboStreakCapsule(
    combo: Int,
    pulseScale: Float,
    isBlitz: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = combo > 0,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -10 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -10 })
    ) {
        val (comboLabel, boostLabel, accentColor, streakIcon) = when {
            combo >= 8 -> Quadruple("GODLIKE STREAK", "2.0x XP", Color(0xFFA855F7), "⚡")
            combo >= 5 -> Quadruple("ON FIRE (x$combo)", "1.5x XP", Color(0xFFFF5252), "🔥")
            combo >= 3 -> Quadruple("STREAK x$combo", "+50 XP", Color(0xFFFF9800), "🔥")
            combo == 2 -> Quadruple("STREAK x2", "+40 XP", Color(0xFFFFC107), "⚡")
            else -> Quadruple("STREAK x1", "+40 XP", Color(0xFF3B82F6), "🎯")
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .scale(if (combo >= 3) ((pulseScale - 1f) * 0.4f + 1f) else 1f)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xEE1E2228) else Color(0xEEF8FAFC),
                border = BorderStroke(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0x15000000)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Small Icon Tag
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = streakIcon,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = comboLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.6.sp
                        ),
                        color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x66FFFFFF) else Color(0x40000000))
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isBlitz) "$boostLabel • +30s" else boostLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp
                        ),
                        color = accentColor
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
